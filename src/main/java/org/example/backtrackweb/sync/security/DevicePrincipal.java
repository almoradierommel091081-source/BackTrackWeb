package org.example.backtrackweb.sync.security;

import org.example.backtrackweb.entity.Role;

/**
 * The identity of a request made by a registered device.
 *
 * <p>It carries the device, the account that registered it and the business
 * both belong to. Controllers read the business from here (never from the
 * request body), which is what keeps one business out of another's data.
 */
public record DevicePrincipal(Long deviceId,
                              String deviceUuid,
                              Long userId,
                              String username,
                              Role role,
                              Long businessId) {

    public boolean isOwner() {
        return role == Role.OWNER;
    }
}
