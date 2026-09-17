package com.theotech.common.mail;

import com.theotech.common.exception.MailFailedException;
import com.theotech.settings.dto.MailSettings;
import com.theotech.settings.service.SettingsService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Properties;

/**
 * SMTP sender configured from the settings table on every send (the account is edited in Settings and
 * must apply immediately; the table is tiny). Port 465 means implicit SSL, anything else STARTTLS.
 */
@Component
public class SmtpEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailSender.class);

    private final SettingsService settings;

    public SmtpEmailSender(SettingsService settings) {
        this.settings = settings;
    }

    @Override
    public void send(String to, String subject, String html) {
        MailSettings m = settings.mail();
        if (!m.configured()) {
            throw new MailFailedException("MAIL_NOT_CONFIGURED", "SMTP server or account is not configured");
        }
        String[] recipients = Arrays.stream((to == null ? "" : to).split("[,;]"))
                .map(String::trim).filter(s -> !s.isEmpty()).toArray(String[]::new);
        if (recipients.length == 0) {
            throw new MailFailedException("MAIL_FAILED", "No recipient");
        }
        JavaMailSenderImpl sender = sender(m);
        try {
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
            helper.setFrom(m.fromOrUsername());
            helper.setTo(recipients);
            helper.setSubject(subject);
            helper.setText(html, true);
            sender.send(message);
            log.info("E-mail '{}' sent to {}", subject, String.join(", ", recipients));
        } catch (MessagingException | MailException e) {
            log.warn("E-mail '{}' to {} failed: {}", subject, to, e.getMessage());
            throw new MailFailedException("MAIL_FAILED", "The mail server refused the message: " + e.getMessage());
        }
    }

    static JavaMailSenderImpl sender(MailSettings m) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(m.host());
        sender.setPort(m.port());
        sender.setUsername(m.username());
        sender.setPassword(m.password());
        sender.setDefaultEncoding("UTF-8");
        Properties p = sender.getJavaMailProperties();
        p.put("mail.transport.protocol", "smtp");
        p.put("mail.smtp.auth", "true");
        p.put("mail.smtp.connectiontimeout", "15000");
        p.put("mail.smtp.timeout", "20000");
        p.put("mail.smtp.writetimeout", "20000");
        if (m.port() == 465) {
            p.put("mail.smtp.ssl.enable", "true");
        } else {
            p.put("mail.smtp.starttls.enable", "true");
            p.put("mail.smtp.starttls.required", "true");
        }
        return sender;
    }
}
