package org.example.backtrackweb.service;

import org.example.backtrackweb.entity.MovementType;
import org.example.backtrackweb.entity.Product;
import org.example.backtrackweb.entity.StockMovement;
import org.example.backtrackweb.entity.User;
import org.example.backtrackweb.exception.BusinessException;
import org.example.backtrackweb.repository.ProductRepository;
import org.example.backtrackweb.repository.StockMovementRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * The only place where product stock is allowed to change.
 * Every change also writes a permanent inventory history row.
 */
@Service
public class InventoryService {

    private final ProductRepository productRepository;
    private final StockMovementRepository movementRepository;

    public InventoryService(ProductRepository productRepository,
                            StockMovementRepository movementRepository) {
        this.productRepository = productRepository;
        this.movementRepository = movementRepository;
    }

    /**
     * Applies a stock change to a product that is already loaded and locked.
     * Negative stock is refused here, which is the final guarantee that stock
     * can never go below zero.
     */
    @Transactional
    public void apply(Product product, int change, MovementType type, String reason,
                      String reference, User performedBy) {
        int newStock = product.getStockQuantity() + change;
        if (newStock < 0) {
            throw new BusinessException("Not enough stock for " + product.getName()
                    + ". Available: " + product.getStockQuantity() + ".");
        }
        product.setStockQuantity(newStock);
        productRepository.save(product);

        StockMovement movement = new StockMovement();
        movement.setProduct(product);
        movement.setType(type);
        movement.setQuantityChange(change);
        movement.setResultingStock(newStock);
        movement.setReason(reason);
        movement.setReference(reference);
        movement.setPerformedBy(performedBy);
        movementRepository.save(movement);
    }

    /** Manual adjustment started by a user, with the product row locked. */
    @Transactional
    public void adjustStock(Long productId, int adjustment, String reason, User performedBy) {
        if (adjustment == 0) {
            throw new BusinessException("Enter a quantity to add or remove (for example 10 or -3).");
        }
        if (reason == null || reason.isBlank()) {
            throw new BusinessException("Please write the reason for this stock adjustment.");
        }
        Product product = productRepository.findByIdForUpdate(productId)
                .orElseThrow(() -> new BusinessException("Product not found."));
        apply(product, adjustment, MovementType.ADJUSTMENT, reason.trim(), null, performedBy);
    }

    @Transactional(readOnly = true)
    public List<StockMovement> history(Long productId) {
        return movementRepository.findByProductIdOrderByCreatedAtDescIdDesc(productId);
    }

    @Transactional(readOnly = true)
    public List<StockMovement> recent() {
        return movementRepository.findTop10ByOrderByCreatedAtDesc();
    }
}
