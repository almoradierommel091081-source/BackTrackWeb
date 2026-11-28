package org.example.backtrackweb.tenant;

import org.example.backtrackweb.entity.Role;
import org.example.backtrackweb.entity.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * The authenticated principal. Carries the business id so the tenant can be
 * bound from the security context without another database round trip.
 */
public class TenantUserDetails implements UserDetails {

    private final Long userId;
    private final Long businessId;
    private final String username;
    private final String passwordHash;
    private final Role role;
    private final boolean active;

    public TenantUserDetails(User user) {
        this.userId = user.getId();
        this.businessId = user.getBusinessId();
        this.username = user.getUsername();
        this.passwordHash = user.getPasswordHash();
        this.role = user.getRole();
        this.active = user.isActive();
    }

    public Long getUserId() {
        return userId;
    }

    public Long getBusinessId() {
        return businessId;
    }

    public Role getRole() {
        return role;
    }

    public boolean isOwner() {
        return role == Role.OWNER;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }
}
