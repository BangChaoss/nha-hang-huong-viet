package com.huongViet.reservation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReservationAuditRepository extends JpaRepository<ReservationAudit, String> {
    List<ReservationAudit> findByReservationIdOrderByChangedAtDesc(String reservationId);
}
