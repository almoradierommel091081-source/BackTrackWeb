package org.example.backtrackweb.service;

import org.example.backtrackweb.dto.PaymentForm;
import org.example.backtrackweb.dto.SaleForm;
import org.example.backtrackweb.dto.SaleItemForm;
import org.example.backtrackweb.entity.Customer;
import org.example.backtrackweb.entity.MovementType;
import org.example.backtrackweb.entity.PaymentMethod;
import org.example.backtrackweb.entity.PaymentStatus;
import org.example.backtrackweb.entity.Product;
import org.example.backtrackweb.entity.Role;
import org.example.backtrackweb.entity.Sale;
import org.example.backtrackweb.entity.SaleStatus;
import org.example.backtrackweb.entity.User;
import org.example.backtrackweb.exception.BusinessException;
import org.example.backtrackweb.support.PostgresTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The most important rules of the system: sales take stock, stock never goes
 * negative, and cancelling a sale returns the stock exactly once.
 */
class SaleFlowTest extends PostgresTestBase {

    @Autowired
    private SaleService saleService;
    @Autowired
    private ProductService productService;
    @Autowired
    private CustomerService customerService;
    @Autowired
    private InventoryService inventoryService;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private User cashier;

    @BeforeEach
    void setUp() {
        cashier = new User();
        cashier.setUsername("cashier");
        cashier.setFullName("Test Cashier");
        cashier.setRole(Role.OWNER);
        cashier.setActive(true);
        cashier.setPasswordHash(passwordEncoder.encode("secret123"));
        cashier = userRepository.save(cashier);
    }

    private Product product(String sku, String name, String price, int stock) {
        Product product = new Product();
        product.setSku(sku);
        product.setName(name);
        product.setSellingPrice(new BigDecimal(price));
        product.setCostPrice(new BigDecimal("5.00"));
        product.setStockQuantity(0);
        product.setMinStockLevel(2);
        product = productRepository.save(product);
        if (stock > 0) {
            inventoryService.apply(product, stock, MovementType.OPENING, "Opening stock", null, cashier);
        }
        return productRepository.findById(product.getId()).orElseThrow();
    }

    private SaleForm form(int quantity, String paid, String discount, Long customerId) {
        SaleForm form = new SaleForm();
        form.setPaymentMethod(PaymentMethod.CASH);
        form.setDiscountAmount(new BigDecimal(discount));
        form.setAmountPaid(new BigDecimal(paid));
        form.setCustomerId(customerId);

        SaleItemForm line = new SaleItemForm();
        line.setProductId(productRepository.findAll().get(0).getId());
        line.setQuantity(quantity);
        form.setItems(List.of(line));
        return form;
    }

    @Test
    void creatingASaleDeductsStockAndSavesTotals() {
        product("SKU-1", "Bottled Water", "20.00", 10);

        SaleForm form = form(3, "100.00", "10.00", null);
        Sale sale = saleService.createSale(form, cashier);

        assertThat(sale.getId()).isNotNull();
        assertThat(sale.getSaleNumber()).startsWith("S-");
        assertThat(sale.getSubtotal()).isEqualByComparingTo("60.00");
        assertThat(sale.getDiscountAmount()).isEqualByComparingTo("10.00");
        assertThat(sale.getTotalAmount()).isEqualByComparingTo("50.00");
        assertThat(sale.getAmountPaid()).isEqualByComparingTo("50.00");
        assertThat(sale.getChangeAmount()).isEqualByComparingTo("50.00");
        assertThat(sale.getStatus()).isEqualTo(SaleStatus.COMPLETED);
        assertThat(sale.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);

        Product saved = productRepository.findAll().get(0);
        assertThat(saved.getStockQuantity()).isEqualTo(7);

        assertThat(stockMovementRepository.findAll())
                .anyMatch(movement -> movement.getType() == MovementType.SALE
                        && movement.getQuantityChange() == -3
                        && movement.getResultingStock() == 7);
    }

    @Test
    void twoLinesOfTheSameProductAreCombined() {
        product("SKU-2", "Face Mask", "15.00", 10);

        SaleForm form = new SaleForm();
        form.setAmountPaid(new BigDecimal("100"));
        SaleItemForm first = new SaleItemForm();
        first.setProductId(productRepository.findAll().get(0).getId());
        first.setQuantity(2);
        SaleItemForm second = new SaleItemForm();
        second.setProductId(productRepository.findAll().get(0).getId());
        second.setQuantity(3);
        form.setItems(List.of(first, second));

        Sale sale = saleService.createSale(form, cashier);

        assertThat(sale.getItems()).hasSize(1);
        assertThat(sale.getItems().get(0).getQuantity()).isEqualTo(5);
        assertThat(productRepository.findAll().get(0).getStockQuantity()).isEqualTo(5);
    }

    @Test
    void saleIsRefusedWhenStockIsNotEnough() {
        product("SKU-3", "Notebook", "30.00", 2);

        SaleForm form = form(5, "150.00", "0", null);

        assertThatThrownBy(() -> saleService.createSale(form, cashier))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Not enough stock");

        assertThat(productRepository.findAll().get(0).getStockQuantity()).isEqualTo(2);
        assertThat(saleRepository.count()).isZero();
    }

    @Test
    void unpaidSaleIsSavedAsPendingWithBalance() {
        product("SKU-4", "Pen", "10.00", 10);

        Sale sale = saleService.createSale(form(4, "0", "0", null), cashier);

        assertThat(sale.getStatus()).isEqualTo(SaleStatus.PENDING);
        assertThat(sale.getPaymentStatus()).isEqualTo(PaymentStatus.UNPAID);
        assertThat(sale.getBalance()).isEqualByComparingTo("40.00");
        assertThat(sale.getPayments()).isEmpty();
    }

    @Test
    void partialPaymentCanBeCompletedLater() {
        product("SKU-5", "Bag", "100.00", 5);

        Sale sale = saleService.createSale(form(2, "50.00", "0", null), cashier);
        assertThat(sale.getStatus()).isEqualTo(SaleStatus.IN_PROGRESS);
        assertThat(sale.getPaymentStatus()).isEqualTo(PaymentStatus.PARTIAL);
        assertThat(sale.getBalance()).isEqualByComparingTo("150.00");

        PaymentForm payment = new PaymentForm();
        payment.setAmount(new BigDecimal("150.00"));
        payment.setMethod(PaymentMethod.GCASH);
        Sale paid = saleService.addPayment(sale.getId(), payment, cashier);

        assertThat(paid.getBalance()).isEqualByComparingTo("0.00");
        assertThat(paid.getStatus()).isEqualTo(SaleStatus.COMPLETED);
        assertThat(paid.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(paymentRepository.findAll()).hasSize(2);
    }

    @Test
    void paymentBiggerThanBalanceIsRefused() {
        product("SKU-6", "Bag", "100.00", 5);
        Sale sale = saleService.createSale(form(1, "20.00", "0", null), cashier);

        PaymentForm payment = new PaymentForm();
        payment.setAmount(new BigDecimal("500.00"));

        assertThatThrownBy(() -> saleService.addPayment(sale.getId(), payment, cashier))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("remaining balance");
    }

    @Test
    void cancellingRestoresStockOnlyOnce() {
        Product product = product("SKU-7", "Shoes", "500.00", 4);

        Sale sale = saleService.createSale(form(2, "1000.00", "0", null), cashier);
        assertThat(productRepository.findById(product.getId()).orElseThrow().getStockQuantity()).isEqualTo(2);

        Sale cancelled = saleService.cancel(sale.getId(), "Customer changed their mind", cashier);
        assertThat(cancelled.getStatus()).isEqualTo(SaleStatus.CANCELLED);
        assertThat(productRepository.findById(product.getId()).orElseThrow().getStockQuantity()).isEqualTo(4);

        assertThatThrownBy(() -> saleService.cancel(sale.getId(), "again", cashier))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already cancelled");

        assertThat(productRepository.findById(product.getId()).orElseThrow().getStockQuantity()).isEqualTo(4);
        assertThat(saleRepository.count()).isEqualTo(1);
    }

    @Test
    void saleCanBelongToACustomerAndShowsInTheirHistory() {
        product("SKU-8", "Cup", "50.00", 5);

        Customer customer = new Customer();
        customer.setName("Maria Santos");
        customer.setPhone("09171234567");
        Customer saved = customerService.create(customer);

        Sale sale = saleService.createSale(form(1, "50.00", "0", saved.getId()), cashier);

        assertThat(sale.getCustomer().getName()).isEqualTo("Maria Santos");
        assertThat(customerService.history(saved.getId())).hasSize(1);
    }

    @Test
    void discountBiggerThanSubtotalIsRefused() {
        product("SKU-9", "Chair", "100.00", 5);

        assertThatThrownBy(() -> saleService.createSale(form(1, "0", "500", null), cashier))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Discount");
    }
}
