package org.example.backtrackweb.repository;

import org.example.backtrackweb.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long>, JpaSpecificationExecutor<Customer> {

    Optional<Customer> findByIdAndBusinessId(Long id, Long businessId);

    Optional<Customer> findByUuidAndBusinessId(String uuid, Long businessId);

    List<Customer> findByArchivedFalseAndBusinessIdOrderByNameAsc(Long businessId);

    long countByArchivedFalseAndBusinessId(Long businessId);

    Optional<Customer> findByPhoneAndBusinessId(String phone, Long businessId);
}
