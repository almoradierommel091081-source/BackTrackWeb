package org.example.backtrackweb.sync.repository;

import org.example.backtrackweb.sync.entity.SyncNumberLease;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SyncNumberLeaseRepository extends JpaRepository<SyncNumberLease, Long> {

    List<SyncNumberLease> findByBusinessIdAndDeviceIdOrderByFirstValueDesc(Long businessId, Long deviceId);

    /**
     * Highest number ever reserved under a prefix in this business, or null when
     * the prefix has never been used. Used to keep blocks of one device
     * contiguous even after the counter was recreated.
     */
    @Query("select max(l.lastValue) from SyncNumberLease l where l.businessId = :businessId and l.prefix = :prefix")
    Long maxLastValue(@Param("businessId") Long businessId, @Param("prefix") String prefix);

    /** 1 when this number falls inside a block the server actually issued to that device. */
    @Query("""
            select count(l) from SyncNumberLease l
            where l.businessId = :businessId and l.deviceId = :deviceId and l.prefix = :prefix
              and l.firstValue <= :value and l.lastValue >= :value
            """)
    long countIssuedTo(@Param("businessId") Long businessId,
                       @Param("deviceId") Long deviceId,
                       @Param("prefix") String prefix,
                       @Param("value") long value);
}
