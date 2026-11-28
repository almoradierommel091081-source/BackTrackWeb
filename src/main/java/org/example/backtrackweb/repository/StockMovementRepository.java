package org.example.backtrackweb.repository;

import org.example.backtrackweb.entity.StockMovement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {

    List<StockMovement> findByProductIdAndBusinessIdOrderByCreatedAtDescIdDesc(Long productId, Long businessId);

    List<StockMovement> findTop10ByBusinessIdOrderByCreatedAtDesc(Long businessId);
}
