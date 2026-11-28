package org.example.backtrackweb.repository;

import org.example.backtrackweb.entity.BusinessSettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BusinessSettingsRepository extends JpaRepository<BusinessSettings, Long> {

    Optional<BusinessSettings> findByBusinessId(Long businessId);
}
