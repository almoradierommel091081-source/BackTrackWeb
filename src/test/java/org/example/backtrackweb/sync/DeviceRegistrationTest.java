package org.example.backtrackweb.sync;

import org.example.backtrackweb.entity.Role;
import org.example.backtrackweb.entity.User;
import org.example.backtrackweb.exception.BusinessException;
import org.example.backtrackweb.sync.entity.SyncDevice;
import org.example.backtrackweb.sync.security.DevicePrincipal;
import org.example.backtrackweb.sync.service.DeviceService;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A device registers once with an account password, receives a token exactly
 * once, and the database keeps only a hash of it.
 */
class DeviceRegistrationTest extends SyncTestBase {

    @Test
    void registrationReturnsATokenButStoresOnlyItsHash() {
        account("deviceowner", Role.OWNER);

        DeviceService.Registration registration = register("deviceowner", "Front desk PC");
        SyncDevice device = deviceOf(registration.deviceUuid());

        assertThat(registration.token()).startsWith("btk_");
        assertThat(device.getTokenHash())
                .hasSize(64)
                .isNotEqualTo(registration.token())
                .isEqualTo(new org.example.backtrackweb.sync.security.DeviceTokenService().hash(
                        registration.token()));
        assertThat(device.getTokenPrefix()).hasSize(12);
        assertThat(registration.token()).startsWith(device.getTokenPrefix());
        assertThat(registration.tokenExpiresAt()).isAfter(Instant.now().plusSeconds(80L * 24 * 3600));
    }

    @Test
    void aWrongPasswordDoesNotRegisterAnything() {
        account("deviceowner", Role.OWNER);

        assertThatThrownBy(() -> deviceService.register("deviceowner", "wrong-password", "PC", "WINDOWS"))
                .isInstanceOf(BusinessException.class);
        assertThat(syncDeviceRepository.findAll()).isEmpty();
    }

    @Test
    void aDeactivatedAccountCannotRegisterADevice() {
        User owner = account("deviceowner", Role.OWNER);
        owner.setActive(false);
        userRepository.save(owner);

        assertThatThrownBy(() -> register("deviceowner", "PC")).isInstanceOf(BusinessException.class);
    }

    @Test
    void anExpiredTokenIsNoLongerUsable() {
        account("deviceowner", Role.OWNER);
        DeviceService.Registration registration = register("deviceowner", "Old laptop");

        SyncDevice device = deviceOf(registration.deviceUuid());
        device.setTokenExpiresAt(Instant.now().minusSeconds(60));
        syncDeviceRepository.save(device);

        assertThat(deviceOf(registration.deviceUuid()).isUsable(Instant.now())).isFalse();
    }

    @Test
    void onlyAnOwnerCanRevokeADeviceAndTheReasonIsKept() {
        account("deviceowner", Role.OWNER);
        User employee = account("deviceclerk", Role.EMPLOYEE);
        DeviceService.Registration ownerDevice = register("deviceowner", "Owner PC");
        DeviceService.Registration employeeDevice = register("deviceclerk", "Counter PC");

        DevicePrincipal clerk = principalOf(employeeDevice.deviceUuid(), employee);
        assertThatThrownBy(() -> deviceService.revoke(ownerDevice.deviceUuid(), "lost", clerk))
                .isInstanceOf(BusinessException.class);

        User owner = userRepository.findByUsernameIgnoreCase("deviceowner").orElseThrow();
        DevicePrincipal ownerPrincipal = principalOf(ownerDevice.deviceUuid(), owner);
        SyncDevice revoked = deviceService.revoke(employeeDevice.deviceUuid(), "Screen broken",
                ownerPrincipal);

        assertThat(revoked.getRevokedAt()).isNotNull();
        assertThat(revoked.getRevokedReason()).isEqualTo("Screen broken");
        assertThat(revoked.isUsable(Instant.now())).isFalse();
    }
}
