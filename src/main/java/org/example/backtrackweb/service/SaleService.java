package org.example.backtrackweb.service;

import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.example.backtrackweb.dto.PaymentForm;
import org.example.backtrackweb.dto.SaleForm;
import org.example.backtrackweb.dto.SaleItemForm;
import org.example.backtrackweb.entity.Customer;
import org.example.backtrackweb.entity.MovementType;
import org.example.backtrackweb.entity.Payment;
import org.example.backtrackweb.entity.PaymentStatus;
import org.example.backtrackweb.entity.Product;
import org.example.backtrackweb.entity.Sale;
import org.example.backtrackweb.entity.SaleItem;
import org.example.backtrackweb.entity.SaleStatus;
import org.example.backtrackweb.entity.User;
import org.example.backtrackweb.exception.BusinessException;
import org.example.backtrackweb.exception.NotFoundException;
import org.example.backtrackweb.repository.ProductRepository;
import org.example.backtrackweb.repository.SaleRepository;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Everything about sales: creating them (with stock deduction), searching,
 * recording payments and cancelling (with stock restoration).
 *
 * All stock work happens inside one database transaction, so a sale is either
 * saved completely - stock deducted and receipt number used - or not at all.
 */
@Service
public class SaleService {

    private final SaleRepository saleRepository;
    private final ProductRepository productRepository;
    private final CustomerService customerService;
    private final InventoryService inventoryService;
    private final NumberSequenceService numberSequenceService;

    public SaleService(SaleRepository saleRepository,
                       ProductRepository productRepository,
                       CustomerService customerService,
                       InventoryService inventoryService,
                       NumberSequenceService numberSequenceService) {
        this.saleRepository = saleRepository;
        this.productRepository = productRepository;
        this.customerService = customerService;
        this.inventoryService = inventoryService;
        this.numberSequenceService = numberSequenceService;
    }

    @Transactional
    public Sale createSale(SaleForm form, User cashier) {
        if (form.getItems() == null || form.getItems().isEmpty()) {
            throw new BusinessException("Add at least one product to the sale.");
        }

        // Combine rows that repeat the same product, then lock products in a
        // stable order so two cashiers can never deadlock each other.
        Map<Long, Integer> quantities = new TreeMap<>();
        for (SaleItemForm line : form.getItems()) {
            if (line.getProductId() == null) {
                throw new BusinessException("Choose a product for every line.");
            }
            if (line.getQuantity() == null || line.getQuantity() < 1) {
                throw new BusinessException("Every quantity must be at least 1.");
            }
            quantities.merge(line.getProductId(), line.getQuantity(), Integer::sum);
        }

        Customer customer = null;
        if (form.getCustomerId() != null) {
            customer = customerService.get(form.getCustomerId());
            if (customer.isArchived()) {
                throw new BusinessException("That customer is archived. Choose another customer or walk-in.");
            }
        }

        Sale sale = new Sale();
        sale.setSaleNumber(numberSequenceService.nextSaleNumber());
        sale.setCashier(cashier);
        sale.setCustomer(customer);
        sale.setPaymentMethod(form.getPaymentMethod());

        BigDecimal subtotal = BigDecimal.ZERO;
        for (Map.Entry<Long, Integer> entry : quantities.entrySet()) {
            Product product = productRepository.findByIdForUpdate(entry.getKey())
                    .orElseThrow(() -> new BusinessException("A product in this sale no longer exists."));
            if (product.isArchived()) {
                throw new BusinessException(product.getName() + " is archived and cannot be sold.");
            }
            int quantity = entry.getValue();
            if (product.getStockQuantity() < quantity) {
                throw new BusinessException("Not enough stock for " + product.getName()
                        + ". Available: " + product.getStockQuantity() + ", requested: " + quantity + ".");
            }

            BigDecimal unitPrice = product.getSellingPrice().setScale(2, RoundingMode.HALF_UP);
            BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP);
            subtotal = subtotal.add(lineTotal);

            SaleItem item = new SaleItem();
            item.setProduct(product);
            item.setProductName(product.getName());
            item.setSku(product.getSku());
            item.setUnitPrice(unitPrice);
            item.setQuantity(quantity);
            item.setLineTotal(lineTotal);
            sale.addItem(item);

            inventoryService.apply(product, -quantity, MovementType.SALE,
                    "Sold on " + sale.getSaleNumber(), sale.getSaleNumber(), cashier);
        }

        BigDecimal discount = form.getDiscountAmount() == null ? BigDecimal.ZERO : form.getDiscountAmount();
        if (discount.signum() < 0) {
            throw new BusinessException("Discount cannot be negative.");
        }
        if (discount.compareTo(subtotal) > 0) {
            throw new BusinessException("Discount cannot be bigger than the subtotal.");
        }
        BigDecimal total = subtotal.subtract(discount).setScale(2, RoundingMode.HALF_UP);

        BigDecimal tendered = form.getAmountPaid() == null ? BigDecimal.ZERO : form.getAmountPaid();
        if (tendered.signum() < 0) {
            throw new BusinessException("Amount paid cannot be negative.");
        }
        BigDecimal applied = tendered.min(total);
        BigDecimal change = tendered.subtract(applied).setScale(2, RoundingMode.HALF_UP);

        sale.setSubtotal(subtotal);
        sale.setDiscountAmount(discount);
        sale.setTotalAmount(total);
        sale.setAmountPaid(applied);
        sale.setChangeAmount(change);
        sale.setNotes(form.getNotes());

        if (applied.signum() > 0) {
            Payment payment = new Payment();
            payment.setAmount(applied);
            payment.setMethod(form.getPaymentMethod());
            payment.setRecordedBy(cashier);
            payment.setNote("Payment received when the sale was created");
            sale.addPayment(payment);
        }

        applyPaymentState(sale);
        return saleRepository.save(sale);
    }

    /** Adds a payment to a sale that is not fully paid yet. */
    @Transactional
    public Sale addPayment(Long saleId, PaymentForm form, User user) {
        Sale sale = getDetailed(saleId);
        if (sale.getStatus() == SaleStatus.CANCELLED) {
            throw new BusinessException("Cancelled sales cannot receive payments.");
        }
        BigDecimal remaining = sale.getBalance();
        if (remaining.signum() <= 0) {
            throw new BusinessException("This sale is already fully paid.");
        }
        BigDecimal amount = form.getAmount();
        if (amount == null || amount.signum() <= 0) {
            throw new BusinessException("Enter a payment amount greater than zero.");
        }
        if (amount.compareTo(remaining) > 0) {
            throw new BusinessException("Payment is bigger than the remaining balance of "
                    + format(remaining) + ".");
        }

        Payment payment = new Payment();
        payment.setAmount(amount.setScale(2, RoundingMode.HALF_UP));
        payment.setMethod(form.getMethod());
        payment.setNote(form.getNote());
        payment.setRecordedBy(user);
        sale.addPayment(payment);

        sale.setAmountPaid(sale.getAmountPaid().add(amount).setScale(2, RoundingMode.HALF_UP));
        applyPaymentState(sale);
        return saleRepository.save(sale);
    }

    /**
     * Cancels a sale and gives the stock back exactly once.
     * Payment rows stay in the database as a record of what was collected.
     */
    @Transactional
    public Sale cancel(Long saleId, String reason, User user) {
        Sale sale = getDetailed(saleId);
        if (sale.getStatus() == SaleStatus.CANCELLED) {
            throw new BusinessException("This sale is already cancelled.");
        }
        if (reason == null || reason.isBlank()) {
            throw new BusinessException("Please write the reason for cancelling this sale.");
        }

        for (SaleItem item : sale.getItems()) {
            Product product = productRepository.findByIdForUpdate(item.getProduct().getId())
                    .orElseThrow(() -> new BusinessException("Product no longer exists."));
            inventoryService.apply(product, item.getQuantity(), MovementType.CANCELLATION,
                    "Restored from cancelled sale " + sale.getSaleNumber(),
                    sale.getSaleNumber(), user);
        }

        sale.setStatus(SaleStatus.CANCELLED);
        sale.setCancelledAt(Instant.now());
        sale.setCancelledBy(user);
        sale.setCancelReason(reason.trim());
        return saleRepository.save(sale);
    }

    private void applyPaymentState(Sale sale) {
        BigDecimal total = sale.getTotalAmount();
        BigDecimal paid = sale.getAmountPaid();
        if (total.signum() == 0 || paid.compareTo(total) >= 0) {
            sale.setPaymentStatus(PaymentStatus.PAID);
            sale.setStatus(SaleStatus.COMPLETED);
        } else if (paid.signum() > 0) {
            sale.setPaymentStatus(PaymentStatus.PARTIAL);
            sale.setStatus(SaleStatus.IN_PROGRESS);
        } else {
            sale.setPaymentStatus(PaymentStatus.UNPAID);
            sale.setStatus(SaleStatus.PENDING);
        }
    }

    @Transactional(readOnly = true)
    public Sale get(Long id) {
        return saleRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Transaction not found."));
    }

    /** Loads a sale with its items, payments, customer and cashier ready to display. */
    @Transactional(readOnly = true)
    public Sale getDetailed(Long id) {
        Sale sale = saleRepository.findDetailedById(id)
                .orElseThrow(() -> new NotFoundException("Transaction not found."));
        sale.getItems().size();
        sale.getPayments().size();
        return sale;
    }

    @Transactional(readOnly = true)
    public Sale getByNumber(String saleNumber) {
        return saleRepository.findBySaleNumber(saleNumber)
                .orElseThrow(() -> new NotFoundException("Transaction not found."));
    }

    @Transactional(readOnly = true)
    public List<Sale> search(String query, SaleStatus status, LocalDate from, LocalDate to) {
        ZoneId zone = ZoneId.systemDefault();
        Instant fromInstant = from == null ? null : from.atStartOfDay(zone).toInstant();
        Instant toInstant = to == null ? null : to.plusDays(1).atStartOfDay(zone).toInstant();

        Specification<Sale> spec = (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (fromInstant != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), fromInstant));
            }
            if (toInstant != null) {
                predicates.add(cb.lessThan(root.get("createdAt"), toInstant));
            }
            if (query != null && !query.isBlank()) {
                String like = "%" + query.trim().toLowerCase() + "%";
                var customer = root.join("customer", JoinType.LEFT);
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("saleNumber")), like),
                        cb.like(cb.lower(cb.coalesce(customer.get("name"), "walk-in")), like)
                ));
            }
            cq.orderBy(cb.desc(root.get("createdAt")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return saleRepository.findAll(spec);
    }

    @Transactional(readOnly = true)
    public List<Sale> recent() {
        return saleRepository.findTop5ByOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public Map<Long, Integer> quantitiesOf(Sale sale) {
        Map<Long, Integer> quantities = new LinkedHashMap<>();
        sale.getItems().forEach(item -> quantities.put(item.getProduct().getId(), item.getQuantity()));
        return quantities;
    }

    private String format(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
