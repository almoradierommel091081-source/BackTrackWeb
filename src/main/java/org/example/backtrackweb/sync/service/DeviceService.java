package org.example.backtrackweb.sync.service;

import org.example.backtrackweb.entity.User;
import org.example.backtrackweb.exception.BusinessException;
import org.example.backtrackweb.repository.UserRepository;
import org.example.backtrackweb.sync.entity.SyncDevice;
import org.example.backtrackweb.sync.entity.SyncLeaseEvent;
import org.example.backtrackweb.sync.repository.SyncDeviceRepository;
import org.example.backtrackweb.sync.repository.SyncLeaseEventRepository;
import org.example.backtrackweb.sync.security.DevicePrincipal;
import org.example.backtrackweb.sync.security.DeviceTokenService;
import org.example.backtrackweb.tenant.TenantContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Registration and revocation of the Windows and Android clients.
 *
 * <p>A device registers once with the username and password of an account and
 * receives a token that is valid for 90 days. The token is shown exactly once;
 * only its hash is kept. Everything afterwards is authenticated with that
 * token, and every lookup is scoped to the device's own business.
 */
@Service
public class DeviceService {

    /** Chosen in the approved Phase 2 plan. */
    public static final Duration TOKEN_LIFETIME = Duration.ofDays(90);

    private final SyncDeviceRepository deviceRepository;
    private final SyncLeaseEventRepository leaseEventRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final DeviceTokenService tokenService;
    private final SyncLeaseService leaseService;

    public DeviceService(SyncDeviceRepository deviceRepository,
                         SyncLeaseEventRepository leaseEventRepository,
                         UserRepository userRepository,
                         PasswordEncoder passwordEncoder,
                         DeviceTokenService tokenService,
                         SyncLeaseService leaseService) {
        this.deviceRepository = deviceRepository;
        this.leaseEventRepository = leaseEventRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.leaseService = leaseService;
    }

    /** What the device gets back once, at registration. */
    public record Registration(String deviceUuid, String token, Instant tokenExpiresAt, Long businessId) {
    }

    @Transactional
    public Registration register(String username, String password, String deviceName, String platform) {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw new BusinessException("Username and password are required to register a device.");
        }
        if (deviceName == null || deviceName.isBlank()) {
            throw new BusinessException("Give this device a name so it can be recognised later.");
        }
        User user = userRepository.findByUsernameIgnoreCase(username.trim()).orElse(null);
        // Same answer for an unknown account and a wrong password.
        if (user == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new BusinessException("Those account details are not correct.");
        }
        if (!user.isActive()) {
            throw new BusinessException("That account is deactivated and cannot register a device.");
        }

        String token = tokenService.newToken();
        SyncDevice device = new SyncDevice();
        device.setUuid(UUID.randomUUID().toString());
        device.setBusinessId(user.getBusinessId());
        device.setUserId(user.getId());
        device.setName(deviceName.trim());
        device.setPlatform(normalisePlatform(platform));
        device.setTokenHash(tokenService.hash(token));
        device.setTokenPrefix(tokenService.prefixOf(token));
        device.setTokenExpiresAt(Instant.now().plus(TOKEN_LIFETIME));
        device = deviceRepository.save(device);

        // Registration is a business event like any other; the lease itself
        // stays with whichever device holds it.
        SyncLeaseEvent event = new SyncLeaseEvent();
        event.setBusinessId(device.getBusinessId());
        event.setEpoch(leaseService.currentEpoch(device.getBusinessId()));
        event.setEvent("REGISTERED");
        event.setDeviceId(device.getId());
        event.setActorUserId(user.getId());
        event.setNote("Device registered: " + device.getName() + " (" + device.getPlatform() + ")");
        leaseEventRepository.save(event);

        return new Registration(device.getUuid(), token, device.getTokenExpiresAt(), device.getBusinessId());
    }

    @Transactional(readOnly = true)
    public List<SyncDevice> list() {
        return deviceRepository.findByBusinessIdOrderByCreatedAtDesc(TenantContext.require());
    }

    @Transactional(readOnly = true)
    public SyncDevice require(String deviceUuid) {
        return deviceRepository.findByUuidAndBusinessId(deviceUuid, TenantContext.require())
                .orElseThrow(() -> new BusinessException("Device not found."));
    }

    /**
     * Revokes a device. An owner can do this from any device of the business;
     * if the revoked device held the lease, the lease is released so the
     * business is not left without a writer.
     */
    @Transactional
    public SyncDevice revoke(String deviceUuid, String reason, DevicePrincipal actor) {
        if (!actor.isOwner()) {
            throw new BusinessException("Only the business owner can revoke a device.");
        }
        SyncDevice device = require(deviceUuid);
        if (device.getRevokedAt() != null) {
            throw new BusinessException("That device is already revoked.");
        }
        device.setRevokedAt(Instant.now());
        device.setRevokedReason(reason == null || reason.isBlank()
                ? "Revoked by " + actor.username()
                : reason.trim());
        device = deviceRepository.save(device);

        leaseService.releaseIfHeldBy(device.getId(), actor.userId(), SyncLeaseEvent.REVOKED,
                "Device revoked: " + device.getName());
        return device;
    }

    private String normalisePlatform(String platform) {
        String value = platform == null ? "UNKNOWN" : platform.trim().toUpperCase(Locale.ROOT);
        return switch (value) {
            case "WINDOWS", "ANDROID" -> value;
            default -> "UNKNOWN";
        };
    }
}
