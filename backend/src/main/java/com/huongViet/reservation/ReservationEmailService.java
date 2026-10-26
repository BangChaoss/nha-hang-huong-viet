package com.huongViet.reservation;

import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;

@Service
public class ReservationEmailService {

    private static final Logger logger = LoggerFactory.getLogger(ReservationEmailService.class);

    private final JavaMailSender mailSender;
    private final String fromEmail;

    public ReservationEmailService(JavaMailSender mailSender,
                                   @Value("${app.mail.from:onboarding@resend.dev}") String fromEmail) {
        this.mailSender = mailSender;
        this.fromEmail = fromEmail;
    }

    /**
     * Sends booking confirmation email to customer/guest.
     * Non-blocking for the reservation flow: catches any mail errors so booking succeeds.
     */
    public void sendBookingConfirmation(Reservation reservation, String zoneName) {
        if (reservation.getGuestEmail() == null || reservation.getGuestEmail().isBlank()) {
            logger.debug("No guest email provided for reservation {}. Skipping email dispatch.", reservation.getId());
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(reservation.getGuestEmail());

            String dateFormatted = reservation.getReservationDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
            String timeFormatted = reservation.getReservationTime().format(DateTimeFormatter.ofPattern("HH:mm"));
            String subject = String.format("[Hương Việt] Xác nhận yêu cầu đặt bàn — %s lúc %s", dateFormatted, timeFormatted);
            helper.setSubject(subject);

            String specialRequestsDisplay = (reservation.getSpecialRequests() != null && !reservation.getSpecialRequests().isBlank())
                ? reservation.getSpecialRequests()
                : "Không có";
            String zoneDisplay = (zoneName != null && !zoneName.isBlank()) ? zoneName : "Theo sắp xếp";

            String html = """
                <div style="font-family: Arial, sans-serif; line-height: 1.6; color: #333; max-width: 600px; margin: 0 auto; border: 1px solid #e2e8f0; border-radius: 8px; padding: 24px;">
                    <h2 style="color: #b91c1c; margin-top: 0;">Nhà hàng Hương Việt</h2>
                    <p>Kính chào <strong>%s</strong>,</p>
                    <p>Chúng tôi đã nhận được yêu cầu đặt bàn của bạn:</p>
                    <div style="background-color: #f8fafc; border-left: 4px solid #b91c1c; padding: 12px 16px; margin: 16px 0; border-radius: 4px;">
                        <p style="margin: 6px 0;">📅 <strong>Ngày:</strong> %s</p>
                        <p style="margin: 6px 0;">🕐 <strong>Giờ:</strong> %s</p>
                        <p style="margin: 6px 0;">👥 <strong>Số khách:</strong> %d người</p>
                        <p style="margin: 6px 0;">📍 <strong>Khu vực:</strong> %s</p>
                        <p style="margin: 6px 0;">📝 <strong>Yêu cầu đặc biệt:</strong> %s</p>
                    </div>
                    <p><strong>Trạng thái:</strong> <span style="color: #d97706; font-weight: bold;">Đang chờ xác nhận</span></p>
                    <p>Nhân viên nhà hàng sẽ liên hệ bạn qua số điện thoại <strong>%s</strong> để xác nhận và giữ bàn.</p>
                    <p style="font-size: 13px; color: #64748b;">Mã đặt bàn: <code>%s</code></p>
                    <hr style="border: none; border-top: 1px solid #e2e8f0; margin: 20px 0;" />
                    <p style="margin-bottom: 0;">Trân trọng,<br/><strong>Đội ngũ Nhà hàng Hương Việt</strong></p>
                </div>
                """.formatted(
                    reservation.getGuestName(),
                    dateFormatted,
                    timeFormatted,
                    reservation.getPartySize(),
                    zoneDisplay,
                    specialRequestsDisplay,
                    reservation.getGuestPhone(),
                    reservation.getId()
                );

            helper.setText(html, true);
            mailSender.send(message);
            logger.info("Reservation confirmation email sent successfully to {} for reservation {}", reservation.getGuestEmail(), reservation.getId());
        } catch (Exception e) {
            logger.error("Failed to send reservation confirmation email for reservation {}: {}", reservation.getId(), e.getMessage());
        }
    }
}
