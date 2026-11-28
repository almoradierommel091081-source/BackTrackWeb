package org.example.backtrackweb.service;

import org.example.backtrackweb.entity.DiscrepancyStatus;
import org.example.backtrackweb.entity.InventoryDiscrepancy;
import org.example.backtrackweb.entity.Product;
import org.example.backtrackweb.entity.User;
import org.example.backtrackweb.exception.BusinessException;
import org.example.backtrackweb.exception.NotFoundException;
import org.example.backtrackweb.repository.InventoryDiscrepancyRepository;
import org.example.backtrackweb.tenant.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * The accept-and-flag record for offline sales the server could not stock.
 *
 * <p>Rows are never deleted. Resolving only changes the status and records who
 * resolved it and why, so the complete history of the transaction stays
 * available for review.
 */
@Service
public class InventoryDiscrepancyService {

    private final InventoryDiscrepancyRepository repository;

    public InventoryDiscrepancyService(InventoryDiscrepancyRepository repository) {
        this.repository = repository;
    }

    private Long tenant() {
        return TenantContext.require();
    }

    /** Called inside the sale transaction, so the flag and the sale commit together. */
    @Transactional
    public InventoryDiscrepancy record(Product product, int expectedStock, int resultingStock,
                                       int delta, String saleUuid, String movementUuid) {
        InventoryDiscrepancy discrepancy = new InventoryDiscrepancy();
        discrepancy.setProduct(product);
        discrepancy.setExpectedStock(expectedStock);
        discrepancy.setResultingStock(resultingStock);
        discrepancy.setDelta(delta);
        discrepancy.setSaleUuid(saleUuid);
        discrepancy.setMovementUuid(movementUuid);
        discrepancy.setStatus(DiscrepancyStatus.OPEN);
        discrepancy.setNote("Stock went negative for a sale that was completed offline.");
        return repository.save(discrepancy);
    }

    @Transactional(readOnly = true)
    public List<InventoryDiscrepancy> list(DiscrepancyStatus status) {
        Long businessId = tenant();
        return status == null
                ? repository.findByBusinessIdOrderByCreatedAtDesc(businessId)
                : repository.findByBusinessIdAndStatusOrderByCreatedAtDesc(businessId, status);
    }

    @Transactional(readOnly = true)
    public long countOpen() {
        return repository.countByBusinessIdAndStatus(tenant(), DiscrepancyStatus.OPEN);
    }

    @Transactional
    public InventoryDiscrepancy resolve(Long id, String note, DiscrepancyStatus status, User user) {
        InventoryDiscrepancy discrepancy = repository.findByIdAndBusinessId(id, tenant())
                .orElseThrow(() -> new NotFoundException("Discrepancy not found."));
        if (discrepancy.getStatus() == DiscrepancyStatus.RESOLVED) {
            throw new BusinessException("This discrepancy is already resolved.");
        }
        discrepancy.setStatus(status == null ? DiscrepancyStatus.RESOLVED : status);
        discrepancy.setNote(note == null || note.isBlank() ? discrepancy.getNote() : note.trim());
        discrepancy.setResolvedAt(Instant.now());
        discrepancy.setResolvedBy(user);
        return repository.save(discrepancy);
    }
}
