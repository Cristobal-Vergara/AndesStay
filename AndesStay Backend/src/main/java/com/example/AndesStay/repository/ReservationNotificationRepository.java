package com.example.AndesStay.repository;

import com.example.AndesStay.entity.ReservationNotification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReservationNotificationRepository extends JpaRepository<ReservationNotification, Long> {
    Page<ReservationNotification> findByAudience(String audience, Pageable pageable);
    Page<ReservationNotification> findByAudienceAndRecipientId(String audience, String recipientId, Pageable pageable);
}
