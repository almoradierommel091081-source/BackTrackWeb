package org.example.backtrackweb.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.example.backtrackweb.entity.PaymentMethod;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * What the "New Sale" form sends to the server.
 */
public class SaleForm {

    /** Null = walk-in customer. */
    private Long customerId;

    @NotNull(message = "Choose a payment method")
    private PaymentMethod paymentMethod = PaymentMethod.CASH;

    @NotNull(message = "Discount is required (use 0 for none)")
    @DecimalMin(value = "0", message = "Discount cannot be negative")
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @NotNull(message = "Amount paid is required")
    @DecimalMin(value = "0", message = "Amount paid cannot be negative")
    private BigDecimal amountPaid = BigDecimal.ZERO;

    @Size(max = 500, message = "Notes are too long")
    private String notes;

    @Valid
    @NotEmpty(message = "Add at least one product to the sale")
    private List<SaleItemForm> items = new ArrayList<>();

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(PaymentMethod paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public BigDecimal getDiscountAmount() {
        return discountAmount;
    }

    public void setDiscountAmount(BigDecimal discountAmount) {
        this.discountAmount = discountAmount;
    }

    public BigDecimal getAmountPaid() {
        return amountPaid;
    }

    public void setAmountPaid(BigDecimal amountPaid) {
        this.amountPaid = amountPaid;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public List<SaleItemForm> getItems() {
        return items;
    }

    public void setItems(List<SaleItemForm> items) {
        this.items = items;
    }
}
