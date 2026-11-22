package org.example.backtrackweb.service;

import org.example.backtrackweb.entity.NumberSequence;
import org.example.backtrackweb.repository.NumberSequenceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Hands out human friendly document numbers such as S-000001.
 * The counter row is locked, so concurrent sales cannot reuse a number.
 */
@Service
public class NumberSequenceService {

    private static final String SALE_SEQUENCE = "sale";

    private final NumberSequenceRepository repository;

    public NumberSequenceService(NumberSequenceRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public String nextSaleNumber() {
        NumberSequence sequence = repository.findForUpdate(SALE_SEQUENCE)
                .orElseGet(() -> repository.save(new NumberSequence(SALE_SEQUENCE, 1L)));
        long value = sequence.getNextValue();
        sequence.setNextValue(value + 1);
        repository.save(sequence);
        return String.format("S-%06d", value);
    }
}
