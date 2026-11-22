package org.example.backtrackweb.repository;

import org.example.backtrackweb.entity.NumberSequence;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface NumberSequenceRepository extends JpaRepository<NumberSequence, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select n from NumberSequence n where n.name = :name")
    Optional<NumberSequence> findForUpdate(@Param("name") String name);
}
