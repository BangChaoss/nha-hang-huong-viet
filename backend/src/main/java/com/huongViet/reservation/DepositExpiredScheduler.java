package com.huongViet.reservation;

import com.huongViet.auth.SystemUserInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Component
public class DepositExpiredScheduler {

    private static final Logger logger = LoggerFactory.getLogger(DepositExpiredScheduler.class);
    private static final int DEPOSIT_TIMEOUT_MINUTES = 10;

    private final ReservationRepository reservationRepository;
    private final ReservationAuditRepository auditRepository;

    public DepositExpiredScheduler(ReservationRepository reservationRepository,
                                   ReservationAuditRepository auditRepository) {
        this.reservationRepository = reservationRepository;
        this.auditRepository = auditRepository;
    }

    /**
     * Chạy mỗi 60 giây. Tự động hủy đơn VIP PENDING_DEPOSIT quá 10 phút.
     */
    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void cancelExpiredDeposits() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(DEPOSIT_TIMEOUT_MINUTES);

        List<Reservation> expired = reservationRepository.findExpiredPendingDeposit(cutoff);

        for (Reservation reservation : expired) {
            ReservationStatus oldStatus = reservation.getStatus();
            reservation.setStatus(ReservationStatus.CANCELLED);
            reservation.setCancellationReason(
                "Tự động hủy: Không đặt cọc trong vòng " + DEPOSIT_TIMEOUT_MINUTES + " phút");
            reservationRepository.save(reservation);

            ReservationAudit audit = new ReservationAudit(
                UUID.randomUUID().toString(),
                reservation.getId(),
                SystemUserInitializer.SYSTEM_USER_ID,
                oldStatus,
                ReservationStatus.CANCELLED,
                null,
                null,
                "Auto-cancel: Deposit timeout " + DEPOSIT_TIMEOUT_MINUTES + "m"
            );
            auditRepository.save(audit);

            logger.info("VIP deposit expired: reservation={}, guest={}",
                reservation.getId(), reservation.getGuestName());
        }
    }
}
