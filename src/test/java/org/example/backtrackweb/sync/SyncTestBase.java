package org.example.backtrackweb.sync;

import org.example.backtrackweb.entity.Business;
import org.example.backtrackweb.entity.Role;
import org.example.backtrackweb.entity.User;
import org.example.backtrackweb.sync.entity.SyncDevice;
import org.example.backtrackweb.sync.security.DevicePrincipal;
import org.example.backtrackweb.sync.service.DeviceService;
import org.example.backtrackweb.support.PostgresTestBase;
import org.example.backtrackweb.tenant.TenantContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.function.Supplier;

/**
 * Helpers shared by the synchronization tests: real accounts, real device
 * registration and a way to run a block as one business.
 */
abstract class SyncTestBase extends PostgresTestBase {

    @Autowired
    protected DeviceService deviceService;
    @Autowired
    protected PasswordEncoder passwordEncoder;

    protected User account(String username, Role role) {
        User user = new User();
        user.setUsername(username);
        user.setFullName(username);
        user.setRole(role);
        user.setActive(true);
        user.setPasswordHash(passwordEncoder.encode("secret123"));
        return userRepository.save(user);
    }

    protected Long secondBusiness(String name) {
        return businessRepository.save(new Business(name)).getId();
    }

    protected DeviceService.Registration register(String username, String deviceName) {
        return deviceService.register(username, "secret123", deviceName, "WINDOWS");
    }

    protected SyncDevice deviceOf(String deviceUuid) {
        return syncDeviceRepository.findByUuidAndBusinessId(deviceUuid, TenantContext.require())
                .orElseThrow();
    }

    protected DevicePrincipal principalOf(String deviceUuid, User user) {
        SyncDevice device = deviceOf(deviceUuid);
        return new DevicePrincipal(device.getId(), device.getUuid(), user.getId(), user.getUsername(),
                user.getRole(), device.getBusinessId());
    }

    protected <T> T as(Long businessId, Supplier<T> action) {
        return TenantContext.callAs(businessId, action);
    }

    protected void runAs(Long businessId, Runnable action) {
        TenantContext.runAs(businessId, action);
    }
}
