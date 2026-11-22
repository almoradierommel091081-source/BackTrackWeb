package org.example.backtrackweb.repository;

import org.example.backtrackweb.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findBySaleIdOrderByPaidAtAsc(Long saleId);
}
