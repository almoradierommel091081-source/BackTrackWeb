package org.example.backtrackweb.service;

import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.example.backtrackweb.dto.ProductForm;
import org.example.backtrackweb.entity.MovementType;
import org.example.backtrackweb.entity.Product;
import org.example.backtrackweb.entity.ProductCategory;
import org.example.backtrackweb.entity.StockMovement;
import org.example.backtrackweb.entity.User;
import org.example.backtrackweb.exception.BusinessException;
import org.example.backtrackweb.exception.NotFoundException;
import org.example.backtrackweb.repository.ProductRepository;
import org.example.backtrackweb.tenant.TenantContext;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Product and inventory management. Stock is only changed through
 * {@link InventoryService}, never by editing a product. Every query is scoped
 * to the business bound to the request.
 */
@Service
public class ProductService {

    private final ProductRepository repository;
    private final InventoryService inventoryService;
    private final CategoryService categoryService;

    public ProductService(ProductRepository repository,
                          InventoryService inventoryService,
                          CategoryService categoryService) {
        this.repository = repository;
        this.inventoryService = inventoryService;
        this.categoryService = categoryService;
    }

    private Long tenant() {
        return TenantContext.require();
    }

    @Transactional(readOnly = true)
    public List<Product> search(String query, Long categoryId, boolean lowStockOnly, boolean includeArchived) {
        Long businessId = tenant();
        Specification<Product> spec = (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("businessId"), businessId));
            if (!includeArchived) {
                predicates.add(cb.isFalse(root.get("archived")));
            }
            if (lowStockOnly) {
                predicates.add(cb.lessThanOrEqualTo(root.get("stockQuantity"), root.get("minStockLevel")));
            }
            if (categoryId != null) {
                predicates.add(cb.equal(root.join("category", JoinType.LEFT).get("id"), categoryId));
            }
            if (query != null && !query.isBlank()) {
                String like = "%" + query.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), like),
                        cb.like(cb.lower(root.get("sku")), like)
                ));
            }
            cq.orderBy(cb.asc(root.get("name")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return repository.findAll(spec);
    }

    @Transactional(readOnly = true)
    public List<Product> findActive() {
        return repository.findByArchivedFalseAndBusinessIdOrderByNameAsc(tenant());
    }

    @Transactional(readOnly = true)
    public Product get(Long id) {
        return repository.findByIdAndBusinessId(id, tenant())
                .orElseThrow(() -> new NotFoundException("Product not found."));
    }

    @Transactional(readOnly = true)
    public Product getByUuid(String uuid) {
        return repository.findByUuidAndBusinessId(uuid, tenant())
                .orElseThrow(() -> new NotFoundException("Product not found."));
    }

    @Transactional(readOnly = true)
    public List<Product> lowStock() {
        return repository.findLowStock(tenant());
    }

    @Transactional(readOnly = true)
    public long countLowStock() {
        return repository.countLowStock(tenant());
    }

    @Transactional(readOnly = true)
    public long countActive() {
        return repository.countByArchivedFalseAndBusinessId(tenant());
    }

    @Transactional(readOnly = true)
    public List<StockMovement> history(Long productId) {
        get(productId);
        return inventoryService.history(productId);
    }

    @Transactional
    public Product create(ProductForm form, User performedBy) {
        String sku = form.getSku().trim();
        if (repository.existsBySkuIgnoreCaseAndBusinessId(sku, tenant())) {
            throw new BusinessException("A product with SKU " + sku + " already exists.");
        }
        Product product = new Product();
        product.setSku(sku);
        product.setName(form.getName().trim());
        product.setDescription(trimToNull(form.getDescription()));
        product.setCategory(resolveCategory(form.getCategoryId()));
        product.setSellingPrice(money(form.getSellingPrice()));
        product.setCostPrice(money(form.getCostPrice()));
        product.setMinStockLevel(Math.max(0, form.getMinStockLevel() == null ? 0 : form.getMinStockLevel()));
        product.setStockQuantity(0);
        product.setArchived(false);
        product = repository.save(product);

        int opening = form.getOpeningStock() == null ? 0 : form.getOpeningStock();
        if (opening > 0) {
            inventoryService.apply(product, opening, MovementType.OPENING,
                    "Opening stock entered when the product was created", null, performedBy);
        }
        return repository.findByIdAndBusinessId(product.getId(), tenant()).orElse(product);
    }

    @Transactional
    public Product update(Long id, ProductForm form) {
        Product product = get(id);
        String sku = form.getSku().trim();
        repository.findBySkuIgnoreCaseAndBusinessId(sku, tenant())
                .filter(other -> !other.getId().equals(product.getId()))
                .ifPresent(other -> {
                    throw new BusinessException("A product with SKU " + sku + " already exists.");
                });
        product.setSku(sku);
        product.setName(form.getName().trim());
        product.setDescription(trimToNull(form.getDescription()));
        product.setCategory(resolveCategory(form.getCategoryId()));
        product.setSellingPrice(money(form.getSellingPrice()));
        product.setCostPrice(money(form.getCostPrice()));
        product.setMinStockLevel(Math.max(0, form.getMinStockLevel() == null ? 0 : form.getMinStockLevel()));
        return repository.save(product);
    }

    @Transactional
    public void setArchived(Long id, boolean archived) {
        Product product = get(id);
        product.setArchived(archived);
        repository.save(product);
    }

    @Transactional
    public void adjustStock(Long productId, int adjustment, String reason, User performedBy) {
        inventoryService.adjustStock(productId, adjustment, reason, performedBy);
    }

    private ProductCategory resolveCategory(Long categoryId) {
        return categoryId == null ? null : categoryService.get(categoryId);
    }

    private BigDecimal money(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value.signum() < 0) {
            throw new BusinessException("Prices cannot be negative.");
        }
        return value.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
