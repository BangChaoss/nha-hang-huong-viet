package com.huongViet.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Service responsible for sending email notifications via Resend (SMTP).
 * Uses Spring's JavaMailSender configured via application.properties.
 */
@Service
public class EmailService {
    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final String fromEmail;

    public EmailService(JavaMailSender mailSender,
                        @Value("${app.mail.from:onboarding@resend.dev}") String fromEmail) {
        this.mailSender = mailSender;
        this.fromEmail = fromEmail;
    }

    /**
     * Sends a password reset email containing the 6-digit OTP.
     *
     * @param toEmail recipient email address
     * @param otp the 6-digit OTP code to include in the email
     */
    public void sendPasswordResetOtp(String toEmail, String otp) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(toEmail);
            message.setSubject("Mã OTP đặt lại mật khẩu - Nhà hàng Hương Việt");
            message.setText("Xin chào,\n\n" +
                    "Bạn đã gửi yêu cầu đặt lại mật khẩu cho tài khoản tại Nhà hàng Hương Việt.\n\n" +
                    "Mã OTP xác thực của bạn là:\n\n" +
                    "      " + otp + "\n\n" +
                    "(Mã có hiệu lực trong 30 phút. Vui lòng không chia sẻ mã này cho bất kỳ ai).\n\n" +
                    "Nếu bạn không thực hiện yêu cầu này, vui lòng bỏ qua email.\n\n" +
                    "Trân trọng,\nĐội ngũ Nhà hàng Hương Việt");
            mailSender.send(message);
            logger.info("Password reset OTP [{}] sent successfully to {} via Resend", otp, toEmail);
        } catch (Exception e) {
            logger.error("Failed to send password reset OTP via Resend to {}: {}", toEmail, e.getMessage());
        }
    }

    public void sendPasswordResetEmail(String toEmail, String otp) {
        sendPasswordResetOtp(toEmail, otp);
    }
}
