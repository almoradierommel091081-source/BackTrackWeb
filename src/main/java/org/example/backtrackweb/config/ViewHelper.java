package org.example.backtrackweb.config;

import org.example.backtrackweb.entity.BusinessSettings;
import org.example.backtrackweb.service.SettingsService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Small formatting helper used by the Thymeleaf templates:
 * ${fmt.money(sale.totalAmount)}, ${fmt.dateTime(sale.createdAt)}.
 * The currency symbol comes from the business settings, so a business can
 * switch from PHP to USD without touching the templates.
 */
@Component
public class ViewHelper {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MMM d, yyyy");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a");

    private final SettingsService settingsService;

    public ViewHelper(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    public String currency() {
        return settingsService.get().getCurrency();
    }

    public String money(BigDecimal value) {
        BigDecimal amount = value == null ? BigDecimal.ZERO : value;
        NumberFormat format = NumberFormat.getNumberInstance(Locale.US);
        format.setMinimumFractionDigits(2);
        format.setMaximumFractionDigits(2);
        return currency() + " " + format.format(amount.setScale(2, RoundingMode.HALF_UP));
    }

    public String number(Number value) {
        NumberFormat format = NumberFormat.getNumberInstance(Locale.US);
        return value == null ? "0" : format.format(value);
    }

    public String date(Instant instant) {
        return instant == null ? "" : DATE.format(instant.atZone(ZoneId.systemDefault()));
    }

    public String dateTime(Instant instant) {
        return instant == null ? "" : DATE_TIME.format(instant.atZone(ZoneId.systemDefault()));
    }

    public String date(LocalDate date) {
        return date == null ? "" : DATE.format(date);
    }

    public String businessName() {
        return settingsService.get().getBusinessName();
    }

    public BusinessSettings settings() {
        return settingsService.get();
    }
}
