package com.huongViet.reservation;

import com.huongViet.auth.SystemUserInitializer;
import com.huongViet.table.TableRepository;
import com.huongViet.table.TableStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@Component
public class NoShowScheduler {

    private static final Logger logger = LoggerFactory.getLogger(NoShowScheduler.class);
    private static final Duration GRACE_PERIOD = Duration.ofMinutes(15); // BR-26: 15-minute grace period

    private final ReservationRepository reservationRepository;
    private final ReservationAuditRepository auditRepository;
    private final TableRepository tableRepository;

    public NoShowScheduler(ReservationRepository reservationRepository,
                           ReservationAuditRepository auditRepository,
                           TableRepository tableRepository) {
        this.reservationRepository = reservationRepository;
        this.auditRepository = auditRepository;
        this.tableRepository = tableRepository;
    }

    /**
     * BR-26 / UC13-E1: Runs every 5 minutes to detect overdue CONFIRMED reservations.
     * If reservation_time + 15 minutes < now → NO_SHOW.
     * Releases assigned table back to AVAILABLE.
     */
    @Scheduled(fixedRate = 300_000) // 5 minutes
    @Transactional
    public void processNoShows() {
        LocalDate today = LocalDate.now();
        LocalTime graceTime = LocalTime.now().minus(GRACE_PERIOD);

        List<Reservation> overdue = reservationRepository.findOverdueConfirmedReservations(today, graceTime);

        for (Reservation reservation : overdue) {
            ReservationStatus oldStatus = reservation.getStatus();
            reservation.setStatus(ReservationStatus.NO_SHOW);
            reservation.setCancellationReason("Tự động hủy do quá giờ nhận bàn (No-Show)");

            // Release table if currently RESERVED
            if (reservation.getTableId() != null) {
                tableRepository.findByIdAndDeletedAtIsNull(reservation.getTableId())
                    .ifPresent(table -> {
                        if (table.getStatus() == TableStatus.RESERVED) {
                            table.setStatus(TableStatus.AVAILABLE);
                            tableRepository.save(table);
                        }
                    });
            }

            reservationRepository.save(reservation);

            // Audit trail
            ReservationAudit audit = new ReservationAudit(
                UUID.randomUUID().toString(),
                reservation.getId(),
                SystemUserInitializer.SYSTEM_USER_ID,
                oldStatus,
                ReservationStatus.NO_SHOW,
                reservation.getTableId(),
                null,
                "No-show: Quá 15 phút grace period"
            );
            auditRepository.save(audit);

            logger.info("No-show processed: reservation={}, guest={}", reservation.getId(), reservation.getGuestName());
        }

        if (!overdue.isEmpty()) {
            logger.info("No-show scheduler processed {} reservations", overdue.size());
        }
    }
}
