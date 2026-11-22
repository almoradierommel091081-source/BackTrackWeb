package org.example.backtrackweb.controller;

import org.example.backtrackweb.config.CurrentUserService;
import org.example.backtrackweb.dto.SaleForm;
import org.example.backtrackweb.entity.PaymentMethod;
import org.example.backtrackweb.entity.Product;
import org.example.backtrackweb.entity.Sale;
import org.example.backtrackweb.entity.User;
import org.example.backtrackweb.exception.BusinessException;
import org.example.backtrackweb.service.CustomerService;
import org.example.backtrackweb.service.ProductService;
import org.example.backtrackweb.service.SaleService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/**
 * The point-of-sale page and the receipt.
 */
@Controller
@RequestMapping("/sales")
public class SaleController {

    private final SaleService saleService;
    private final ProductService productService;
    private final CustomerService customerService;
    private final CurrentUserService currentUserService;

    public SaleController(SaleService saleService,
                          ProductService productService,
                          CustomerService customerService,
                          CurrentUserService currentUserService) {
        this.saleService = saleService;
        this.productService = productService;
        this.customerService = customerService;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/new")
    public String newSale(@RequestParam(required = false) Long customerId, Model model) {
        List<Product> products = productService.findActive();
        SaleForm saleForm = new SaleForm();
        saleForm.setCustomerId(customerId);
        model.addAttribute("saleForm", saleForm);
        model.addAttribute("products", products);
        model.addAttribute("customers", customerService.findActive());
        model.addAttribute("paymentMethods", PaymentMethod.values());
        return "sales/new";
    }

    @PostMapping
    public String create(@ModelAttribute("saleForm") SaleForm saleForm,
                         Authentication authentication,
                         RedirectAttributes redirect) {
        try {
            User cashier = currentUserService.require(authentication);
            Sale sale = saleService.createSale(saleForm, cashier);
            redirect.addFlashAttribute("success",
                    "Sale " + sale.getSaleNumber() + " was saved and stock was updated.");
            return "redirect:/sales/" + sale.getId() + "/receipt";
        } catch (BusinessException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
            return "redirect:/sales/new";
        }
    }

    @GetMapping("/{id}/receipt")
    public String receipt(@PathVariable Long id, Model model) {
        model.addAttribute("sale", saleService.getDetailed(id));
        return "sales/receipt";
    }
}
