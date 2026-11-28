package org.example.backtrackweb.sync.repository;

import org.example.backtrackweb.sync.entity.SyncOperation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SyncOperationRepository extends JpaRepository<SyncOperation, Long> {

    /** An operation id is unique inside its business, never across the database. */
    Optional<SyncOperation> findByOpIdAndBusinessId(String opId, Long businessId);
}
