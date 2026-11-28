package org.example.backtrackweb.tenant;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example.backtrackweb.entity.User;
import org.example.backtrackweb.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Binds the business for every browser request.
 *
 * <p>The business always comes from the authenticated identity, never from a
 * URL, query parameter or form field. Requests that arrive without an
 * authenticated user (login page, static assets) simply run without a binding;
 * any attempt to touch tenant data then fails closed.
 *
 * <p>Requests already bound by the device filter (the machine API) are left
 * alone.
 */
@Component
public class TenantInterceptor implements HandlerInterceptor {

    private final UserRepository userRepository;

    public TenantInterceptor(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (TenantContext.isSet()) {
            return true;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return true;
        }
        if (authentication.getPrincipal() instanceof TenantUserDetails principal) {
            TenantContext.set(principal.getBusinessId());
            return true;
        }
        // A principal created without our UserDetails (for example a test or a
        // future authentication mechanism): resolve the business from the
        // account record, which is the only trusted source.
        User user = userRepository.findByUsernameIgnoreCase(authentication.getName()).orElse(null);
        if (user != null) {
            TenantContext.set(user.getBusinessId());
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        TenantContext.clear();
    }
}
