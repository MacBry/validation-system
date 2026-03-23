package com.mac.bry.validationsystem.security.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

@Slf4j
@Service
public class EmailService {

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Value("${spring.mail.properties.mail.from:no-reply@validationsystem.com}")
    private String fromEmail;

    public void sendPasswordResetEmail(String toEmail, String resetLink) {
        log.info("Sending password reset email to: {}", toEmail);
        // SECURITY: Reset link should never be logged for GMP compliance

        if (mailSender == null) {
            log.warn("JavaMailSender is not configured. Email will NOT be sent, but reset link is available in logs.");
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject("Resetowanie hasła - Validation System");

            String htmlContent = """
                    <html>
                    <body>
                        <h2>Resetowanie hasła w Validation System</h2>
                        <p>Otrzymaliśmy prośbę o zresetowanie hasła dla Twojego konta.</p>
                        <p>Aby ustawić nowe hasło, kliknij w poniższy link:</p>
                        <p><a href="%s">Zresetuj hasło</a></p>
                        <p>Link jest ważny przez 1 godzinę. Jeśli to nie Ty wysłałeś prośbę, zignoruj tę wiadomość.</p>
                    </body>
                    </html>
                    """.formatted(resetLink);

            helper.setText(htmlContent, true); // true indicates HTML

            mailSender.send(message);
            log.info("Email sent successfully to: {}", toEmail);

        } catch (MessagingException e) {
            log.error("Failed to send password reset email to {}", toEmail, e);
        } catch (Exception e) {
            log.error("An unexpected error occurred while sending email to {}", toEmail, e);
        }
    }
}
