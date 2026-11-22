package org.example.backtrackweb.controller;

import jakarta.validation.Valid;
import org.example.backtrackweb.config.CurrentUserService;
import org.example.backtrackweb.dto.PasswordChangeForm;
import org.example.backtrackweb.entity.User;
import org.example.backtrackweb.exception.BusinessException;
import org.example.backtrackweb.service.UserService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Lets every logged-in user change their own password.
 */
@Controller
@RequestMapping("/profile")
public class ProfileController {

    private final UserService userService;
    private final CurrentUserService currentUserService;

    public ProfileController(UserService userService, CurrentUserService currentUserService) {
        this.userService = userService;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/password")
    public String form(Model model) {
        model.addAttribute("passwordChangeForm", new PasswordChangeForm());
        return "profile/password";
    }

    @PostMapping("/password")
    public String change(@Valid @ModelAttribute("passwordChangeForm") PasswordChangeForm form,
                         BindingResult binding, Authentication authentication,
                         RedirectAttributes redirect) {
        if (binding.hasErrors()) {
            return "profile/password";
        }
        try {
            User user = currentUserService.require(authentication);
            userService.changePassword(user, form);
            redirect.addFlashAttribute("success", "Your password was changed.");
            return "redirect:/dashboard";
        } catch (BusinessException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
            return "redirect:/profile/password";
        }
    }
}
