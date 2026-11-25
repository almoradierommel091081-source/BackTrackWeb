package org.example.backtrackweb.service;

import org.example.backtrackweb.dto.ProductForm;
import org.example.backtrackweb.dto.SaleForm;
import org.example.backtrackweb.dto.SaleItemForm;
import org.example.backtrackweb.entity.Product;
import org.example.backtrackweb.entity.Role;
import org.example.backtrackweb.entity.Sale;
import org.example.backtrackweb.entity.User;
import org.example.backtrackweb.support.PostgresTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 1 foundations: the schema is created by Flyway, every business row
 * gets a sync uuid and a business id, and optimistic locking is active.
 * The business rules themselves are covered by the other test classes.
 */
class SyncFoundationTest extends PostgresTestBase {

    @Autowired
    private ProductService productService;
    @Autowired
    private SaleService saleService;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User owner;

    @BeforeEach
    void setUp() {
        User user = new User();
        user.setUsername("syncowner");
        user.setFullName("Sync Owner");
        user.setRole(Role.OWNER);
        user.setActive(true);
        user.setPasswordHash(passwordEncoder.encode("secret123"));
        owner = userRepository.save(user);
    }

    private ProductForm productForm(String sku, String name, String price, int openingStock) {
        ProductForm form = new ProductForm();
        form.setSku(sku);
        form.setName(name);
        form.setSellingPrice(new BigDecimal(price));
        form.setCostPrice(new BigDecimal("5.00"));
        form.setMinStockLevel(1);
        form.setOpeningStock(openingStock);
        return form;
    }

    @Test
    void flywayAppliedTheBaselineAndThePhaseOneMigration() {
        Integer applied = jdbcTemplate.queryForObject(
                "select count(*) from flyway_schema_history where success = true and version in ('1', '2')",
                Integer.class);
        Integer businesses = jdbcTemplate.queryForObject(
                "select count(*) from businesses where id = 1", Integer.class);

        assertThat(applied).isEqualTo(2);
        assertThat(businesses).isEqualTo(1);
    }

    @Test
    void everyNewRecordGetsAUuidAndTheDefaultBusiness() {
        Product product = productService.create(productForm("SYNC-1", "Cable", "50.00", 5), owner);

        assertThat(product.getUuid()).isNotBlank().hasSize(36);
        assertThat(product.getBusinessId()).isEqualTo(1L);
        assertThat(owner.getUuid()).isNotBlank().hasSize(36);
        assertThat(owner.getBusinessId()).isEqualTo(1L);
    }

    @Test
    void changingAProductIncrementsItsVersion() {
        Product product = productService.create(productForm("SYNC-2", "Adapter", "80.00", 3), owner);
        // Creating the product also records its opening stock, which is already
        // one update, so the counter is not assumed to be zero here.
        long versionBeforeEdit = product.getVersion();

        ProductForm edit = productForm("SYNC-2", "Adapter v2", "85.00", 0);
        productService.update(product.getId(), edit);

        Product reloaded = productRepository.findById(product.getId()).orElseThrow();
        assertThat(reloaded.getVersion()).isEqualTo(versionBeforeEdit + 1);
        assertThat(reloaded.getName()).isEqualTo("Adapter v2");
    }

    @Test
    void salesAndTheirLinesAndPaymentsAreSyncIdentified() {
        Product product = productService.create(productForm("SYNC-3", "Lamp", "200.00", 4), owner);

        SaleForm form = new SaleForm();
        form.setAmountPaid(new BigDecimal("200.00"));
        SaleItemForm line = new SaleItemForm();
        line.setProductId(product.getId());
        line.setQuantity(1);
        form.setItems(List.of(line));

        Sale sale = saleService.createSale(form, owner);

        assertThat(sale.getUuid()).isNotBlank().hasSize(36);
        assertThat(sale.getBusinessId()).isEqualTo(1L);
        assertThat(sale.getItems()).allSatisfy(item -> assertThat(item.getUuid()).hasSize(36));
        assertThat(sale.getPayments()).allSatisfy(payment -> {
            assertThat(payment.getUuid()).hasSize(36);
            assertThat(payment.getBusinessId()).isEqualTo(1L);
        });
        assertThat(sale.getUpdatedAt()).isNotNull();
        assertThat(stockMovementRepository.findAll())
                .isNotEmpty()
                .allSatisfy(movement -> assertThat(movement.getUuid()).hasSize(36));
    }
}
