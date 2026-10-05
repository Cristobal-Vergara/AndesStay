package com.example.AndesStay.repository;

import com.example.AndesStay.entity.Reservation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ReservationRepository
        extends JpaRepository<Reservation, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Reservation r where r.id = :id")
    Optional<Reservation> findByIdForUpdate(@Param("id") Long id);

    @Query("""
            select (count(r) > 0) from Reservation r
            where r.unitId = :unitId and r.status in :statuses
              and r.checkIn < :checkOut and r.checkOut > :checkIn
              and (:excludedId is null or r.id <> :excludedId)
            """)
    boolean existsOverlappingReservation(
            @Param("unitId") Long unitId,
            @Param("statuses") Collection<String> statuses,
            @Param("checkIn") LocalDate checkIn,
            @Param("checkOut") LocalDate checkOut,
            @Param("excludedId") Long excludedId);

    boolean existsByUnitId(Long unitId);

    List<Reservation> findByOwnerId(String ownerId);

    Page<Reservation> findByOwnerId(String ownerId, Pageable pageable);

    @Query("select r from Reservation r where r.checkIn = :date or r.checkOut = :date")
    List<Reservation> findOperationsOn(@Param("date") LocalDate date);

    @Query("select r.status as status, count(r) as total from Reservation r group by r.status")
    List<StatusCount> countGroupedByStatus();

    @Query("select r.unitId as unitId, count(r) as total from Reservation r group by r.unitId")
    List<UnitCount> countGroupedByUnit();

    @Query("select count(distinct r.unitId) from Reservation r where r.status = 'EN_ESTADÍA' and r.unitId in (select u.id from Unit u)")
    long countOccupiedUnits();

    interface StatusCount { String getStatus(); long getTotal(); }
    interface UnitCount { Long getUnitId(); long getTotal(); }

    Optional<Reservation> findByIdAndOwnerId(Long id, String ownerId);

    long countByStatus(String status);

    long countByUnitId(Long unitId);
}
