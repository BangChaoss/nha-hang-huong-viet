package com.huongViet.reservation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

public interface ReservationRepository extends JpaRepository<Reservation, String> {

    /**
     * UC08-BR16: Checks zone capacity for a specific date and time slot.
     * Counts the total partySize of CONFIRMED reservations assigned to tables in the target zone within the dining window.
     */
    @Query("""
        SELECT COALESCE(SUM(r.partySize), 0)
        FROM Reservation r
        JOIN RestaurantTable t ON t.id = r.tableId
        WHERE t.zoneId = :zoneId
          AND r.reservationDate = :date
          AND r.reservationTime BETWEEN :startTime AND :endTime
          AND r.status = 'CONFIRMED'
          AND r.deletedAt IS NULL
        """)
    int sumConfirmedPartySizeByZoneAndSlot(
        @Param("zoneId") String zoneId,
        @Param("date") LocalDate date,
        @Param("startTime") LocalTime startTime,
        @Param("endTime") LocalTime endTime
    );

    /**
     * Advisory query to count pending reservations for a specific date and time slot.
     */
    @Query("""
        SELECT COALESCE(SUM(r.partySize), 0)
        FROM Reservation r
        WHERE r.reservationDate = :date
          AND r.reservationTime = :time
          AND r.status = 'PENDING_HOLD'
          AND r.deletedAt IS NULL
        """)
    int sumPendingPartySizeByDateAndTime(
        @Param("date") LocalDate date,
        @Param("time") LocalTime time
    );

    /**
     * UC08: Find customer booking history with table and zone eager-fetched to prevent N+1 query.
     */
    @Query("""
        SELECT r FROM Reservation r
        LEFT JOIN FETCH r.table t
        LEFT JOIN FETCH t.zone z
        WHERE r.customerId = :customerId
          AND r.deletedAt IS NULL
        ORDER BY r.reservationDate DESC, r.reservationTime DESC
        """)
    List<Reservation> findByCustomerIdWithTableAndZone(@Param("customerId") String customerId);

    /**
     * UC08: Guest lookup by phone with table and zone eager-fetched.
     */
    @Query("""
        SELECT r FROM Reservation r
        LEFT JOIN FETCH r.table t
        LEFT JOIN FETCH t.zone z
        WHERE r.guestPhone = :guestPhone
          AND r.deletedAt IS NULL
        ORDER BY r.reservationDate DESC, r.reservationTime DESC
        """)
    List<Reservation> findByGuestPhoneWithTableAndZone(@Param("guestPhone") String guestPhone);

    /**
     * UC09 foundation: List reservations by date with table and zone eager-fetched.
     */
    @Query("""
        SELECT r FROM Reservation r
        LEFT JOIN FETCH r.table t
        LEFT JOIN FETCH t.zone z
        WHERE r.reservationDate = :date
          AND r.deletedAt IS NULL
        ORDER BY r.reservationTime ASC
        """)
    List<Reservation> findByReservationDateWithTableAndZone(@Param("date") LocalDate date);

    /**
     * Find active reservation by ID with table and zone eager-fetched.
     */
    @Query("""
        SELECT r FROM Reservation r
        LEFT JOIN FETCH r.table t
        LEFT JOIN FETCH t.zone z
        WHERE r.id = :id
          AND r.deletedAt IS NULL
        """)
    Optional<Reservation> findActiveByIdWithTableAndZone(@Param("id") String id);

    Optional<Reservation> findByIdAndDeletedAtIsNull(String id);
}
