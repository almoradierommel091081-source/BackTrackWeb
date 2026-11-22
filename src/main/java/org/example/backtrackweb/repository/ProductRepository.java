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

public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    Optional<Product> findBySkuIgnoreCase(String sku);

    boolean existsBySkuIgnoreCase(String sku);

    List<Product> findByArchivedFalseOrderByNameAsc();

    long countByArchivedFalse();

    /**
     * Locks the product row until the current transaction finishes.
     * This is what stops two cashiers from selling the same last item twice.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id = :id")
    Optional<Product> findByIdForUpdate(@Param("id") Long id);

    @Query("""
            select p from Product p
            where p.archived = false and p.stockQuantity <= p.minStockLevel
            order by p.stockQuantity asc, p.name asc
            """)
    List<Product> findLowStock();

    @Query("select count(p) from Product p where p.archived = false and p.stockQuantity <= p.minStockLevel")
    long countLowStock();
}
