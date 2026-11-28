package org.example.backtrackweb.config;

import org.example.backtrackweb.entity.BusinessSettings;
import org.example.backtrackweb.entity.User;
import org.example.backtrackweb.repository.UserRepository;
import org.example.backtrackweb.service.SettingsService;
import org.example.backtrackweb.tenant.TenantContext;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Makes the logged-in user, the business settings and the formatting helper
 * available to every page, so the sidebar and header always show real data.
 */
@ControllerAdvice
public class GlobalModelAttributes {

    private final UserRepository userRepository;
    private final SettingsService settingsService;
    private final ViewHelper viewHelper;

    public GlobalModelAttributes(UserRepository userRepository,
                                 SettingsService settingsService,
                                 ViewHelper viewHelper) {
        this.userRepository = userRepository;
        this.settingsService = settingsService;
        this.viewHelper = viewHelper;
    }

    @ModelAttribute("currentUser")
    public User currentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        return userRepository.findByUsernameIgnoreCase(authentication.getName()).orElse(null);
    }

    @ModelAttribute("isOwner")
    public boolean isOwner(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_OWNER"));
    }

    @ModelAttribute("canManageCustomers")
    public boolean canManageCustomers(Authentication authentication) {
        User user = currentUser(authentication);
        return user != null && user.mayManageCustomers();
    }

    @ModelAttribute("settings")
    public BusinessSettings settings() {
        // Public pages (login, errors) are rendered before any account is known,
        // so no business is bound: there is nothing tenant-scoped to read and the
        // page must not create a settings row for a guessed business.
        return TenantContext.isSet() ? settingsService.get() : null;
    }

    @ModelAttribute("fmt")
    public ViewHelper fmt() {
        return viewHelper;
    }
}
