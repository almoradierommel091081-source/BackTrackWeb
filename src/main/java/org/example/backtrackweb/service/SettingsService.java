package org.example.backtrackweb.service;

import org.example.backtrackweb.entity.BusinessSettings;
import org.example.backtrackweb.repository.BusinessSettingsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * Reads and saves the single business settings row, creating sensible
 * defaults the first time the system runs.
 */
@Service
public class SettingsService {

    private final BusinessSettingsRepository repository;

    public SettingsService(BusinessSettingsRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public BusinessSettings get() {
        return repository.findById(1L).orElseGet(() -> repository.save(new BusinessSettings()));
    }

    @Transactional
    public BusinessSettings save(BusinessSettings form) {
        BusinessSettings current = get();
        current.setBusinessName(trimOrDefault(form.getBusinessName(), "My Business"));
        current.setAddress(form.getAddress());
        current.setContactNumber(form.getContactNumber());
        current.setEmail(form.getEmail());
        current.setCurrency(trimOrDefault(form.getCurrency(), "PHP"));
        current.setLogoUrl(trimOrDefault(form.getLogoUrl(), "/images/backtrack-logo.png"));
        current.setReceiptFooter(form.getReceiptFooter());
        current.setDefaultMinStockLevel(Math.max(0, form.getDefaultMinStockLevel()));
        return repository.save(current);
    }

    private String trimOrDefault(String value, String fallback) {
        return Objects.requireNonNullElse(value, "").isBlank() ? fallback : value.trim();
    }
}
