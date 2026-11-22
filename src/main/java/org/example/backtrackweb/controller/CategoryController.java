package org.example.backtrackweb.controller;

import org.example.backtrackweb.exception.BusinessException;
import org.example.backtrackweb.service.CategoryService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Product categories (owner-only, because POST /products/** is owner-only).
 */
@Controller
@RequestMapping("/products/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("categories", categoryService.findAll());
        return "products/categories";
    }

    @PostMapping
    public String create(@RequestParam String name, RedirectAttributes redirect) {
        try {
            categoryService.create(name);
            redirect.addFlashAttribute("success", "Category was added.");
        } catch (BusinessException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/products/categories";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirect) {
        try {
            categoryService.delete(id);
            redirect.addFlashAttribute("success", "Category was deleted.");
        } catch (DataIntegrityViolationException ex) {
            redirect.addFlashAttribute("error",
                    "This category is still used by products. Move those products to another category first.");
        } catch (BusinessException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/products/categories";
    }
}
