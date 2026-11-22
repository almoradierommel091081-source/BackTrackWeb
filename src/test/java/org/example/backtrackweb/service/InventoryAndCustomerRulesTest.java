package org.example.backtrackweb.service;

import org.example.backtrackweb.dto.ProductForm;
import org.example.backtrackweb.entity.Customer;
import org.example.backtrackweb.entity.Product;
import org.example.backtrackweb.entity.Role;
import org.example.backtrackweb.entity.User;
import org.example.backtrackweb.exception.BusinessException;
import org.example.backtrackweb.support.PostgresTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Products, stock adjustments and customers.
 */
class InventoryAndCustomerRulesTest extends PostgresTestBase {

    @Autowired
    private ProductService productService;
    @Autowired
    private CustomerService customerService;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private User owner;

    @BeforeEach
    void setUp() {
        owner = new User();
        owner.setUsername("owner1");
        owner.setFullName("Owner One");
        owner.setRole(Role.OWNER);
        owner.setActive(true);
        owner.setPasswordHash(passwordEncoder.encode("secret123"));
        owner = userRepository.save(owner);
    }

    private ProductForm form(String sku, String name, String price, int openingStock) {
        ProductForm form = new ProductForm();
        form.setSku(sku);
        form.setName(name);
        form.setSellingPrice(new BigDecimal(price));
        form.setCostPrice(new BigDecimal("1.00"));
        form.setMinStockLevel(3);
        form.setOpeningStock(openingStock);
        return form;
    }

    @Test
    void productIsCreatedWithOpeningStockAndHistory() {
        Product product = productService.create(form("P-1", "Ballpen", "12.50", 25), owner);

        assertThat(product.getStockQuantity()).isEqualTo(25);
        assertThat(productService.history(product.getId())).hasSize(1);
        assertThat(productService.history(product.getId()).get(0).getResultingStock()).isEqualTo(25);
    }

    @Test
    void duplicateSkuIsRefused() {
        productService.create(form("P-2", "Ballpen", "12.50", 1), owner);

        assertThatThrownBy(() -> productService.create(form("p-2", "Other pen", "10.00", 1), owner))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void stockAdjustmentIsRecordedAndCannotGoNegative() {
        Product product = productService.create(form("P-3", "Eraser", "8.00", 10), owner);

        productService.adjustStock(product.getId(), -4, "Damaged", owner);
        assertThat(productService.get(product.getId()).getStockQuantity()).isEqualTo(6);

        productService.adjustStock(product.getId(), 20, "Delivery", owner);
        assertThat(productService.get(product.getId()).getStockQuantity()).isEqualTo(26);
        assertThat(productService.history(product.getId())).hasSize(3);

        assertThatThrownBy(() -> productService.adjustStock(product.getId(), -100, "Too much", owner))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Not enough stock");

        assertThat(productService.get(product.getId()).getStockQuantity()).isEqualTo(26);
    }

    @Test
    void lowStockProductsAreReported() {
        productService.create(form("P-4", "Low item", "5.00", 2), owner);
        productService.create(form("P-5", "Healthy item", "5.00", 50), owner);

        assertThat(productService.lowStock())
                .extracting(Product::getSku)
                .containsExactly("P-4");
    }

    @Test
    void archivedProductsDisappearFromTheCatalogueButKeepHistory() {
        Product product = productService.create(form("P-6", "Old item", "5.00", 3), owner);
        productService.setArchived(product.getId(), true);

        assertThat(productService.findActive()).isEmpty();
        assertThat(productService.get(product.getId()).isArchived()).isTrue();
        assertThat(productService.countActive()).isZero();
    }

    @Test
    void customerContactNumberMustBeUnique() {
        Customer first = new Customer();
        first.setName("Maria Santos");
        first.setPhone("09171234567");
        customerService.create(first);

        Customer duplicate = new Customer();
        duplicate.setName("M. Santos");
        duplicate.setPhone("09171234567");

        assertThatThrownBy(() -> customerService.create(duplicate))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already uses that contact number");
    }

    @Test
    void customersCanBeSearchedAndArchived() {
        Customer customer = new Customer();
        customer.setName("Pedro Garcia");
        customer.setPhone("09998887777");
        Customer saved = customerService.create(customer);

        assertThat(customerService.search("pedro", false)).hasSize(1);
        assertThat(customerService.search("nobody", false)).isEmpty();

        customerService.setArchived(saved.getId(), true);
        assertThat(customerService.search(null, false)).isEmpty();
        assertThat(customerService.search(null, true)).hasSize(1);
    }
}
