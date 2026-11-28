package org.example.backtrackweb.service;

import jakarta.persistence.criteria.Predicate;
import org.example.backtrackweb.entity.Customer;
import org.example.backtrackweb.entity.Sale;
import org.example.backtrackweb.exception.BusinessException;
import org.example.backtrackweb.exception.NotFoundException;
import org.example.backtrackweb.repository.CustomerRepository;
import org.example.backtrackweb.repository.SaleRepository;
import org.example.backtrackweb.tenant.TenantContext;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Customer management: search, create, edit, archive.
 * Customers are never deleted, only archived, so sales keep their history.
 * Every query is scoped to the business bound to the request.
 */
@Service
public class CustomerService {

    private final CustomerRepository repository;
    private final SaleRepository saleRepository;

    public CustomerService(CustomerRepository repository, SaleRepository saleRepository) {
        this.repository = repository;
        this.saleRepository = saleRepository;
    }

    private Long tenant() {
        return TenantContext.require();
    }

    @Transactional(readOnly = true)
    public List<Customer> search(String query, boolean includeArchived) {
        Long businessId = tenant();
        Specification<Customer> spec = (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("businessId"), businessId));
            if (!includeArchived) {
                predicates.add(cb.isFalse(root.get("archived")));
            }
            if (query != null && !query.isBlank()) {
                String like = "%" + query.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), like),
                        cb.like(cb.lower(cb.coalesce(root.get("phone"), "")), like),
                        cb.like(cb.lower(cb.coalesce(root.get("email"), "")), like)
                ));
            }
            cq.orderBy(cb.asc(root.get("name")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return repository.findAll(spec);
    }

    @Transactional(readOnly = true)
    public List<Customer> findActive() {
        return repository.findByArchivedFalseAndBusinessIdOrderByNameAsc(tenant());
    }

    @Transactional(readOnly = true)
    public Customer get(Long id) {
        return repository.findByIdAndBusinessId(id, tenant())
                .orElseThrow(() -> new NotFoundException("Customer not found."));
    }

    @Transactional(readOnly = true)
    public Customer getByUuid(String uuid) {
        return repository.findByUuidAndBusinessId(uuid, tenant())
                .orElseThrow(() -> new NotFoundException("Customer not found."));
    }

    @Transactional(readOnly = true)
    public List<Sale> history(Long customerId) {
        get(customerId);
        return saleRepository.findByCustomerIdAndBusinessIdOrderByCreatedAtDesc(customerId, tenant());
    }

    @Transactional(readOnly = true)
    public long countActive() {
        return repository.countByArchivedFalseAndBusinessId(tenant());
    }

    @Transactional
    public Customer create(Customer form) {
        validate(form, null);
        form.setId(null);
        form.setArchived(false);
        return repository.save(form);
    }

    @Transactional
    public Customer update(Long id, Customer form) {
        Customer customer = get(id);
        validate(form, customer.getId());
        customer.setName(form.getName().trim());
        customer.setPhone(trimToNull(form.getPhone()));
        customer.setEmail(trimToNull(form.getEmail()));
        customer.setAddress(trimToNull(form.getAddress()));
        customer.setNotes(trimToNull(form.getNotes()));
        return repository.save(customer);
    }

    @Transactional
    public void setArchived(Long id, boolean archived) {
        Customer customer = get(id);
        customer.setArchived(archived);
        repository.save(customer);
    }

    private void validate(Customer form, Long currentId) {
        if (form.getName() == null || form.getName().isBlank()) {
            throw new BusinessException("Customer name is required.");
        }
        String phone = trimToNull(form.getPhone());
        if (phone != null) {
            repository.findByPhoneAndBusinessId(phone, tenant())
                    .filter(existing -> !existing.getId().equals(currentId))
                    .ifPresent(existing -> {
                        throw new BusinessException(
                                "Another customer already uses that contact number (" + existing.getName() + ").");
                    });
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
