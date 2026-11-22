package org.example.backtrackweb.controller;

import jakarta.validation.Valid;
import org.example.backtrackweb.config.CurrentUserService;
import org.example.backtrackweb.dto.PaymentForm;
import org.example.backtrackweb.entity.PaymentMethod;
import org.example.backtrackweb.entity.SaleStatus;
import org.example.backtrackweb.entity.User;
import org.example.backtrackweb.exception.BusinessException;
import org.example.backtrackweb.service.SaleService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
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

import java.time.LocalDate;

/**
 * Transaction list, details, payments and cancellation.
 */
@Controller
@RequestMapping("/transactions")
public class TransactionController {

    private final SaleService saleService;
    private final CurrentUserService currentUserService;

    public TransactionController(SaleService saleService, CurrentUserService currentUserService) {
        this.saleService = saleService;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    public String list(@RequestParam(required = false) String q,
                       @RequestParam(required = false) SaleStatus status,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                       Model model) {
        model.addAttribute("sales", saleService.search(q, status, from, to));
        model.addAttribute("q", q);
        model.addAttribute("status", status);
        model.addAttribute("from", from);
        model.addAttribute("to", to);
        model.addAttribute("statuses", SaleStatus.values());
        return "transactions/list";
    }

    @GetMapping("/{id}")
    public String view(@PathVariable Long id, Model model) {
        model.addAttribute("sale", saleService.getDetailed(id));
        model.addAttribute("paymentForm", new PaymentForm());
        model.addAttribute("paymentMethods", PaymentMethod.values());
        return "transactions/view";
    }

    @PostMapping("/{id}/payments")
    public String addPayment(@PathVariable Long id, @Valid PaymentForm paymentForm, BindingResult binding,
                             Authentication authentication, RedirectAttributes redirect) {
        if (binding.hasErrors()) {
            redirect.addFlashAttribute("error", binding.getAllErrors().get(0).getDefaultMessage());
            return "redirect:/transactions/" + id;
        }
        try {
            User user = currentUserService.require(authentication);
            saleService.addPayment(id, paymentForm, user);
            redirect.addFlashAttribute("success", "Payment recorded.");
        } catch (BusinessException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/transactions/" + id;
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasRole('OWNER')")
    public String cancel(@PathVariable Long id,
                         @RequestParam String reason,
                         Authentication authentication,
                         RedirectAttributes redirect) {
        try {
            User user = currentUserService.require(authentication);
            saleService.cancel(id, reason, user);
            redirect.addFlashAttribute("success",
                    "Transaction cancelled. Stock was returned and the history is kept.");
        } catch (BusinessException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/transactions/" + id;
    }
}
