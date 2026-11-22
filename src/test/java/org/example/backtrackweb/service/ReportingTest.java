package org.example.backtrackweb.service;

import org.example.backtrackweb.dto.ProductForm;
import org.example.backtrackweb.dto.SaleForm;
import org.example.backtrackweb.dto.SaleItemForm;
import org.example.backtrackweb.entity.Customer;
import org.example.backtrackweb.entity.Product;
import org.example.backtrackweb.entity.Role;
import org.example.backtrackweb.entity.User;
import org.example.backtrackweb.support.PostgresTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Dashboard and report numbers must match what is really in the database.
 */
class ReportingTest extends PostgresTestBase {

    @Autowired
    private DashboardService dashboardService;
    @Autowired
    private ReportService reportService;
    @Autowired
    private ProductService productService;
    @Autowired
    private SaleService saleService;
    @Autowired
    private CustomerService customerService;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private User owner;

    @BeforeEach
    void setUp() {
        User user = new User();
        user.setUsername("reportowner");
        user.setFullName("Report Owner");
        user.setRole(Role.OWNER);
        user.setActive(true);
        user.setPasswordHash(passwordEncoder.encode("secret123"));
        owner = userRepository.save(user);
    }

    private ProductForm productForm(String sku, String price, int stock) {
        ProductForm form = new ProductForm();
        form.setSku(sku);
        form.setName("Product " + sku);
        form.setSellingPrice(new BigDecimal(price));
        form.setCostPrice(new BigDecimal("5.00"));
        form.setMinStockLevel(1);
        form.setOpeningStock(stock);
        return form;
    }

    @Test
    void emptyDatabaseShowsHonestZeros() {
        var stats = dashboardService.stats();

        assertThat(stats.totalProducts()).isZero();
        assertThat(stats.totalCustomers()).isZero();
        assertThat(stats.totalTransactions()).isZero();
        assertThat(stats.revenueTotal()).isEqualByComparingTo("0.00");
        assertThat(stats.revenueToday()).isEqualByComparingTo("0.00");
        assertThat(dashboardService.lowStockProducts()).isEmpty();
        assertThat(dashboardService.recentSales()).isEmpty();
    }

    @Test
    void dashboardAndReportsReflectRealSales() {
        Product product = productService.create(productForm("R-1", "100.00", 20), owner);

        Customer customer = new Customer();
        customer.setName("Ana Reyes");
        customer.setPhone("09170000001");
        customer = customerService.create(customer);

        SaleForm form = new SaleForm();
        form.setAmountPaid(new BigDecimal("200.00"));
        form.setCustomerId(customer.getId());
        SaleItemForm line = new SaleItemForm();
        line.setProductId(product.getId());
        line.setQuantity(2);
        form.setItems(List.of(line));
        saleService.createSale(form, owner);

        var stats = dashboardService.stats();
        assertThat(stats.totalProducts()).isEqualTo(1);
        assertThat(stats.totalCustomers()).isEqualTo(1);
        assertThat(stats.totalTransactions()).isEqualTo(1);
        assertThat(stats.completedSales()).isEqualTo(1);
        assertThat(stats.revenueTotal()).isEqualByComparingTo("200.00");
        assertThat(stats.revenueToday()).isEqualByComparingTo("200.00");
        assertThat(dashboardService.recentSales()).hasSize(1);

        LocalDate today = LocalDate.now();
        var salesReport = reportService.salesReport(today.minusDays(1), today);
        assertThat(salesReport.sales()).hasSize(1);
        assertThat(salesReport.totalRevenue()).isEqualByComparingTo("200.00");
        assertThat(salesReport.totalCollected()).isEqualByComparingTo("200.00");
        assertThat(salesReport.topProducts()).hasSize(1);
        assertThat(salesReport.topProducts().get(0).quantitySold()).isEqualTo(2);

        var inventory = reportService.inventoryReport();
        assertThat(inventory.totalUnits()).isEqualTo(18);
        assertThat(inventory.retailValue()).isEqualByComparingTo("1800.00");

        var customers = reportService.customerReport(today.minusDays(1), today);
        assertThat(customers).hasSize(1);
        assertThat(customers.get(0).totalSpent()).isEqualByComparingTo("200.00");

        assertThat(reportService.salesCsv(salesReport)).contains("S-000001").contains("200.00");
        assertThat(reportService.inventoryCsv(inventory)).contains("R-1");
    }

    @Test
    void cancelledSalesAreExcludedFromRevenue() {
        Product product = productService.create(productForm("R-2", "50.00", 10), owner);

        SaleForm form = new SaleForm();
        form.setAmountPaid(new BigDecimal("100.00"));
        SaleItemForm line = new SaleItemForm();
        line.setProductId(product.getId());
        line.setQuantity(2);
        form.setItems(List.of(line));
        var sale = saleService.createSale(form, owner);

        saleService.cancel(sale.getId(), "Test cancellation", owner);

        var stats = dashboardService.stats();
        assertThat(stats.revenueTotal()).isEqualByComparingTo("0.00");
        assertThat(stats.totalTransactions()).isEqualTo(1);
        assertThat(stats.cancelledSales()).isEqualTo(1);
        assertThat(productService.get(product.getId()).getStockQuantity()).isEqualTo(10);
    }
}
