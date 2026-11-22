package org.example.backtrackweb.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Where Spring Security sends a user whose role is not enough for a page.
 */
@Controller
public class AccessDeniedController {

    /**
     * Mapped for every HTTP method: Spring Security forwards here with the
     * method of the denied request, so a blocked POST must work too.
     */
    @RequestMapping("/access-denied")
    public String accessDenied() {
        return "error/403";
    }
}
