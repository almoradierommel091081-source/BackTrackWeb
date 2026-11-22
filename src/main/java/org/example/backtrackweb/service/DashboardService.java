package org.example.backtrackweb.service;

import org.example.backtrackweb.entity.Product;
import org.example.backtrackweb.entity.Sale;
import org.example.backtrackweb.entity.SaleStatus;
import org.example.backtrackweb.repository.CustomerRepository;
import org.example.backtrackweb.repository.ProductRepository;
import org.example.backtrackweb.repository.SaleRepository;
import org.example.backtrackweb.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

/**
 * Builds the real numbers shown on the dashboard. Nothing here is hardcoded:
 * an empty database produces honest zeros and empty lists.
 */
@Service
public class DashboardService {

    private final ProductRepository productRepository;
    private final CustomerRepository customerRepository;
    private final SaleRepository saleRepository;
    private final UserRepository userRepository;

    public DashboardService(ProductRepository productRepository,
                            CustomerRepository customerRepository,
                            SaleRepository saleRepository,
                            UserRepository userRepository) {
        this.productRepository = productRepository;
        this.customerRepository = customerRepository;
        this.saleRepository = saleRepository;
        this.userRepository = userRepository;
    }

    public record Stats(
            long totalProducts,
            long totalCustomers,
            long totalTransactions,
            long completedSales,
            long cancelledSales,
            long transactionsToday,
            long lowStockCount,
            long employeeCount,
            BigDecimal revenueTotal,
            BigDecimal revenueToday,
            BigDecimal revenueThisWeek,
            BigDecimal revenueThisMonth,
            BigDecimal collectedThisMonth
    ) {
    }

    @Transactional(readOnly = true)
    public Stats stats() {
        ZoneId zone = ZoneId.systemDefault();
        LocalDate today = LocalDate.now(zone);
        Instant startOfToday = today.atStartOfDay(zone).toInstant();
        Instant startOfTomorrow = today.plusDays(1).atStartOfDay(zone).toInstant();
        Instant startOfWeek = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .atStartOfDay(zone).toInstant();
        Instant startOfMonth = today.withDayOfMonth(1).atStartOfDay(zone).toInstant();

        return new Stats(
                productRepository.countByArchivedFalse(),
                customerRepository.countByArchivedFalse(),
                saleRepository.count(),
                saleRepository.countByStatus(SaleStatus.COMPLETED),
                saleRepository.countByStatus(SaleStatus.CANCELLED),
                saleRepository.countByCreatedAtGreaterThanEqual(startOfToday),
                productRepository.countLowStock(),
                userRepository.countByRoleAndActiveTrue(org.example.backtrackweb.entity.Role.EMPLOYEE),
                saleRepository.sumRevenue(SaleStatus.CANCELLED),
                saleRepository.sumRevenueBetween(startOfToday, startOfTomorrow, SaleStatus.CANCELLED),
                saleRepository.sumRevenueBetween(startOfWeek, startOfTomorrow, SaleStatus.CANCELLED),
                saleRepository.sumRevenueBetween(startOfMonth, startOfTomorrow, SaleStatus.CANCELLED),
                saleRepository.sumCollectedBetween(startOfMonth, startOfTomorrow, SaleStatus.CANCELLED)
        );
    }

    @Transactional(readOnly = true)
    public List<Product> lowStockProducts() {
        return productRepository.findLowStock();
    }

    @Transactional(readOnly = true)
    public List<Sale> recentSales() {
        return saleRepository.findTop5ByOrderByCreatedAtDesc();
    }
}
