package org.example.backtrackweb.sync.service;

import org.example.backtrackweb.entity.BusinessSettings;
import org.example.backtrackweb.entity.Customer;
import org.example.backtrackweb.entity.MovementType;
import org.example.backtrackweb.entity.Product;
import org.example.backtrackweb.entity.ProductCategory;
import org.example.backtrackweb.entity.StockMovement;
import org.example.backtrackweb.entity.User;
import org.example.backtrackweb.exception.BusinessException;
import org.example.backtrackweb.repository.BusinessSettingsRepository;
import org.example.backtrackweb.repository.CustomerRepository;
import org.example.backtrackweb.repository.ProductCategoryRepository;
import org.example.backtrackweb.repository.ProductRepository;
import org.example.backtrackweb.service.InventoryDiscrepancyService;
import org.example.backtrackweb.service.InventoryService;
import org.example.backtrackweb.sync.SyncConflictException;
import org.example.backtrackweb.sync.dto.SyncPayloads;
import org.example.backtrackweb.tenant.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/**
 * Applies master data (products, customers, categories, settings) coming from
 * a device.
 *
 * <p>Records are matched by uuid, never by database id, and every edit carries
 * the version the device started from. If the server's version has moved on,
 * the edit is refused with a version conflict instead of silently overwriting
 * newer data - the device then pulls the current record and decides how to merge.
 *
 * <p>Stock never arrives as an absolute value: it is applied as a delta through
 * {@link InventoryService}, so a sale and an adjustment can never overwrite each
 * other's effect.
 */
@Service
public class SyncMasterDataService {

    private final ProductRepository productRepository;
    private final CustomerRepository customerRepository;
    private final ProductCategoryRepository categoryRepository;
    private final BusinessSettingsRepository settingsRepository;
    private final InventoryService inventoryService;
    private final InventoryDiscrepancyService discrepancyService;

    public SyncMasterDataService(ProductRepository productRepository,
                                 CustomerRepository customerRepository,
                                 ProductCategoryRepository categoryRepository,
                                 BusinessSettingsRepository settingsRepository,
                                 InventoryService inventoryService,
                                 InventoryDiscrepancyService discrepancyService) {
        this.productRepository = productRepository;
        this.customerRepository = customerRepository;
        this.categoryRepository = categoryRepository;
        this.settingsRepository = settingsRepository;
        this.inventoryService = inventoryService;
        this.discrepancyService = discrepancyService;
    }

    private Long tenant() {
        return TenantContext.require();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Product upsertProduct(SyncPayloads.Product payload, User actor) {
        String uuid = requiredUuid(payload.uuid(), "product");
        if (isBlank(payload.sku()) || isBlank(payload.name())) {
            throw new BusinessException("A product needs a SKU and a name.");
        }
        String sku = payload.sku().trim();
        Product product = productRepository.findByUuidAndBusinessId(uuid, tenant()).orElse(null);

        if (product == null) {
            productRepository.findBySkuIgnoreCaseAndBusinessId(sku, tenant())
                    .ifPresent(existing -> {
                        throw new BusinessException("A product with SKU " + sku + " already exists.");
                    });
            product = new Product();
            product.setUuid(uuid);
            product.setSku(sku);
            product.setName(payload.name().trim());
            product.setDescription(trimToNull(payload.description()));
            product.setCategory(resolveCategory(payload.categoryUuid()));
            product.setSellingPrice(money(payload.sellingPrice(), "Selling price"));
            product.setCostPrice(money(payload.costPrice(), "Cost price"));
            product.setMinStockLevel(nonNegative(payload.minStockLevel(), 0));
            product.setArchived(Boolean.TRUE.equals(payload.archived()));
            product.setStockQuantity(0);
            product = productRepository.save(product);

            int opening = payload.stockDelta() == null ? 0 : payload.stockDelta();
            if (opening > 0) {
                inventoryService.apply(product, opening, MovementType.OPENING,
                        "Opening stock recorded on a device", null, actor);
            }
            return productRepository.findById(product.getId()).orElse(product);
        }

        requireVersion("product", payload.baseVersion(), product.getVersion());
        Long existingId = product.getId();
        if (!product.getSku().equalsIgnoreCase(sku)) {
            productRepository.findBySkuIgnoreCaseAndBusinessId(sku, tenant())
                    .filter(other -> !other.getId().equals(existingId))
                    .ifPresent(other -> {
                        throw new BusinessException("A product with SKU " + sku + " already exists.");
                    });
        }
        product.setSku(sku);
        product.setName(payload.name().trim());
        product.setDescription(trimToNull(payload.description()));
        product.setCategory(resolveCategory(payload.categoryUuid()));
        product.setSellingPrice(money(payload.sellingPrice(), "Selling price"));
        product.setCostPrice(money(payload.costPrice(), "Cost price"));
        product.setMinStockLevel(nonNegative(payload.minStockLevel(), product.getMinStockLevel()));
        if (payload.archived() != null) {
            product.setArchived(payload.archived());
        }
        product = productRepository.save(product);

        if (payload.stockDelta() != null && payload.stockDelta() != 0) {
            applyStockChange(product, payload.stockDelta(), "Stock adjustment from a device", actor);
        }
        return productRepository.findById(product.getId()).orElse(product);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Customer upsertCustomer(SyncPayloads.Customer payload) {
        String uuid = requiredUuid(payload.uuid(), "customer");
        if (isBlank(payload.name())) {
            throw new BusinessException("A customer needs a name.");
        }
        Customer customer = customerRepository.findByUuidAndBusinessId(uuid, tenant()).orElse(null);
        if (customer == null) {
            customer = new Customer();
            customer.setUuid(uuid);
        } else {
            requireVersion("customer", payload.baseVersion(), customer.getVersion());
        }
        customer.setName(payload.name().trim());
        customer.setPhone(trimToNull(payload.phone()));
        customer.setEmail(trimToNull(payload.email()));
        customer.setAddress(trimToNull(payload.address()));
        customer.setNotes(trimToNull(payload.notes()));
        if (payload.archived() != null) {
            customer.setArchived(payload.archived());
        }
        return customerRepository.save(customer);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public ProductCategory upsertCategory(SyncPayloads.Category payload) {
        String uuid = requiredUuid(payload.uuid(), "category");
        if (isBlank(payload.name())) {
            throw new BusinessException("A category needs a name.");
        }
        String name = payload.name().trim();
        ProductCategory category = categoryRepository.findByUuidAndBusinessId(uuid, tenant()).orElse(null);
        if (category == null) {
            categoryRepository.findByNameIgnoreCaseAndBusinessId(name, tenant())
                    .ifPresent(existing -> {
                        throw new BusinessException("A category named " + name + " already exists.");
                    });
            category = new ProductCategory();
            category.setUuid(uuid);
        } else {
            requireVersion("category", payload.baseVersion(), category.getVersion());
        }
        category.setName(name);
        return categoryRepository.save(category);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public BusinessSettings updateSettings(SyncPayloads.Settings payload) {
        BusinessSettings settings = settingsRepository.findByBusinessId(tenant())
                .orElseThrow(() -> new BusinessException("This business has no settings yet."));
        requireVersion("settings", payload.baseVersion(), settings.getVersion());

        if (!isBlank(payload.businessName())) {
            settings.setBusinessName(payload.businessName().trim());
        }
        settings.setAddress(payload.address());
        settings.setContactNumber(payload.contactNumber());
        settings.setEmail(payload.email());
        if (!isBlank(payload.currency())) {
            settings.setCurrency(payload.currency().trim());
        }
        if (!isBlank(payload.logoUrl())) {
            settings.setLogoUrl(payload.logoUrl().trim());
        }
        settings.setReceiptFooter(payload.receiptFooter());
        if (payload.defaultMinStockLevel() != null) {
            settings.setDefaultMinStockLevel(Math.max(0, payload.defaultMinStockLevel()));
        }
        return settingsRepository.save(settings);
    }

    /** A stock change that is not part of a sale (damage, recount, delivery). */
    @Transactional(propagation = Propagation.MANDATORY)
    public Product applyStockDelta(SyncPayloads.StockDelta payload, User actor) {
        String uuid = requiredUuid(payload.productUuid(), "product");
        if (payload.delta() == null || payload.delta() == 0) {
            throw new BusinessException("A stock change must not be zero.");
        }
        Product product = productRepository.findByUuidAndBusinessId(uuid, tenant())
                .orElseThrow(() -> new BusinessException("That product does not exist on the server yet."));
        product = productRepository.findByIdForUpdateAndBusinessId(product.getId(), tenant())
                .orElseThrow(() -> new BusinessException("That product does not exist on the server yet."));

        String reason = isBlank(payload.reason()) ? "Stock change from a device" : payload.reason().trim();
        applyStockChange(product, payload.delta(), reason, actor);
        return product;
    }

    private void applyStockChange(Product product, int delta, String reason, User actor) {
        int expected = product.getStockQuantity();
        StockMovement movement = inventoryService.applyFromSync(product, delta, MovementType.ADJUSTMENT,
                reason, null, actor);
        if (movement.getResultingStock() < 0) {
            discrepancyService.record(product, expected, movement.getResultingStock(), delta, null,
                    movement.getUuid());
        }
    }

    private ProductCategory resolveCategory(String categoryUuid) {
        if (isBlank(categoryUuid)) {
            return null;
        }
        return categoryRepository.findByUuidAndBusinessId(categoryUuid, tenant())
                .orElseThrow(() -> new BusinessException("That category does not exist on the server yet."));
    }

    /**
     * Refuses an edit that started from an older version than the server has.
     * The client is told the current version so it can pull and retry.
     */
    private void requireVersion(String what, Long baseVersion, long currentVersion) {
        if (baseVersion == null) {
            throw new SyncConflictException("version_required",
                    "Editing an existing " + what + " requires the version it was edited from.");
        }
        if (baseVersion != currentVersion) {
            throw new SyncConflictException("version_conflict",
                    "This " + what + " was changed on the server since it was downloaded "
                            + "(server version " + currentVersion + ", your base version "
                            + baseVersion + "). Pull it again and re-apply your changes.");
        }
    }

    private String requiredUuid(String uuid, String what) {
        if (isBlank(uuid)) {
            throw new BusinessException("Every " + what + " must carry its uuid.");
        }
        try {
            return UUID.fromString(uuid.trim()).toString();
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("The " + what + " uuid is not a valid uuid.");
        }
    }

    private BigDecimal money(BigDecimal value, String what) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value.signum() < 0) {
            throw new BusinessException(what + " cannot be negative.");
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private int nonNegative(Integer value, int fallback) {
        return value == null ? fallback : Math.max(0, value);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String trimToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }
}
