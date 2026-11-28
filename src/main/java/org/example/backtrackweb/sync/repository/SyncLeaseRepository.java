package org.example.backtrackweb.sync.repository;

import jakarta.persistence.LockModeType;
import org.example.backtrackweb.sync.entity.SyncLease;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SyncLeaseRepository extends JpaRepository<SyncLease, Long> {

    /**
     * Loads the lease of a business with a row lock, so two devices claiming at
     * the same moment are serialised and only one can win.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from SyncLease l where l.businessId = :businessId")
    Optional<SyncLease> findForUpdate(@Param("businessId") Long businessId);
}
