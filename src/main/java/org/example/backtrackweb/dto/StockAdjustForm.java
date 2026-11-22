package org.example.backtrackweb.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Manual stock adjustment. Positive numbers add stock, negative numbers remove it.
 */
public class StockAdjustForm {

    @NotNull(message = "Quantity is required")
    private Integer adjustment;

    @NotBlank(message = "Please write the reason for this adjustment")
    @Size(max = 255)
    private String reason;

    public Integer getAdjustment() {
        return adjustment;
    }

    public void setAdjustment(Integer adjustment) {
        this.adjustment = adjustment;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
