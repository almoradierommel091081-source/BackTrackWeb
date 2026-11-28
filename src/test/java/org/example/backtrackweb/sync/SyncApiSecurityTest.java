package org.example.backtrackweb.sync;

import org.example.backtrackweb.entity.Role;
import org.example.backtrackweb.entity.User;
import org.example.backtrackweb.tenant.TenantContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The machine API over HTTP: no token means no access, registration is the only
 * way in, and a token of one business never returns another business's data.
 */
@AutoConfigureMockMvc
class SyncApiSecurityTest extends SyncTestBase {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    private String registerOverHttp(String username, String deviceName) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/devices/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username,
                                "password", "secret123",
                                "deviceName", deviceName,
                                "platform", "ANDROID"))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("token").asString();
    }

    @Test
    void syncEndpointsRefuseAnonymousCalls() throws Exception {
        mockMvc.perform(get("/api/v1/sync/pull")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/devices")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/sync/lease/claim")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/discrepancies")).andExpect(status().isUnauthorized());
    }

    @Test
    void aBadPasswordCannotRegister() throws Exception {
        account("httpowner", Role.OWNER);

        mockMvc.perform(post("/api/v1/devices/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "httpowner",
                                "password", "not-the-password",
                                "deviceName", "PC"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aRegisteredTokenWorksAndTheRawTokenIsNeverListedAgain() throws Exception {
        account("httpowner", Role.OWNER);
        String token = registerOverHttp("httpowner", "Owner phone");

        mockMvc.perform(get("/api/v1/devices").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tokenPrefix").exists());

        MvcResult devices = mockMvc.perform(get("/api/v1/devices")
                        .header("Authorization", "Bearer " + token))
                .andReturn();
        assertThat(devices.getResponse().getContentAsString()).doesNotContain(token);
        assertThat(devices.getResponse().getContentAsString()).doesNotContain("tokenHash");

        mockMvc.perform(get("/api/v1/sync/lease").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.epoch").value(0));

        mockMvc.perform(post("/api/v1/sync/lease/claim").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.epoch").value(1));
    }

    @Test
    void aRevokedTokenStopsWorkingImmediately() throws Exception {
        User owner = account("httpowner", Role.OWNER);
        String token = registerOverHttp("httpowner", "Lost phone");

        // The device filter clears the binding when a request ends.
        TenantContext.set(1L);
        var device = deviceService.list().getFirst();
        deviceService.revoke(device.getUuid(), "lost", new org.example.backtrackweb.sync.security.DevicePrincipal(
                device.getId(), device.getUuid(), owner.getId(), owner.getUsername(), owner.getRole(),
                device.getBusinessId()));

        mockMvc.perform(get("/api/v1/sync/lease").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aDeviceOnlyEverSeesItsOwnBusiness() throws Exception {
        account("businessone", Role.OWNER);
        String tokenOne = registerOverHttp("businessone", "Shop one PC");

        Long otherBusiness = secondBusiness("Second Shop");
        as(otherBusiness, () -> account("businesstwo", Role.OWNER));
        String tokenTwo = as(otherBusiness, () -> {
            try {
                return registerOverHttp("businesstwo", "Shop two PC");
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
        });

        TenantContext.set(1L);
        mockMvc.perform(post("/api/v1/sync/push")
                        .header("Authorization", "Bearer " + tokenOne)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "deviceUuid", deviceService.list().stream()
                                        .filter(d -> d.getBusinessId().equals(1L))
                                        .findFirst().orElseThrow().getUuid(),
                                "leaseEpoch", 1,
                                "operations", java.util.List.of()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.reason").value("no_lease"));

        // Each token resolves to its own business, so neither can read the other's feed.
        MvcResult one = mockMvc.perform(get("/api/v1/sync/pull").param("since", "0")
                        .header("Authorization", "Bearer " + tokenOne))
                .andExpect(status().isOk()).andReturn();
        MvcResult two = mockMvc.perform(get("/api/v1/sync/pull").param("since", "0")
                        .header("Authorization", "Bearer " + tokenTwo))
                .andExpect(status().isOk()).andReturn();

        assertThat(one.getResponse().getContentAsString()).contains("\"changes\":[]");
        assertThat(two.getResponse().getContentAsString()).contains("\"changes\":[]");

        TenantContext.set(1L);
        assertThat(deviceService.list()).hasSize(1);
    }
}
