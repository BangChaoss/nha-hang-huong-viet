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
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private final JavaMailSender mailSender;
    private final String fromEmail;

    public ReservationEmailService(JavaMailSender mailSender,
                                   @Value("${app.mail.from:onboarding@resend.dev}") String fromEmail) {
        this.mailSender = mailSender;
        this.fromEmail = fromEmail;
    }

    /**
     * Sends booking request confirmation email to customer/guest (Pending state).
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

            String dateFormatted = reservation.getReservationDate().format(DATE_FORMATTER);
            String timeFormatted = reservation.getReservationTime().format(TIME_FORMATTER);
            String bookingCode = reservation.getBookingCode();
            String subject = String.format("[Hương Việt] Xác nhận yêu cầu đặt bàn — %s lúc %s (%s)", dateFormatted, timeFormatted, bookingCode);
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
                    <p style="font-size: 13px; color: #64748b;">Mã đặt bàn: <code style="font-weight: bold; color: #b91c1c;">%s</code></p>
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
                    bookingCode
                );

            helper.setText(html, true);
            mailSender.send(message);
            logger.info("Reservation confirmation email sent successfully to {} for reservation {} ({})",
                reservation.getGuestEmail(), reservation.getId(), bookingCode);
        } catch (Exception e) {
            logger.error("Failed to send reservation confirmation email for reservation {}: {}", reservation.getId(), e.getMessage());
        }
    }

    /**
     * Sends booking approved/confirmed email to customer when staff confirms and assigns table(s).
     */
    public void sendBookingApprovedEmail(Reservation reservation, String tableDisplay, String zoneName) {
        if (reservation.getGuestEmail() == null || reservation.getGuestEmail().isBlank()) {
            logger.debug("No guest email provided for reservation {}. Skipping approved email dispatch.", reservation.getId());
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(reservation.getGuestEmail());

            String dateFormatted = reservation.getReservationDate().format(DATE_FORMATTER);
            String timeFormatted = reservation.getReservationTime().format(TIME_FORMATTER);
            String bookingCode = reservation.getBookingCode();
            String subject = String.format("[Hương Việt] Đặt bàn thành công — %s lúc %s (%s)", dateFormatted, timeFormatted, bookingCode);
            helper.setSubject(subject);

            String specialRequestsDisplay = (reservation.getSpecialRequests() != null && !reservation.getSpecialRequests().isBlank())
                ? reservation.getSpecialRequests()
                : "Không có";
            String zoneDisplay = (zoneName != null && !zoneName.isBlank()) ? zoneName : "Theo sắp xếp";
            String tableFormatted = (tableDisplay != null && !tableDisplay.isBlank()) ? tableDisplay : "Theo sắp xếp của nhân viên";

            String html = """
                <div style="font-family: Arial, sans-serif; line-height: 1.6; color: #333; max-width: 600px; margin: 0 auto; border: 1px solid #e2e8f0; border-radius: 8px; padding: 24px;">
                    <h2 style="color: #16a34a; margin-top: 0;">Nhà hàng Hương Việt</h2>
                    <p>Kính chào <strong>%s</strong>,</p>
                    <p>Yêu cầu đặt bàn của bạn đã được nhân viên nhà hàng <strong style="color: #16a34a;">xác nhận thành công</strong>! Chúng tôi đã chuẩn bị sẵn sàng để đón tiếp quý khách:</p>
                    <div style="background-color: #f0fdf4; border-left: 4px solid #16a34a; padding: 12px 16px; margin: 16px 0; border-radius: 4px;">
                        <p style="margin: 6px 0;">📅 <strong>Ngày:</strong> %s</p>
                        <p style="margin: 6px 0;">🕐 <strong>Giờ:</strong> %s</p>
                        <p style="margin: 6px 0;">👥 <strong>Số khách:</strong> %d người</p>
                        <p style="margin: 6px 0;">📍 <strong>Khu vực:</strong> %s</p>
                        <p style="margin: 6px 0;">🪑 <strong>Vị trí bàn:</strong> %s</p>
                        <p style="margin: 6px 0;">📝 <strong>Yêu cầu đặc biệt:</strong> %s</p>
                    </div>
                    <p><strong>Trạng thái:</strong> <span style="color: #16a34a; font-weight: bold;">ĐÃ XÁC NHẬN</span></p>
                    <p style="font-size: 14px; color: #1e293b; background-color: #f8fafc; padding: 10px; border-radius: 6px; border: 1px dashed #cbd5e1;">
                        Mã đặt bàn của quý khách: <strong style="font-family: monospace; font-size: 16px; color: #b91c1c;">%s</strong>
                    </p>
                    <p style="font-size: 13px; color: #64748b; margin-top: 12px;">
                        <em>* Lưu ý: Nhà hàng hỗ trợ giữ bàn trong vòng 30 phút kể từ giờ hẹn. Khi đến nơi, quý khách vui lòng đọc số điện thoại (<strong>%s</strong>) hoặc mã đặt bàn để nhân viên hướng dẫn nhận bàn.</em>
                    </p>
                    <hr style="border: none; border-top: 1px solid #e2e8f0; margin: 20px 0;" />
                    <p style="margin-bottom: 0;">Rất hân hạnh được phục vụ quý khách!<br/><strong>Đội ngũ Nhà hàng Hương Việt</strong></p>
                </div>
                """.formatted(
                    reservation.getGuestName(),
                    dateFormatted,
                    timeFormatted,
                    reservation.getPartySize(),
                    zoneDisplay,
                    tableFormatted,
                    specialRequestsDisplay,
                    bookingCode,
                    reservation.getGuestPhone()
                );

            helper.setText(html, true);
            mailSender.send(message);
            logger.info("Reservation approval email sent successfully to {} for reservation {} ({})",
                reservation.getGuestEmail(), reservation.getId(), bookingCode);
        } catch (Exception e) {
            logger.error("Failed to send reservation approval email for reservation {}: {}", reservation.getId(), e.getMessage());
        }
    }

    /**
     * Sends booking cancellation email to customer/guest with reason (from customer or staff).
     */
    public void sendBookingCancelledEmail(Reservation reservation, String reason, String zoneName) {
        if (reservation.getGuestEmail() == null || reservation.getGuestEmail().isBlank()) {
            logger.debug("No guest email provided for reservation {}. Skipping cancelled email dispatch.", reservation.getId());
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(reservation.getGuestEmail());

            String dateFormatted = reservation.getReservationDate().format(DATE_FORMATTER);
            String timeFormatted = reservation.getReservationTime().format(TIME_FORMATTER);
            String bookingCode = reservation.getBookingCode();
            String subject = String.format("[Hương Việt] Thông báo hủy đặt bàn — %s lúc %s (%s)", dateFormatted, timeFormatted, bookingCode);
            helper.setSubject(subject);

            String reasonDisplay = (reason != null && !reason.isBlank()) ? reason : "Không có lý do cụ thể";
            String zoneDisplay = (zoneName != null && !zoneName.isBlank()) ? zoneName : "Theo sắp xếp";

            String html = """
                <div style="font-family: Arial, sans-serif; line-height: 1.6; color: #333; max-width: 600px; margin: 0 auto; border: 1px solid #e2e8f0; border-radius: 8px; padding: 24px;">
                    <h2 style="color: #ef4444; margin-top: 0;">Nhà hàng Hương Việt</h2>
                    <p>Kính chào <strong>%s</strong>,</p>
                    <p>Yêu cầu đặt bàn của bạn đã được cập nhật trạng thái hủy trên hệ thống với thông tin chi tiết dưới đây:</p>
                    <div style="background-color: #fef2f2; border-left: 4px solid #ef4444; padding: 12px 16px; margin: 16px 0; border-radius: 4px;">
                        <p style="margin: 6px 0;">📅 <strong>Ngày:</strong> %s</p>
                        <p style="margin: 6px 0;">🕐 <strong>Giờ:</strong> %s</p>
                        <p style="margin: 6px 0;">👥 <strong>Số khách:</strong> %d người</p>
                        <p style="margin: 6px 0;">📍 <strong>Khu vực:</strong> %s</p>
                        <p style="margin: 6px 0;">❌ <strong>Trạng thái:</strong> <span style="color: #ef4444; font-weight: bold;">ĐÃ HỦY</span></p>
                        <p style="margin: 6px 0;">📝 <strong>Lý do hủy:</strong> <span style="color: #b91c1c; font-weight: 600;">%s</span></p>
                    </div>
                    <p style="font-size: 13px; color: #64748b;">Mã đặt bàn: <code style="font-weight: bold; color: #b91c1c;">%s</code></p>
                    <p style="font-size: 13px; color: #475569; margin-top: 12px;">
                        Nếu quý khách có nhu cầu đặt bàn vào thời gian khác, xin vui lòng truy cập website hoặc liên hệ hotline nhà hàng để được phục vụ chu đáo nhất.
                    </p>
                    <hr style="border: none; border-top: 1px solid #e2e8f0; margin: 20px 0;" />
                    <p style="margin-bottom: 0;">Trân trọng cảm ơn,<br/><strong>Đội ngũ Nhà hàng Hương Việt</strong></p>
                </div>
                """.formatted(
                    reservation.getGuestName(),
                    dateFormatted,
                    timeFormatted,
                    reservation.getPartySize(),
                    zoneDisplay,
                    reasonDisplay,
                    bookingCode
                );

            helper.setText(html, true);
            mailSender.send(message);
            logger.info("Reservation cancellation email sent successfully to {} for reservation {} ({})",
                reservation.getGuestEmail(), reservation.getId(), bookingCode);
        } catch (Exception e) {
            logger.error("Failed to send reservation cancellation email for reservation {}: {}", reservation.getId(), e.getMessage());
        }
    }
}
