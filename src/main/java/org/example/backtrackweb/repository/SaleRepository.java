package org.example.backtrackweb.repository;

import org.example.backtrackweb.entity.Sale;
import org.example.backtrackweb.entity.SaleStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SaleRepository extends JpaRepository<Sale, Long>, JpaSpecificationExecutor<Sale> {

    Optional<Sale> findBySaleNumber(String saleNumber);

    @Query("""
            select distinct s from Sale s
            left join fetch s.items i
            left join fetch s.customer
            left join fetch s.cashier
            where s.id = :id
            """)
    Optional<Sale> findDetailedById(@Param("id") Long id);

    List<Sale> findTop5ByOrderByCreatedAtDesc();

    List<Sale> findByCustomerIdOrderByCreatedAtDesc(Long customerId);

    long countByStatus(SaleStatus status);

    long countByCreatedAtGreaterThanEqual(Instant from);

    long countByCreatedAtGreaterThanEqualAndStatusNot(Instant from, SaleStatus excluded);

    @Query("""
            select coalesce(sum(s.totalAmount), 0) from Sale s
            where s.status <> :excluded and s.createdAt >= :from and s.createdAt < :to
            """)
    BigDecimal sumRevenueBetween(@Param("from") Instant from,
                                 @Param("to") Instant to,
                                 @Param("excluded") SaleStatus excluded);

    @Query("""
            select coalesce(sum(s.totalAmount), 0) from Sale s
            where s.status <> :excluded
            """)
    BigDecimal sumRevenue(@Param("excluded") SaleStatus excluded);

    @Query("""
            select coalesce(sum(s.amountPaid), 0) from Sale s
            where s.status <> :excluded and s.createdAt >= :from and s.createdAt < :to
            """)
    BigDecimal sumCollectedBetween(@Param("from") Instant from,
                                   @Param("to") Instant to,
                                   @Param("excluded") SaleStatus excluded);
}
