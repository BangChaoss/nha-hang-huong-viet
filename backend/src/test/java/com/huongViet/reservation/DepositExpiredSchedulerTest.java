package com.huongViet.reservation;

import com.huongViet.auth.SystemUserInitializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DepositExpiredSchedulerTest {

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private ReservationAuditRepository auditRepository;

    private DepositExpiredScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new DepositExpiredScheduler(reservationRepository, auditRepository);
    }

    @Test
    @DisplayName("cancelExpiredDeposits - Tự động hủy đơn PENDING_DEPOSIT quá 10 phút và ghi audit")
    void cancelExpiredDeposits_autoCancelsExpiredReservations() {
        Reservation expiredRes = new Reservation(
            "res-vip-expired", null, "Khách Hết Hạn", "0900111222", "expired@huongviet.com",
            4, LocalDate.now().plusDays(1), LocalTime.of(19, 0), null, "zone-vip"
        );
        expiredRes.setStatus(ReservationStatus.PENDING_DEPOSIT);
        expiredRes.setDepositAmount(new BigDecimal("250000"));
        expiredRes.setCreatedAt(LocalDateTime.now().minusMinutes(15));

        when(reservationRepository.findExpiredPendingDeposit(any(LocalDateTime.class)))
            .thenReturn(List.of(expiredRes));
        when(reservationRepository.save(any(Reservation.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        scheduler.cancelExpiredDeposits();

        assertEquals(ReservationStatus.CANCELLED, expiredRes.getStatus());
        assertTrue(expiredRes.getCancellationReason().contains("Không thanh toán phí thu & cọc phòng VIP"));
        verify(reservationRepository, times(1)).save(expiredRes);

        ArgumentCaptor<ReservationAudit> auditCaptor = ArgumentCaptor.forClass(ReservationAudit.class);
        verify(auditRepository, times(1)).save(auditCaptor.capture());
        ReservationAudit savedAudit = auditCaptor.getValue();
        assertEquals(SystemUserInitializer.SYSTEM_USER_ID, savedAudit.getChangedBy());
        assertEquals(ReservationStatus.PENDING_DEPOSIT, savedAudit.getOldStatus());
        assertEquals(ReservationStatus.CANCELLED, savedAudit.getNewStatus());
        assertTrue(savedAudit.getReason().contains("Auto-cancel"));
    }
}
