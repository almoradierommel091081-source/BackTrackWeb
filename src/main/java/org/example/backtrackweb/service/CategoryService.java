package org.example.backtrackweb.service;

import org.example.backtrackweb.entity.ProductCategory;
import org.example.backtrackweb.exception.BusinessException;
import org.example.backtrackweb.exception.NotFoundException;
import org.example.backtrackweb.repository.ProductCategoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Product categories. Categories are shared lists, so they are kept small and
 * simple: add, rename, delete when unused.
 */
@Service
public class CategoryService {

    private final ProductCategoryRepository repository;

    public CategoryService(ProductCategoryRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<ProductCategory> findAll() {
        return repository.findAllByOrderByNameAsc();
    }

    @Transactional(readOnly = true)
    public ProductCategory get(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Category not found."));
    }

    @Transactional
    public ProductCategory create(String rawName) {
        String name = rawName == null ? "" : rawName.trim();
        if (name.isBlank()) {
            throw new BusinessException("Category name is required.");
        }
        if (repository.existsByNameIgnoreCase(name)) {
            throw new BusinessException("That category already exists.");
        }
        return repository.save(new ProductCategory(name));
    }

    @Transactional
    public void delete(Long id) {
        ProductCategory category = get(id);
        repository.delete(category);
    }
}
