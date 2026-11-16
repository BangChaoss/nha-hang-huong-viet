package com.huongViet.reservation;

import com.huongViet.table.RestaurantTable;
import com.huongViet.table.TableRepository;
import com.huongViet.table.TableStatus;
import com.huongViet.table.TableStatusMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Component
public class ReservationTableStatusScheduler {

    private static final Logger logger = LoggerFactory.getLogger(ReservationTableStatusScheduler.class);
    private static final int RESERVE_BEFORE_MINUTES = 60;

    private final ReservationRepository reservationRepository;
    private final TableRepository tableRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public ReservationTableStatusScheduler(
            ReservationRepository reservationRepository,
            TableRepository tableRepository,
            SimpMessagingTemplate messagingTemplate) {
        this.reservationRepository = reservationRepository;
        this.tableRepository = tableRepository;
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Kiểm tra mỗi 60 giây.
     * Chuyển tất cả các bàn trong đơn (kể cả cụm bàn gộp) sang RESERVED trước giờ đặt 60 phút.
     */
    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void reserveTablesBeforeBookingTime() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime reservationDeadline = now.plusMinutes(RESERVE_BEFORE_MINUTES);

        List<Reservation> due = reservationRepository.findConfirmedReservationsDueForTableReservation(
                reservationDeadline.toLocalDate(),
                reservationDeadline.toLocalTime()
        );

        for (Reservation reservation : due) {
            List<String> targetTableIds = new ArrayList<>();
            if (reservation.getMergedTableIds() != null && !reservation.getMergedTableIds().isBlank()) {
                targetTableIds.addAll(Arrays.stream(reservation.getMergedTableIds().split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .distinct()
                        .toList());
            } else if (reservation.getTableId() != null && !reservation.getTableId().isBlank()) {
                targetTableIds.add(reservation.getTableId().trim());
            }

            for (String tableId : targetTableIds) {
                tableRepository.findByIdAndDeletedAtIsNull(tableId).ifPresent(table -> {
                    if (table.getStatus() != TableStatus.AVAILABLE) {
                        return;
                    }

                    table.setStatus(TableStatus.RESERVED);
                    tableRepository.save(table);

                    try {
                        String zoneName = table.getZone() != null ? table.getZone().getZoneName() : null;
                        messagingTemplate.convertAndSend(
                                "/topic/table-status",
                                new TableStatusMessage(
                                        table.getId(),
                                        table.getTableNumber(),
                                        TableStatus.AVAILABLE,
                                        TableStatus.RESERVED,
                                        zoneName
                                )
                        );
                    } catch (Exception ex) {
                        logger.warn("Không thể phát sự kiện trạng thái bàn {}: {}", table.getTableNumber(), ex.getMessage());
                    }

                    logger.info("Bàn {} chuyển AVAILABLE -> RESERVED trước giờ đặt 60 phút (đơn {})",
                            table.getTableNumber(), reservation.getId());
                });
            }
        }
    }
}
