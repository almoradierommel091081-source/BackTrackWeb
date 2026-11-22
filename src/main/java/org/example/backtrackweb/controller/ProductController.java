package org.example.backtrackweb.controller;

import jakarta.validation.Valid;
import org.example.backtrackweb.config.CurrentUserService;
import org.example.backtrackweb.dto.ProductForm;
import org.example.backtrackweb.dto.StockAdjustForm;
import org.example.backtrackweb.entity.Product;
import org.example.backtrackweb.entity.User;
import org.example.backtrackweb.exception.BusinessException;
import org.example.backtrackweb.service.CategoryService;
import org.example.backtrackweb.service.ProductService;
import org.example.backtrackweb.service.SettingsService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Products and stock. Reading is open to every logged-in user; changing
 * products or stock is owner-only (enforced in SecurityConfig and here).
 */
@Controller
@RequestMapping("/products")
public class ProductController {

    private final ProductService productService;
    private final CategoryService categoryService;
    private final CurrentUserService currentUserService;
    private final SettingsService settingsService;

    public ProductController(ProductService productService,
                             CategoryService categoryService,
                             CurrentUserService currentUserService,
                             SettingsService settingsService) {
        this.productService = productService;
        this.categoryService = categoryService;
        this.currentUserService = currentUserService;
        this.settingsService = settingsService;
    }

    @GetMapping
    public String list(@RequestParam(required = false) String q,
                       @RequestParam(required = false) Long categoryId,
                       @RequestParam(defaultValue = "false") boolean lowStock,
                       @RequestParam(defaultValue = "false") boolean showArchived,
                       Model model) {
        model.addAttribute("products", productService.search(q, categoryId, lowStock, showArchived));
        model.addAttribute("categories", categoryService.findAll());
        model.addAttribute("q", q);
        model.addAttribute("categoryId", categoryId);
        model.addAttribute("lowStock", lowStock);
        model.addAttribute("showArchived", showArchived);
        return "products/list";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        ProductForm form = new ProductForm();
        form.setMinStockLevel(settingsService.get().getDefaultMinStockLevel());
        model.addAttribute("productForm", form);
        model.addAttribute("categories", categoryService.findAll());
        return "products/form";
    }

    @PostMapping
    public String create(@Valid ProductForm productForm, BindingResult binding, Model model,
                         Authentication authentication, RedirectAttributes redirect) {
        if (binding.hasErrors()) {
            model.addAttribute("categories", categoryService.findAll());
            return "products/form";
        }
        try {
            User user = currentUserService.require(authentication);
            Product product = productService.create(productForm, user);
            redirect.addFlashAttribute("success", "Product " + product.getName() + " was added.");
            return "redirect:/products/" + product.getId();
        } catch (BusinessException ex) {
            model.addAttribute("error", ex.getMessage());
            model.addAttribute("categories", categoryService.findAll());
            return "products/form";
        }
    }

    @GetMapping("/{id}")
    public String view(@PathVariable Long id, Model model) {
        Product product = productService.get(id);
        model.addAttribute("product", product);
        model.addAttribute("movements", productService.history(id));
        model.addAttribute("stockAdjustForm", new StockAdjustForm());
        return "products/view";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        model.addAttribute("productForm", ProductForm.fromEntity(productService.get(id)));
        model.addAttribute("categories", categoryService.findAll());
        return "products/form";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id, @Valid ProductForm productForm, BindingResult binding,
                         Model model, RedirectAttributes redirect) {
        if (binding.hasErrors()) {
            productForm.setId(id);
            model.addAttribute("categories", categoryService.findAll());
            return "products/form";
        }
        try {
            productService.update(id, productForm);
            redirect.addFlashAttribute("success", "Product details were saved.");
            return "redirect:/products/" + id;
        } catch (BusinessException ex) {
            productForm.setId(id);
            model.addAttribute("error", ex.getMessage());
            model.addAttribute("categories", categoryService.findAll());
            return "products/form";
        }
    }

    @PostMapping("/{id}/stock")
    public String adjustStock(@PathVariable Long id, @Valid StockAdjustForm stockAdjustForm,
                              BindingResult binding, Authentication authentication,
                              RedirectAttributes redirect) {
        if (binding.hasErrors()) {
            redirect.addFlashAttribute("error", binding.getAllErrors().get(0).getDefaultMessage());
            return "redirect:/products/" + id;
        }
        try {
            User user = currentUserService.require(authentication);
            productService.adjustStock(id, stockAdjustForm.getAdjustment(), stockAdjustForm.getReason(), user);
            redirect.addFlashAttribute("success", "Stock updated.");
        } catch (BusinessException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/products/" + id;
    }

    @PostMapping("/{id}/archive")
    public String archive(@PathVariable Long id, RedirectAttributes redirect) {
        productService.setArchived(id, true);
        redirect.addFlashAttribute("success", "Product archived. Its history and sales are kept.");
        return "redirect:/products";
    }

    @PostMapping("/{id}/restore")
    public String restore(@PathVariable Long id, RedirectAttributes redirect) {
        productService.setArchived(id, false);
        redirect.addFlashAttribute("success", "Product restored.");
        return "redirect:/products/" + id;
    }
}
