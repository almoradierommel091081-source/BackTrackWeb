package org.example.backtrackweb.service;

import org.example.backtrackweb.entity.ProductCategory;
import org.example.backtrackweb.exception.BusinessException;
import org.example.backtrackweb.exception.NotFoundException;
import org.example.backtrackweb.repository.ProductCategoryRepository;
import org.example.backtrackweb.tenant.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Product categories. Categories are shared lists inside one business: add,
 * rename, delete when unused.
 */
@Service
public class CategoryService {

    private final ProductCategoryRepository repository;

    public CategoryService(ProductCategoryRepository repository) {
        this.repository = repository;
    }

    private Long tenant() {
        return TenantContext.require();
    }

    @Transactional(readOnly = true)
    public List<ProductCategory> findAll() {
        return repository.findAllByBusinessIdOrderByNameAsc(tenant());
    }

    @Transactional(readOnly = true)
    public ProductCategory get(Long id) {
        return repository.findByIdAndBusinessId(id, tenant())
                .orElseThrow(() -> new NotFoundException("Category not found."));
    }

    @Transactional(readOnly = true)
    public ProductCategory getByUuid(String uuid) {
        return repository.findByUuidAndBusinessId(uuid, tenant())
                .orElseThrow(() -> new NotFoundException("Category not found."));
    }

    @Transactional
    public ProductCategory create(String rawName) {
        String name = rawName == null ? "" : rawName.trim();
        if (name.isBlank()) {
            throw new BusinessException("Category name is required.");
        }
        if (repository.existsByNameIgnoreCaseAndBusinessId(name, tenant())) {
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
