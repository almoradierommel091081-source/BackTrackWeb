package org.example.backtrackweb.repository;

import org.example.backtrackweb.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

/**
 * Products, always scoped to one business. Every finder takes the business id
 * so a caller cannot read across tenants.
 */
public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    Optional<Product> findByIdAndBusinessId(Long id, Long businessId);

    Optional<Product> findByUuidAndBusinessId(String uuid, Long businessId);

    Optional<Product> findBySkuIgnoreCaseAndBusinessId(String sku, Long businessId);

    boolean existsBySkuIgnoreCaseAndBusinessId(String sku, Long businessId);

    List<Product> findByArchivedFalseAndBusinessIdOrderByNameAsc(Long businessId);

    long countByArchivedFalseAndBusinessId(Long businessId);

    /**
     * Locks the product row until the current transaction finishes.
     * This is what stops two cashiers from selling the same last item twice.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id = :id and p.businessId = :businessId")
    Optional<Product> findByIdForUpdateAndBusinessId(@Param("id") Long id,
                                                     @Param("businessId") Long businessId);

    @Query("""
            select p from Product p
            where p.businessId = :businessId and p.archived = false
              and p.stockQuantity <= p.minStockLevel
            order by p.stockQuantity asc, p.name asc
            """)
    List<Product> findLowStock(@Param("businessId") Long businessId);

    @Query("""
            select count(p) from Product p
            where p.businessId = :businessId and p.archived = false
              and p.stockQuantity <= p.minStockLevel
            """)
    long countLowStock(@Param("businessId") Long businessId);
}
