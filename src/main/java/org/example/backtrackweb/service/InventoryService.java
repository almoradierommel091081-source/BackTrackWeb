package org.example.backtrackweb.service;

import org.example.backtrackweb.entity.MovementType;
import org.example.backtrackweb.entity.Product;
import org.example.backtrackweb.entity.StockMovement;
import org.example.backtrackweb.entity.User;
import org.example.backtrackweb.exception.BusinessException;
import org.example.backtrackweb.repository.ProductRepository;
import org.example.backtrackweb.repository.StockMovementRepository;
import org.example.backtrackweb.tenant.TenantContext;
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
     * Negative stock is refused here, which is the final guarantee that the web
     * point of sale can never oversell.
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

        writeMovement(product, change, newStock, type, reason, reference, performedBy);
    }

    /**
     * Applies a stock change that arrived from a device.
     *
     * <p>A sale completed offline already took the customer's money, so it is
     * never discarded: stock is allowed to go negative and the caller records
     * an inventory discrepancy for the owner to review. The web point of sale
     * keeps using {@link #apply} and still refuses to oversell.
     *
     * @return the movement that was written; its {@code resultingStock} may be
     *         negative, in which case the caller records a discrepancy
     */
    @Transactional
    public StockMovement applyFromSync(Product product, int change, MovementType type, String reason,
                                       String reference, User performedBy) {
        int newStock = product.getStockQuantity() + change;
        product.setStockQuantity(newStock);
        productRepository.save(product);

        return writeMovement(product, change, newStock, type, reason, reference, performedBy);
    }

    private StockMovement writeMovement(Product product, int change, int newStock, MovementType type,
                                        String reason, String reference, User performedBy) {
        StockMovement movement = new StockMovement();
        movement.setProduct(product);
        movement.setType(type);
        movement.setQuantityChange(change);
        movement.setResultingStock(newStock);
        movement.setReason(reason);
        movement.setReference(reference);
        movement.setPerformedBy(performedBy);
        return movementRepository.save(movement);
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
        Product product = productRepository.findByIdForUpdateAndBusinessId(productId, TenantContext.require())
                .orElseThrow(() -> new BusinessException("Product not found."));
        apply(product, adjustment, MovementType.ADJUSTMENT, reason.trim(), null, performedBy);
    }

    @Transactional(readOnly = true)
    public List<StockMovement> history(Long productId) {
        return movementRepository.findByProductIdAndBusinessIdOrderByCreatedAtDescIdDesc(
                productId, TenantContext.require());
    }

    @Transactional(readOnly = true)
    public List<StockMovement> recent() {
        return movementRepository.findTop10ByBusinessIdOrderByCreatedAtDesc(TenantContext.require());
    }
}
