package com.huongViet.reservation;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReservationEmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    private ReservationEmailService emailService;

    @BeforeEach
    void setUp() {
        emailService = new ReservationEmailService(mailSender, "test@huongviet.vn");
    }

    private Reservation createSampleReservation() {
        Reservation reservation = new Reservation(
            "24589d89-9830-4e3e-86fe-2b23ba4c767f",
            "cust-1",
            "Nguyễn Văn A",
            "0901234567",
            "nguyenvana@example.com",
            4,
            LocalDate.of(2026, 11, 20),
            LocalTime.of(18, 30),
            "Gần cửa sổ",
            "zone-1"
        );
        return reservation;
    }

    @Test
    @DisplayName("sendBookingConfirmation: sends email with formatted booking code HV-XXXXXXXX")
    void testSendBookingConfirmation() throws Exception {
        Reservation res = createSampleReservation();
        MimeMessage mimeMessage = new MimeMessage((Session) null);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        emailService.sendBookingConfirmation(res, "Tầng 1");

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender, times(1)).send(captor.capture());

        MimeMessage sent = captor.getValue();
        assertNotNull(sent);
        assertTrue(sent.getSubject().contains("HV-24589D89"));
        assertTrue(sent.getSubject().contains("Xác nhận yêu cầu đặt bàn"));
    }

    @Test
    @DisplayName("sendBookingApprovedEmail: sends email with confirmed status, table display, and HV-XXXXXXXX")
    void testSendBookingApprovedEmail() throws Exception {
        Reservation res = createSampleReservation();
        MimeMessage mimeMessage = new MimeMessage((Session) null);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        emailService.sendBookingApprovedEmail(res, "Bàn 01", "Tầng 1");

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender, times(1)).send(captor.capture());

        MimeMessage sent = captor.getValue();
        assertNotNull(sent);
        assertTrue(sent.getSubject().contains("HV-24589D89"));
        assertTrue(sent.getSubject().contains("Đặt bàn thành công"));
    }

    @Test
    @DisplayName("sendBookingCancelledEmail: sends email with cancelled status, reason, and HV-XXXXXXXX")
    void testSendBookingCancelledEmail() throws Exception {
        Reservation res = createSampleReservation();
        MimeMessage mimeMessage = new MimeMessage((Session) null);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        emailService.sendBookingCancelledEmail(res, "Khách bận đột xuất", "Tầng 1");

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender, times(1)).send(captor.capture());

        MimeMessage sent = captor.getValue();
        assertNotNull(sent);
        assertTrue(sent.getSubject().contains("HV-24589D89"));
        assertTrue(sent.getSubject().contains("Thông báo hủy đặt bàn"));
    }

    @Test
    @DisplayName("skip sending when guestEmail is null or blank")
    void testSkipWhenNoEmail() {
        Reservation res = createSampleReservation();
        res.setGuestEmail(null);

        emailService.sendBookingConfirmation(res, "Tầng 1");
        emailService.sendBookingApprovedEmail(res, "Bàn 01", "Tầng 1");
        emailService.sendBookingCancelledEmail(res, "Lý do", "Tầng 1");

        verify(mailSender, never()).send(any(MimeMessage.class));
    }
}
