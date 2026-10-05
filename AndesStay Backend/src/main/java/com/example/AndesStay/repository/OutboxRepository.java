package com.example.AndesStay.repository;

import com.example.AndesStay.entity.OutboxEvent;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OutboxRepository extends JpaRepository<OutboxEvent, String> {
    @Query("select e.id from OutboxEvent e where e.publishedAt is null and e.nextAttemptAt <= :now order by e.createdAt, e.id")
    List<String> findReadyIds(@Param("now") LocalDateTime now, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from OutboxEvent e where e.id = :id")
    Optional<OutboxEvent> findByIdForUpdate(@Param("id") String id);

    long countByPublishedAtIsNull();
}
