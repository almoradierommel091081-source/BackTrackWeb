package org.example.backtrackweb.controller;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Shows the login page. The actual login POST is handled by Spring Security.
 */
@Controller
public class AuthController {

    @GetMapping("/")
    public String root(Authentication authentication) {
        return isLoggedIn(authentication) ? "redirect:/dashboard" : "redirect:/login";
    }

    @GetMapping("/login")
    public String login(Authentication authentication) {
        return isLoggedIn(authentication) ? "redirect:/dashboard" : "login";
    }

    private boolean isLoggedIn(Authentication authentication) {
        return authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof org.springframework.security.authentication.AnonymousAuthenticationToken);
    }
}
