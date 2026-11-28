package org.example.backtrackweb.service;

import org.example.backtrackweb.entity.NumberSequence;
import org.example.backtrackweb.repository.NumberSequenceRepository;
import org.example.backtrackweb.tenant.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Hands out human friendly document numbers and the per-business sync
 * sequence. Every counter row is locked, so concurrent callers cannot reuse a
 * number.
 */
@Service
public class NumberSequenceService {

    private final NumberSequenceRepository repository;

    public NumberSequenceService(NumberSequenceRepository repository) {
        this.repository = repository;
    }

    private String saleSequenceName(Long businessId) {
        return "sale-" + businessId;
    }

    private String syncSequenceName(Long businessId) {
        return "sync-" + businessId;
    }

    /** Web point of sale numbering: S-000001, unchanged from before Phase 2. */
    @Transactional(propagation = Propagation.MANDATORY)
    public String nextSaleNumber() {
        return nextSaleNumber(TenantContext.require());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public String nextSaleNumber(Long businessId) {
        long value = increment(saleSequenceName(businessId), 1);
        return String.format("S-%06d", value);
    }

    /**
     * Reserves a block of receipt numbers for one device.
     *
     * <p>The counter is advanced once by the whole block, so two devices can
     * never receive overlapping ranges and no number is ever issued twice.
     *
     * @return {first, last} inclusive
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public long[] reserveSaleNumbers(Long businessId, int count) {
        if (count < 1) {
            throw new IllegalArgumentException("A number block must contain at least one number.");
        }
        long first = increment(saleSequenceName(businessId), count);
        return new long[]{first, first + count - 1};
    }

    /** Next value of the per-business sync sequence (one ordered stream). */
    @Transactional(propagation = Propagation.MANDATORY)
    public long nextSyncSequence(Long businessId) {
        return increment(syncSequenceName(businessId), 1);
    }

    private long increment(String name, int amount) {
        NumberSequence sequence = repository.findForUpdate(name)
                .orElseGet(() -> repository.save(new NumberSequence(name, 1L)));
        long value = sequence.getNextValue();
        sequence.setNextValue(value + amount);
        repository.save(sequence);
        return value;
    }
}
