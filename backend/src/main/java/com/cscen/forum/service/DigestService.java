package com.cscen.forum.service;

import com.cscen.forum.model.Question;
import com.cscen.forum.model.User;
import com.cscen.forum.repo.QuestionRepository;
import com.cscen.forum.repo.UserRepository;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Properties;

/**
 * Weekly community digest: the top questions of the last 7 days, mailed every
 * Monday morning. A no-op (with a log line) until SMTP_HOST is configured.
 */
@Service
public class DigestService {

    private static final Logger log = LoggerFactory.getLogger(DigestService.class);

    private final UserRepository users;
    private final QuestionRepository questions;
    private final String appUrl;
    private final String from;
    private final JavaMailSenderImpl sender;

    public DigestService(UserRepository users, QuestionRepository questions, Environment env) {
        this.users = users;
        this.questions = questions;
        this.appUrl = env.getProperty("APP_URL", "http://localhost:3000").replaceAll("/$", "");
        this.from = env.getProperty("SMTP_FROM", "noreply@cscen.local");

        String host = env.getProperty("SMTP_HOST", "");
        if (host.isBlank()) {
            this.sender = null;
            log.info("Weekly digest disabled (SMTP_HOST not set)");
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
            log.info("Weekly digest enabled via {}", host);
        }
    }

    @Scheduled(cron = "0 0 9 * * MON")
    public void weeklyDigest() {
        log.info("Weekly digest: {}", sendDigest());
    }

    /** Returns a human-readable result; also used by the admin test endpoint. */
    public String sendDigest() {
        if (sender == null) {
            return "skipped — SMTP is not configured (set SMTP_HOST)";
        }
        List<String> topIds = questions.topQuestionIdsSince(Instant.now().minus(7, ChronoUnit.DAYS));
        if (topIds.isEmpty()) {
            return "skipped — no new questions this week";
        }
        List<Question> top = questions.findAllById(topIds);

        StringBuilder items = new StringBuilder();
        for (Question q : top) {
            items.append("<li style=\"margin-bottom:10px\"><a href=\"").append(appUrl)
                    .append("/questions/").append(q.getId())
                    .append("\" style=\"color:#4f46e5;font-weight:600;text-decoration:none\">")
                    .append(escape(q.getTitle())).append("</a></li>");
        }
        String html = """
                <div style="font-family:sans-serif;max-width:560px;margin:0 auto">
                  <h2 style="color:#312e81">CSCE Nexus — this week in supply chain</h2>
                  <p>The questions your community engaged with most this week:</p>
                  <ol>%s</ol>
                  <p><a href="%s" style="color:#4f46e5">Jump back into the discussion →</a></p>
                  <p style="color:#94a3b8;font-size:12px">One Mission. One Ecosystem. Limitless Impact.</p>
                </div>
                """.formatted(items, appUrl);

        int sent = 0;
        for (User user : users.findByIsBannedFalse()) {
            try {
                MimeMessage message = sender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
                helper.setTo(user.getEmail());
                helper.setFrom(from);
                helper.setSubject("Your weekly CSCE Nexus digest");
                helper.setText(html, true);
                sender.send(message);
                sent++;
            } catch (Exception e) {
                log.warn("Digest to {} failed: {}", user.getEmail(), e.getMessage());
            }
        }
        return "sent to " + sent + " members";
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
