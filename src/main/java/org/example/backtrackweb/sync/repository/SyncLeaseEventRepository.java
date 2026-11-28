package org.example.backtrackweb.sync.repository;

import org.example.backtrackweb.sync.entity.SyncLeaseEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SyncLeaseEventRepository extends JpaRepository<SyncLeaseEvent, Long> {

    List<SyncLeaseEvent> findByBusinessIdOrderByCreatedAtDesc(Long businessId);
}
