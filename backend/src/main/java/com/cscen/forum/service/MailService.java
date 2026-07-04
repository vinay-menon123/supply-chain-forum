package com.cscen.forum.service;

import com.cscen.forum.model.User;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Properties;

/**
 * Thin wrapper over JavaMail. A no-op (isEnabled() == false) until SMTP_HOST is
 * configured, so every mail feature degrades gracefully when email is off.
 */
@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final JavaMailSenderImpl sender;
    private final String from;
    private final String appUrl;

    public MailService(Environment env) {
        this.appUrl = env.getProperty("APP_URL", "http://localhost:3000").replaceAll("/$", "");
        this.from = env.getProperty("SMTP_FROM", "noreply@cscen.local");

        String host = env.getProperty("SMTP_HOST", "");
        if (host.isBlank()) {
            this.sender = null;
            log.info("Email disabled (SMTP_HOST not set)");
        } else {
            JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
            mailSender.setHost(host);
            mailSender.setPort(env.getProperty("SMTP_PORT", Integer.class, 587));
            mailSender.setUsername(env.getProperty("SMTP_USER", ""));
            mailSender.setPassword(env.getProperty("SMTP_PASS", ""));
            Properties props = mailSender.getJavaMailProperties();
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.connectiontimeout", "10000");
            props.put("mail.smtp.timeout", "10000");
            this.sender = mailSender;
            log.info("Email enabled via {}", host);
        }
    }

    public boolean isEnabled() {
        return sender != null;
    }

    public String appUrl() {
        return appUrl;
    }

    /** Sends the same HTML to every recipient; returns the count delivered. */
    public int send(Collection<User> recipients, String subject, String html) {
        if (sender == null) {
            return 0;
        }
        int sent = 0;
        for (User user : recipients) {
            if (sendOne(user.getEmail(), subject, html)) {
                sent++;
            }
        }
        return sent;
    }

    public boolean sendOne(String to, String subject, String html) {
        if (sender == null || to == null || to.isBlank()) {
            return false;
        }
        try {
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
            helper.setTo(to);
            helper.setFrom(from);
            helper.setSubject(subject);
            helper.setText(html, true);
            sender.send(message);
            return true;
        } catch (Exception e) {
            log.warn("Email to {} failed: {}", to, e.getMessage());
            return false;
        }
    }

    public static String escape(String text) {
        return text == null ? "" : text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
