package org.example.backtrackweb.repository;

import org.example.backtrackweb.entity.ProductCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductCategoryRepository extends JpaRepository<ProductCategory, Long> {

    Optional<ProductCategory> findByIdAndBusinessId(Long id, Long businessId);

    Optional<ProductCategory> findByUuidAndBusinessId(String uuid, Long businessId);

    List<ProductCategory> findAllByBusinessIdOrderByNameAsc(Long businessId);

    Optional<ProductCategory> findByNameIgnoreCaseAndBusinessId(String name, Long businessId);

    boolean existsByNameIgnoreCaseAndBusinessId(String name, Long businessId);
}
