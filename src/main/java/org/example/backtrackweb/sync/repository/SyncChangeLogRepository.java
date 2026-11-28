package org.example.backtrackweb.sync.repository;

import org.example.backtrackweb.sync.entity.SyncChangeLogEntry;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SyncChangeLogRepository extends JpaRepository<SyncChangeLogEntry, Long> {

    List<SyncChangeLogEntry> findByBusinessIdAndServerSeqGreaterThanOrderByServerSeqAsc(
            Long businessId, long serverSeq, Pageable pageable);

    @Query("select coalesce(max(c.serverSeq), 0) from SyncChangeLogEntry c where c.businessId = :businessId")
    long maxServerSeq(@Param("businessId") Long businessId);
}
