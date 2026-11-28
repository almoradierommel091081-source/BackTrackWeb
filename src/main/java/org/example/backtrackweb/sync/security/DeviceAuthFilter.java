package org.example.backtrackweb.sync.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example.backtrackweb.entity.User;
import org.example.backtrackweb.repository.UserRepository;
import org.example.backtrackweb.sync.entity.SyncDevice;
import org.example.backtrackweb.sync.repository.SyncDeviceRepository;
import org.example.backtrackweb.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

/**
 * Authenticates the machine API with a device token.
 *
 * <p>The token arrives as {@code Authorization: Bearer ...}. The filter hashes
 * it, finds the device, and binds the business from the device record - never
 * from anything the caller sent. A token that is unknown, expired, revoked or
 * belongs to a deactivated account simply does not authenticate, and the
 * security chain answers 401.
 *
 * <p>The binding is cleared when the request ends, so a pooled thread can never
 * carry one business's identity into another request.
 */
@Component
public class DeviceAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(DeviceAuthFilter.class);

    private final SyncDeviceRepository deviceRepository;
    private final UserRepository userRepository;
    private final DeviceTokenService tokenService;

    public DeviceAuthFilter(SyncDeviceRepository deviceRepository,
                            UserRepository userRepository,
                            DeviceTokenService tokenService) {
        this.deviceRepository = deviceRepository;
        this.userRepository = userRepository;
        this.tokenService = tokenService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String token = bearerToken(request);
        if (token == null) {
            chain.doFilter(request, response);
            return;
        }
        try {
            authenticate(token);
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
            SecurityContextHolder.clearContext();
        }
    }

    private void authenticate(String token) {
        SyncDevice device = deviceRepository.findByTokenHash(tokenService.hash(token)).orElse(null);
        if (device == null) {
            log.debug("Rejected a device token that does not match any device");
            return;
        }
        if (!device.isUsable(Instant.now())) {
            log.info("Rejected an expired or revoked device token for device {}", device.getUuid());
            return;
        }
        User user = userRepository.findById(device.getUserId()).orElse(null);
        if (user == null || !user.isActive() || !user.getBusinessId().equals(device.getBusinessId())) {
            log.info("Rejected a device token whose account is gone or inactive (device {})", device.getUuid());
            return;
        }

        DevicePrincipal principal = new DevicePrincipal(device.getId(), device.getUuid(), user.getId(),
                user.getUsername(), user.getRole(), device.getBusinessId());
        var authentication = new UsernamePasswordAuthenticationToken(principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_DEVICE"),
                        new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        TenantContext.set(device.getBusinessId());

        device.setLastSeenAt(Instant.now());
        deviceRepository.save(device);
    }

    private String bearerToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            return null;
        }
        String token = header.substring("Bearer ".length()).trim();
        return token.isEmpty() ? null : token;
    }
}
