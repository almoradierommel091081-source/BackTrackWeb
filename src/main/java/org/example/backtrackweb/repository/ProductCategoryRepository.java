package org.example.backtrackweb.repository;

import org.example.backtrackweb.entity.ProductCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductCategoryRepository extends JpaRepository<ProductCategory, Long> {

    List<ProductCategory> findAllByOrderByNameAsc();

    Optional<ProductCategory> findByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCase(String name);
}
