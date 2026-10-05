package com.example.AndesStay.repository;

import com.example.AndesStay.entity.Unit;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UnitRepository extends JpaRepository<Unit, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from Unit u where u.id = :id")
    Optional<Unit> findByIdForUpdate(@Param("id") Long id);
}
