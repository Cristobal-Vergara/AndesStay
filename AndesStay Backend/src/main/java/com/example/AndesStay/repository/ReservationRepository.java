package com.example.AndesStay.repository;

import com.example.AndesStay.entity.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReservationRepository
        extends JpaRepository<Reservation, Long> {

    List<Reservation> findByUnitIdAndStatus(
            Long unitId,
            String status
    );

    long countByStatus(String status);

    long countByUnitId(Long unitId);
}