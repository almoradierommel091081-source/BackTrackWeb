package org.example.backtrackweb.service;

import org.example.backtrackweb.entity.Product;
import org.example.backtrackweb.entity.Sale;
import org.example.backtrackweb.entity.SaleItem;
import org.example.backtrackweb.entity.SaleStatus;
import org.example.backtrackweb.repository.CustomerRepository;
import org.example.backtrackweb.repository.ProductRepository;
import org.example.backtrackweb.repository.SaleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reports built from real sales, products and customers.
 * Date filters are inclusive of the "to" day.
 */
@Service
public class ReportService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ISO_LOCAL_DATE;

    private final SaleRepository saleRepository;
    private final ProductRepository productRepository;
    private final CustomerRepository customerRepository;

    public ReportService(SaleRepository saleRepository,
                         ProductRepository productRepository,
                         CustomerRepository customerRepository) {
        this.saleRepository = saleRepository;
        this.productRepository = productRepository;
        this.customerRepository = customerRepository;
    }

    public record DailyRevenue(LocalDate date, long saleCount, BigDecimal revenue) {
    }

    public record ProductSales(String productName, int quantitySold, BigDecimal revenue) {
    }

    public record SalesReport(
            LocalDate from,
            LocalDate to,
            List<Sale> sales,
            BigDecimal totalRevenue,
            BigDecimal totalCollected,
            BigDecimal totalDiscount,
            List<DailyRevenue> daily,
            List<ProductSales> topProducts
    ) {
    }

    public record InventoryReport(
            List<Product> products,
            long totalUnits,
            BigDecimal costValue,
            BigDecimal retailValue,
            List<Product> lowStock
    ) {
    }

    public record CustomerSummary(String name, String phone, long saleCount, BigDecimal totalSpent, LocalDate lastPurchase) {
    }

    @Transactional(readOnly = true)
    public SalesReport salesReport(LocalDate from, LocalDate to) {
        List<Sale> sales = loadSales(from, to);

        BigDecimal revenue = BigDecimal.ZERO;
        BigDecimal collected = BigDecimal.ZERO;
        BigDecimal discounts = BigDecimal.ZERO;
        long completed = 0;

        Map<LocalDate, long[]> dailyCounts = new LinkedHashMap<>();
        Map<LocalDate, BigDecimal> dailyRevenue = new LinkedHashMap<>();
        Map<String, int[]> productQuantities = new LinkedHashMap<>();
        Map<String, BigDecimal> productRevenue = new LinkedHashMap<>();

        ZoneId zone = ZoneId.systemDefault();
        List<LocalDate> days = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            days.add(d);
            dailyCounts.put(d, new long[]{0});
            dailyRevenue.put(d, BigDecimal.ZERO);
        }

        for (Sale sale : sales) {
            if (sale.getStatus() != SaleStatus.CANCELLED) {
                revenue = revenue.add(sale.getTotalAmount());
                collected = collected.add(sale.getAmountPaid());
                discounts = discounts.add(sale.getDiscountAmount());
                if (sale.getStatus() == SaleStatus.COMPLETED) {
                    completed++;
                }
                LocalDate day = sale.getCreatedAt().atZone(zone).toLocalDate();
                if (dailyCounts.containsKey(day)) {
                    dailyCounts.get(day)[0]++;
                    dailyRevenue.merge(day, sale.getTotalAmount(), BigDecimal::add);

                    for (SaleItem item : sale.getItems()) {
                        productQuantities.computeIfAbsent(item.getProductName(), k -> new int[]{0})[0] += item.getQuantity();
                        productRevenue.merge(item.getProductName(), item.getLineTotal(), BigDecimal::add);
                    }
                }
            }
        }

        List<DailyRevenue> daily = days.stream()
                .map(day -> new DailyRevenue(day, dailyCounts.get(day)[0], dailyRevenue.get(day)))
                .toList();

        List<ProductSales> topProducts = productQuantities.entrySet().stream()
                .map(entry -> new ProductSales(entry.getKey(), entry.getValue()[0],
                        productRevenue.get(entry.getKey())))
                .sorted(Comparator.comparing(ProductSales::revenue).reversed())
                .limit(50)
                .toList();

        return new SalesReport(from, to, sales, revenue.setScale(2, RoundingMode.HALF_UP),
                collected.setScale(2, RoundingMode.HALF_UP),
                discounts.setScale(2, RoundingMode.HALF_UP), daily, topProducts);
    }

    @Transactional(readOnly = true)
    public InventoryReport inventoryReport() {
        List<Product> products = productRepository.findByArchivedFalseOrderByNameAsc();
        long units = 0;
        BigDecimal costValue = BigDecimal.ZERO;
        BigDecimal retailValue = BigDecimal.ZERO;
        for (Product product : products) {
            units += product.getStockQuantity();
            costValue = costValue.add(product.getCostPrice()
                    .multiply(BigDecimal.valueOf(product.getStockQuantity())));
            retailValue = retailValue.add(product.getSellingPrice()
                    .multiply(BigDecimal.valueOf(product.getStockQuantity())));
        }
        return new InventoryReport(products, units,
                costValue.setScale(2, RoundingMode.HALF_UP),
                retailValue.setScale(2, RoundingMode.HALF_UP),
                productRepository.findLowStock());
    }

    @Transactional(readOnly = true)
    public List<CustomerSummary> customerReport(LocalDate from, LocalDate to) {
        List<Sale> sales = loadSales(from, to);
        ZoneId zone = ZoneId.systemDefault();
        Map<String, CustomerSummary> byCustomer = new LinkedHashMap<>();

        for (Sale sale : sales) {
            if (sale.getCustomer() == null || sale.getStatus() == SaleStatus.CANCELLED) {
                continue;
            }
            String key = String.valueOf(sale.getCustomer().getId());
            LocalDate day = sale.getCreatedAt().atZone(zone).toLocalDate();
            CustomerSummary existing = byCustomer.get(key);
            if (existing == null) {
                byCustomer.put(key, new CustomerSummary(sale.getCustomer().getName(),
                        sale.getCustomer().getPhone(), 1, sale.getTotalAmount(), day));
            } else {
                byCustomer.put(key, new CustomerSummary(existing.name(), existing.phone(),
                        existing.saleCount() + 1, existing.totalSpent().add(sale.getTotalAmount()),
                        day.isAfter(existing.lastPurchase()) ? day : existing.lastPurchase()));
            }
        }
        return byCustomer.values().stream()
                .sorted(Comparator.comparing(CustomerSummary::totalSpent).reversed())
                .toList();
    }

    @Transactional(readOnly = true)
    public long countCustomers() {
        return customerRepository.countByArchivedFalse();
    }

    private List<Sale> loadSales(LocalDate from, LocalDate to) {
        ZoneId zone = ZoneId.systemDefault();
        var fromInstant = from.atStartOfDay(zone).toInstant();
        var toInstant = to.plusDays(1).atStartOfDay(zone).toInstant();
        List<Sale> sales = saleRepository.findAll((root, cq, cb) -> {
            cq.orderBy(cb.desc(root.get("createdAt")));
            return cb.and(
                    cb.greaterThanOrEqualTo(root.get("createdAt"), fromInstant),
                    cb.lessThan(root.get("createdAt"), toInstant)
            );
        });
        // Touch lazy collections while the transaction is open.
        sales.forEach(sale -> {
            sale.getItems().size();
            if (sale.getCustomer() != null) {
                sale.getCustomer().getName();
            }
            sale.getCashier().getFullName();
        });
        return sales;
    }

    public String salesCsv(SalesReport report) {
        StringBuilder csv = new StringBuilder("Sale number,Date,Customer,Cashier,Status,Payment status,Subtotal,Discount,Total,Paid,Balance\n");
        ZoneId zone = ZoneId.systemDefault();
        for (Sale sale : report.sales()) {
            csv.append(row(
                    sale.getSaleNumber(),
                    DATE.format(sale.getCreatedAt().atZone(zone).toLocalDate()),
                    sale.getCustomer() == null ? "Walk-in" : sale.getCustomer().getName(),
                    sale.getCashier().getFullName(),
                    sale.getStatus().name(),
                    sale.getPaymentStatus().name(),
                    sale.getSubtotal().toPlainString(),
                    sale.getDiscountAmount().toPlainString(),
                    sale.getTotalAmount().toPlainString(),
                    sale.getAmountPaid().toPlainString(),
                    sale.getBalance().toPlainString()
            ));
        }
        csv.append('\n');
        csv.append(row("TOTAL", "", "", "", "", "", "", report.totalDiscount().toPlainString(),
                report.totalRevenue().toPlainString(), report.totalCollected().toPlainString(), ""));
        return csv.toString();
    }

    public String inventoryCsv(InventoryReport report) {
        StringBuilder csv = new StringBuilder("SKU,Product,Category,Stock,Minimum stock,Low stock,Cost price,Selling price,Stock cost value\n");
        for (Product product : report.products()) {
            csv.append(row(
                    product.getSku(),
                    product.getName(),
                    product.getCategory() == null ? "" : product.getCategory().getName(),
                    String.valueOf(product.getStockQuantity()),
                    String.valueOf(product.getMinStockLevel()),
                    product.isLowStock() ? "YES" : "NO",
                    product.getCostPrice().toPlainString(),
                    product.getSellingPrice().toPlainString(),
                    product.getCostPrice().multiply(BigDecimal.valueOf(product.getStockQuantity())).toPlainString()
            ));
        }
        return csv.toString();
    }

    public String customerCsv(List<CustomerSummary> summaries) {
        StringBuilder csv = new StringBuilder("Customer,Contact number,Sales,Total spent,Last purchase\n");
        for (CustomerSummary summary : summaries) {
            csv.append(row(summary.name(), summary.phone() == null ? "" : summary.phone(),
                    String.valueOf(summary.saleCount()), summary.totalSpent().toPlainString(),
                    DATE.format(summary.lastPurchase())));
        }
        return csv.toString();
    }

    private String row(String... cells) {
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) {
                line.append(',');
            }
            line.append(escape(cells[i]));
        }
        return line.append('\n').toString();
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
