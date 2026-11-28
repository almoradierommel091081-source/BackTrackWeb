package org.example.backtrackweb.web;

import org.example.backtrackweb.support.PostgresTestBase;
import org.example.backtrackweb.tenant.TenantContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The machine API lives on its own security chain: it answers JSON, never
 * redirects to the login page, and exposes nothing but the health probe yet.
 */
@AutoConfigureMockMvc
class ApiPingTest extends PostgresTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void pingIsPublicAndReturnsJson() throws Exception {
        mockMvc.perform(get("/api/v1/ping"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.apiVersion").value("v1"))
                .andExpect(jsonPath("$.serverTime").isNotEmpty());
    }

    /**
     * The global model advice applies to REST controllers too, so the public
     * probe is the API's version of the login page: it must work with no
     * business bound, and it must not leave one bound afterwards.
     */
    @Test
    void pingWorksWithoutATenant() throws Exception {
        TenantContext.clear();

        mockMvc.perform(get("/api/v1/ping"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));

        assertThat(TenantContext.isSet()).isFalse();
    }

    @Test
    void unknownApiPathAnswers401InsteadOfRedirectingToLogin() throws Exception {
        mockMvc.perform(get("/api/v1/anything"))
                .andExpect(status().isUnauthorized());
    }
}
