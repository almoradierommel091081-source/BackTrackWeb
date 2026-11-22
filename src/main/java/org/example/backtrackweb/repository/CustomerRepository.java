package org.example.backtrackweb.repository;

import org.example.backtrackweb.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long>, JpaSpecificationExecutor<Customer> {

    List<Customer> findByArchivedFalseOrderByNameAsc();

    long countByArchivedFalse();

    Optional<Customer> findByPhone(String phone);

    Optional<Customer> findByNameIgnoreCaseAndPhone(String name, String phone);
}
