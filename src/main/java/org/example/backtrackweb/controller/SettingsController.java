package org.example.backtrackweb.controller;

import jakarta.validation.Valid;
import org.example.backtrackweb.entity.BusinessSettings;
import org.example.backtrackweb.service.SettingsService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Business information used across the app and on receipts (owner only).
 */
@Controller
@RequestMapping("/settings")
public class SettingsController {

    private final SettingsService settingsService;

    public SettingsController(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @GetMapping
    public String form(Model model) {
        model.addAttribute("businessSettings", settingsService.get());
        return "settings/form";
    }

    @PostMapping
    public String save(@Valid @ModelAttribute("businessSettings") BusinessSettings businessSettings,
                       BindingResult binding, RedirectAttributes redirect) {
        if (binding.hasErrors()) {
            return "settings/form";
        }
        settingsService.save(businessSettings);
        redirect.addFlashAttribute("success", "Business settings were saved.");
        return "redirect:/settings";
    }
}
