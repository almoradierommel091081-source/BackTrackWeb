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

    Optional<Sale> findByIdAndBusinessId(Long id, Long businessId);

    Optional<Sale> findByUuidAndBusinessId(String uuid, Long businessId);

    Optional<Sale> findBySaleNumberAndBusinessId(String saleNumber, Long businessId);

    @Query("""
            select distinct s from Sale s
            left join fetch s.items i
            left join fetch s.customer
            left join fetch s.cashier
            where s.id = :id and s.businessId = :businessId
            """)
    Optional<Sale> findDetailedByIdAndBusinessId(@Param("id") Long id,
                                                 @Param("businessId") Long businessId);

    @Query("""
            select distinct s from Sale s
            left join fetch s.items i
            left join fetch s.customer
            left join fetch s.cashier
            where s.uuid = :uuid and s.businessId = :businessId
            """)
    Optional<Sale> findDetailedByUuidAndBusinessId(@Param("uuid") String uuid,
                                                   @Param("businessId") Long businessId);

    boolean existsBySaleNumberAndBusinessId(String saleNumber, Long businessId);

    List<Sale> findTop5ByBusinessIdOrderByCreatedAtDesc(Long businessId);

    List<Sale> findByCustomerIdAndBusinessIdOrderByCreatedAtDesc(Long customerId, Long businessId);

    long countByBusinessId(Long businessId);

    long countByBusinessIdAndStatus(Long businessId, SaleStatus status);

    long countByBusinessIdAndCreatedAtGreaterThanEqual(Long businessId, Instant from);

    @Query("""
            select coalesce(sum(s.totalAmount), 0) from Sale s
            where s.businessId = :businessId and s.status <> :excluded
              and s.createdAt >= :from and s.createdAt < :to
            """)
    BigDecimal sumRevenueBetween(@Param("businessId") Long businessId,
                                 @Param("from") Instant from,
                                 @Param("to") Instant to,
                                 @Param("excluded") SaleStatus excluded);

    @Query("""
            select coalesce(sum(s.totalAmount), 0) from Sale s
            where s.businessId = :businessId and s.status <> :excluded
            """)
    BigDecimal sumRevenue(@Param("businessId") Long businessId,
                          @Param("excluded") SaleStatus excluded);

    @Query("""
            select coalesce(sum(s.amountPaid), 0) from Sale s
            where s.businessId = :businessId and s.status <> :excluded
              and s.createdAt >= :from and s.createdAt < :to
            """)
    BigDecimal sumCollectedBetween(@Param("businessId") Long businessId,
                                   @Param("from") Instant from,
                                   @Param("to") Instant to,
                                   @Param("excluded") SaleStatus excluded);
}
