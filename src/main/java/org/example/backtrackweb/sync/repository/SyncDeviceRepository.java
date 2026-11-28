package org.example.backtrackweb.sync.repository;

import org.example.backtrackweb.sync.entity.SyncDevice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SyncDeviceRepository extends JpaRepository<SyncDevice, Long> {

    Optional<SyncDevice> findByUuidAndBusinessId(String uuid, Long businessId);

    Optional<SyncDevice> findByTokenHash(String tokenHash);

    List<SyncDevice> findByBusinessIdOrderByCreatedAtDesc(Long businessId);
}
