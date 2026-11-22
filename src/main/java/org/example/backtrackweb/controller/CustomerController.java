package org.example.backtrackweb.controller;

import jakarta.validation.Valid;
import org.example.backtrackweb.config.CurrentUserService;
import org.example.backtrackweb.entity.Customer;
import org.example.backtrackweb.entity.Sale;
import org.example.backtrackweb.entity.User;
import org.example.backtrackweb.exception.BusinessException;
import org.example.backtrackweb.service.CustomerService;
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

import java.math.BigDecimal;
import java.util.List;

/**
 * Customer list, details, add, edit, archive and restore.
 * Owners and authorised employees can change customers; everybody can look.
 */
@Controller
@RequestMapping("/customers")
public class CustomerController {

    private final CustomerService customerService;
    private final CurrentUserService currentUserService;

    public CustomerController(CustomerService customerService, CurrentUserService currentUserService) {
        this.customerService = customerService;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    public String list(@RequestParam(required = false) String q,
                       @RequestParam(defaultValue = "false") boolean showArchived,
                       Model model) {
        model.addAttribute("customers", customerService.search(q, showArchived));
        model.addAttribute("q", q);
        model.addAttribute("showArchived", showArchived);
        return "customers/list";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("customer", new Customer());
        return "customers/form";
    }

    @PostMapping
    public String create(@Valid Customer customer, BindingResult binding, Model model,
                         Authentication authentication, RedirectAttributes redirect) {
        requireCustomerManager(authentication);
        if (binding.hasErrors()) {
            model.addAttribute("customer", customer);
            return "customers/form";
        }
        try {
            Customer saved = customerService.create(customer);
            redirect.addFlashAttribute("success", "Customer " + saved.getName() + " was added.");
            return "redirect:/customers/" + saved.getId();
        } catch (BusinessException ex) {
            model.addAttribute("error", ex.getMessage());
            model.addAttribute("customer", customer);
            return "customers/form";
        }
    }

    @GetMapping("/{id}")
    public String view(@PathVariable Long id, Model model) {
        Customer customer = customerService.get(id);
        List<Sale> history = customerService.history(id);
        BigDecimal totalSpent = history.stream()
                .filter(sale -> sale.getStatus() != org.example.backtrackweb.entity.SaleStatus.CANCELLED)
                .map(Sale::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        model.addAttribute("customer", customer);
        model.addAttribute("history", history);
        model.addAttribute("totalSpent", totalSpent);
        return "customers/view";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        model.addAttribute("customer", customerService.get(id));
        return "customers/form";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id, @Valid Customer customer, BindingResult binding,
                         Model model, Authentication authentication, RedirectAttributes redirect) {
        requireCustomerManager(authentication);
        if (binding.hasErrors()) {
            customer.setId(id);
            model.addAttribute("customer", customer);
            return "customers/form";
        }
        try {
            customerService.update(id, customer);
            redirect.addFlashAttribute("success", "Customer details were saved.");
            return "redirect:/customers/" + id;
        } catch (BusinessException ex) {
            model.addAttribute("error", ex.getMessage());
            customer.setId(id);
            model.addAttribute("customer", customer);
            return "customers/form";
        }
    }

    @PostMapping("/{id}/archive")
    public String archive(@PathVariable Long id, Authentication authentication, RedirectAttributes redirect) {
        requireCustomerManager(authentication);
        customerService.setArchived(id, true);
        redirect.addFlashAttribute("success", "Customer was archived. Past sales are kept.");
        return "redirect:/customers";
    }

    @PostMapping("/{id}/restore")
    public String restore(@PathVariable Long id, Authentication authentication, RedirectAttributes redirect) {
        requireCustomerManager(authentication);
        customerService.setArchived(id, false);
        redirect.addFlashAttribute("success", "Customer was restored.");
        return "redirect:/customers/" + id;
    }

    private void requireCustomerManager(Authentication authentication) {
        User user = currentUserService.require(authentication);
        if (!user.mayManageCustomers()) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Your account is not allowed to manage customers.");
        }
    }
}
