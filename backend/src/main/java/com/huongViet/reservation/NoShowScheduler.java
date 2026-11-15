package com.huongViet.reservation;

import com.huongViet.auth.SystemUserInitializer;
import com.huongViet.table.TableRepository;
import com.huongViet.table.TableStatus;
import com.huongViet.table.TableStatusMessage;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Component
public class NoShowScheduler {

    private static final Logger logger = LoggerFactory.getLogger(NoShowScheduler.class);
    private static final Duration GRACE_PERIOD = Duration.ofMinutes(15); // BR-26: 15-minute grace period

    private final ReservationRepository reservationRepository;
    private final ReservationAuditRepository auditRepository;
    private final TableRepository tableRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public NoShowScheduler(ReservationRepository reservationRepository,
                           ReservationAuditRepository auditRepository,
                           TableRepository tableRepository,
                           SimpMessagingTemplate messagingTemplate) {
        this.reservationRepository = reservationRepository;
        this.auditRepository = auditRepository;
        this.tableRepository = tableRepository;
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * BR-26 / UC13-E1: Runs every 60 seconds to detect overdue CONFIRMED reservations.
     * If reservation_time + 15 minutes < now -> NO_SHOW.
     * Releases assigned table(s) (including merged tables) back to AVAILABLE.
     */
    @Scheduled(fixedDelay = 60_000) // Check every 60 seconds
    @Transactional
    public void processNoShows() {
        LocalDateTime cutoff = LocalDateTime.now().minus(GRACE_PERIOD);

        List<Reservation> overdue = reservationRepository.findOverdueConfirmedReservations(
            cutoff.toLocalDate(),
            cutoff.toLocalTime()
        );

        for (Reservation reservation : overdue) {
            ReservationStatus oldStatus = reservation.getStatus();
            reservation.setStatus(ReservationStatus.NO_SHOW);
            reservation.setCancellationReason("Tự động hủy do quá giờ nhận bàn (No-Show)");

            reservationRepository.saveAndFlush(reservation);
            // Thu thập tất cả các bàn đã gán (kể cả cụm bàn gộp)
            Set<String> targetTableIds = new HashSet<>();
            if (reservation.getMergedTableIds() != null && !reservation.getMergedTableIds().isBlank()) {
                Arrays.stream(reservation.getMergedTableIds().split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .forEach(targetTableIds::add);
            }
            if (reservation.getTableId() != null && !reservation.getTableId().isBlank()) {
                targetTableIds.add(reservation.getTableId().trim());
            }

            // Giải phóng triệt để toàn bộ bàn về AVAILABLE
            for (String tableId : targetTableIds) {
                tableRepository.findByIdAndDeletedAtIsNull(tableId).ifPresent(table -> {
                    TableStatus oldTableStatus = table.getStatus();
                    if (oldTableStatus != TableStatus.MAINTENANCE) {
                        if (oldTableStatus != TableStatus.AVAILABLE) {
                            table.setStatus(TableStatus.AVAILABLE);
                            tableRepository.save(table);
                        }
                        try {
                            String zoneName = table.getZone() != null ? table.getZone().getZoneName() : null;
                            messagingTemplate.convertAndSend("/topic/table-status",
                                new TableStatusMessage(table.getId(), table.getTableNumber(),
                                    oldTableStatus, TableStatus.AVAILABLE, zoneName));
                        } catch (Exception ex) {
                            logger.warn("Không thể phát sự kiện giải phóng bàn {}: {}", table.getTableNumber(), ex.getMessage());
                        }
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