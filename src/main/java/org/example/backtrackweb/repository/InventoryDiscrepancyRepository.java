package org.example.backtrackweb.repository;

import org.example.backtrackweb.entity.DiscrepancyStatus;
import org.example.backtrackweb.entity.InventoryDiscrepancy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InventoryDiscrepancyRepository extends JpaRepository<InventoryDiscrepancy, Long> {

    Optional<InventoryDiscrepancy> findByIdAndBusinessId(Long id, Long businessId);

    List<InventoryDiscrepancy> findByBusinessIdAndStatusOrderByCreatedAtDesc(Long businessId,
                                                                            DiscrepancyStatus status);

    List<InventoryDiscrepancy> findByBusinessIdOrderByCreatedAtDesc(Long businessId);

    long countByBusinessIdAndStatus(Long businessId, DiscrepancyStatus status);
}
