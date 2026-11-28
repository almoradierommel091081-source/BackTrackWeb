package org.example.backtrackweb.tenant;

import org.example.backtrackweb.dto.ProductForm;
import org.example.backtrackweb.dto.SaleForm;
import org.example.backtrackweb.dto.SaleItemForm;
import org.example.backtrackweb.entity.Business;
import org.example.backtrackweb.entity.Customer;
import org.example.backtrackweb.entity.Product;
import org.example.backtrackweb.entity.ProductCategory;
import org.example.backtrackweb.entity.Role;
import org.example.backtrackweb.entity.Sale;
import org.example.backtrackweb.entity.User;
import org.example.backtrackweb.exception.NotFoundException;
import org.example.backtrackweb.repository.BusinessRepository;
import org.example.backtrackweb.service.CategoryService;
import org.example.backtrackweb.service.CustomerService;
import org.example.backtrackweb.service.DashboardService;
import org.example.backtrackweb.service.ProductService;
import org.example.backtrackweb.service.SaleService;
import org.example.backtrackweb.service.SettingsService;
import org.example.backtrackweb.support.PostgresTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Two businesses exist at the same time in one database. Everything business A
 * owns must be invisible and unusable to business B - through the services and
 * through the HTTP endpoints, not only in the page markup.
 */
@AutoConfigureMockMvc
class TenantIsolationTest extends PostgresTestBase {

    @Autowired
    private BusinessRepository businessRepository;
    @Autowired
    private ProductService productService;
    @Autowired
    private CustomerService customerService;
    @Autowired
    private CategoryService categoryService;
    @Autowired
    private SaleService saleService;
    @Autowired
    private DashboardService dashboardService;
    @Autowired
    private SettingsService settingsService;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private MockMvc mockMvc;

    private Long businessA;
    private Long businessB;
    private Long productA;
    private Long customerA;
    private Long saleA;

    @BeforeEach
    void createTwoBusinesses() {
        businessA = 1L;
        Business second = businessRepository.save(new Business("Second Shop"));
        businessB = second.getId();

        // Everything below is created inside the tenant it belongs to.
        TenantContext.runAs(businessA, () -> {
            User owner = account("ownerA", "Owner A", Role.OWNER);
            userRepository.save(owner);
            Product product = productService.create(form("A-1", "Product A", "100.00", 10), owner);
            productA = product.getId();

            Customer customer = new Customer();
            customer.setName("Customer A");
            customer.setPhone("09170000001");
            customerA = customerService.create(customer).getId();

            SaleForm saleForm = new SaleForm();
            saleForm.setAmountPaid(new BigDecimal("100.00"));
            SaleItemForm line = new SaleItemForm();
            line.setProductId(product.getId());
            line.setQuantity(1);
            saleForm.setItems(List.of(line));
            saleA = saleService.createSale(saleForm, owner).getId();
        });

        TenantContext.runAs(businessB, () -> {
            User owner = account("ownerB", "Owner B", Role.OWNER);
            userRepository.save(owner);
            productService.create(form("B-1", "Product B", "50.00", 5), owner);
        });

        TenantContext.set(businessA);
    }

    private User account(String username, String fullName, Role role) {
        User user = new User();
        user.setUsername(username);
        user.setFullName(fullName);
        user.setRole(role);
        user.setActive(true);
        user.setPasswordHash(passwordEncoder.encode("secret123"));
        return user;
    }

    private ProductForm form(String sku, String name, String price, int stock) {
        ProductForm form = new ProductForm();
        form.setSku(sku);
        form.setName(name);
        form.setSellingPrice(new BigDecimal(price));
        form.setCostPrice(new BigDecimal("10.00"));
        form.setMinStockLevel(1);
        form.setOpeningStock(stock);
        return form;
    }

    @Test
    void businessCannotReadAnotherBusinessProduct() {
        TenantContext.set(businessB);
        assertThatThrownBy(() -> productService.get(productA))
                .isInstanceOf(NotFoundException.class);

        assertThat(productService.findActive())
                .extracting(Product::getSku)
                .containsExactly("B-1");
    }

    @Test
    void businessCannotUpdateAnotherBusinessProduct() {
        TenantContext.set(businessB);
        assertThatThrownBy(() -> productService.update(productA, form("A-1", "Hijacked", "1.00", 0)))
                .isInstanceOf(NotFoundException.class);

        TenantContext.set(businessA);
        assertThat(productService.get(productA).getName()).isEqualTo("Product A");
    }

    @Test
    void businessCannotReadAnotherBusinessCustomerOrSale() {
        TenantContext.set(businessB);
        assertThatThrownBy(() -> customerService.get(customerA)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> saleService.getDetailed(saleA)).isInstanceOf(NotFoundException.class);
        assertThat(saleService.search(null, null, null, null)).isEmpty();
        assertThat(customerService.search(null, true)).isEmpty();
    }

    @Test
    void dashboardAndSettingsArePerBusiness() {
        TenantContext.set(businessA);
        assertThat(dashboardService.stats().totalTransactions()).isEqualTo(1);

        TenantContext.set(businessB);
        assertThat(dashboardService.stats().totalTransactions()).isZero();
        assertThat(dashboardService.stats().revenueTotal()).isEqualByComparingTo("0.00");
        assertThat(settingsService.get().getBusinessId()).isEqualTo(businessB);
    }

    @Test
    void missingTenantFailsClosedInsteadOfReturningEverything() {
        TenantContext.clear();
        assertThatThrownBy(() -> productService.findActive())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No business is bound");
    }

    @Test
    void httpEndpointsDoNotLeakAcrossBusinesses() throws Exception {
        // The test thread currently holds business A; a real request starts with
        // no binding at all, so the interceptor must derive it from the login.
        TenantContext.clear();

        // Signed in as the owner of business B, hitting ids owned by business A.
        mockMvc.perform(get("/products/" + productA).with(user("ownerB").roles("OWNER")))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/products/" + productA + "/edit").with(user("ownerB").roles("OWNER")))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/customers/" + customerA).with(user("ownerB").roles("OWNER")))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/transactions/" + saleA).with(user("ownerB").roles("OWNER")))
                .andExpect(status().isNotFound());

        // The list endpoint only ever shows the caller's own rows.
        mockMvc.perform(get("/products").with(user("ownerB").roles("OWNER")))
                .andExpect(status().isOk());
    }

    /**
     * The unique indexes are per business: a SKU, a category name and a receipt
     * number may be reused by another business, and are still refused twice
     * inside the same one.
     */
    @Test
    void twoBusinessesMayReuseTheSameSkuCategoryNameAndReceiptNumber() {
        TenantContext.runAs(businessB, () -> {
            User ownerB = userRepository.findByUsernameIgnoreCaseAndBusinessId("ownerB", businessB)
                    .orElseThrow();

            // The same SKU and the same category name as business A.
            productService.create(form("A-1", "Product A copy", "10.00", 1), ownerB);
            categoryService.create("Snacks");
            assertThat(categoryRepository.findByNameIgnoreCaseAndBusinessId("Snacks", businessB))
                    .isPresent();

            // Each business numbers its receipts from 1, so both hold S-000001.
            Product product = productRepository
                    .findBySkuIgnoreCaseAndBusinessId("B-1", businessB).orElseThrow();
            SaleForm saleForm = new SaleForm();
            saleForm.setAmountPaid(new BigDecimal("50.00"));
            SaleItemForm line = new SaleItemForm();
            line.setProductId(product.getId());
            line.setQuantity(1);
            saleForm.setItems(List.of(line));
            assertThat(saleService.createSale(saleForm, ownerB).getSaleNumber())
                    .isEqualTo("S-000001");
        });

        // Business A still resolves its own S-000001, its own A-1 and its own
        // categories - the shared values never cross the tenant boundary.
        TenantContext.set(businessA);
        assertThat(saleService.getByNumber("S-000001").getId()).isEqualTo(saleA);
        assertThat(productRepository.findBySkuIgnoreCaseAndBusinessId("A-1", businessA)).isPresent();
        assertThat(categoryRepository.findByNameIgnoreCaseAndBusinessId("Snacks", businessA)).isEmpty();

        // And a second record with the same name inside one business is still
        // refused by the per-business index.
        categoryService.create("Beverages");
        assertThatThrownBy(() -> categoryRepository.saveAndFlush(new ProductCategory("Beverages")))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(categoryRepository.findAllByBusinessIdOrderByNameAsc(businessA)).hasSize(1);
    }
}
