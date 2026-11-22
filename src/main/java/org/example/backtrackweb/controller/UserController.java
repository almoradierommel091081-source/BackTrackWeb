package org.example.backtrackweb.controller;

import jakarta.validation.Valid;
import org.example.backtrackweb.config.CurrentUserService;
import org.example.backtrackweb.dto.UserForm;
import org.example.backtrackweb.entity.Role;
import org.example.backtrackweb.entity.User;
import org.example.backtrackweb.exception.BusinessException;
import org.example.backtrackweb.service.UserService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Account management. The whole /users path is owner-only (see SecurityConfig).
 */
@Controller
@RequestMapping("/users")
public class UserController {

    private final UserService userService;
    private final CurrentUserService currentUserService;

    public UserController(UserService userService, CurrentUserService currentUserService) {
        this.userService = userService;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("users", userService.findAll());
        return "users/list";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        UserForm form = new UserForm();
        form.setActive(true);
        model.addAttribute("userForm", form);
        model.addAttribute("roles", Role.values());
        model.addAttribute("passwordRequired", true);
        return "users/form";
    }

    @PostMapping
    public String create(@Valid UserForm userForm, BindingResult binding, Model model,
                         RedirectAttributes redirect) {
        if (binding.hasErrors()) {
            model.addAttribute("roles", Role.values());
            model.addAttribute("passwordRequired", true);
            return "users/form";
        }
        try {
            userService.create(userForm);
            redirect.addFlashAttribute("success", "Account " + userForm.getUsername() + " was created.");
            return "redirect:/users";
        } catch (BusinessException ex) {
            model.addAttribute("error", ex.getMessage());
            model.addAttribute("roles", Role.values());
            model.addAttribute("passwordRequired", true);
            return "users/form";
        }
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        model.addAttribute("userForm", UserForm.fromEntity(userService.get(id)));
        model.addAttribute("roles", Role.values());
        model.addAttribute("passwordRequired", false);
        return "users/form";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id, @Valid UserForm userForm, BindingResult binding,
                         Model model, Authentication authentication, RedirectAttributes redirect) {
        if (binding.hasErrors()) {
            userForm.setId(id);
            model.addAttribute("roles", Role.values());
            model.addAttribute("passwordRequired", false);
            return "users/form";
        }
        try {
            User acting = currentUserService.require(authentication);
            userService.update(id, userForm, acting);
            redirect.addFlashAttribute("success", "Account was updated.");
            return "redirect:/users";
        } catch (BusinessException ex) {
            userForm.setId(id);
            model.addAttribute("error", ex.getMessage());
            model.addAttribute("roles", Role.values());
            model.addAttribute("passwordRequired", false);
            return "users/form";
        }
    }

    @PostMapping("/{id}/toggle")
    public String toggle(@PathVariable Long id, Authentication authentication, RedirectAttributes redirect) {
        try {
            User acting = currentUserService.require(authentication);
            User target = userService.get(id);
            userService.setActive(id, !target.isActive(), acting);
            redirect.addFlashAttribute("success",
                    target.isActive() ? "Account deactivated." : "Account activated.");
        } catch (BusinessException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/users";
    }
}
