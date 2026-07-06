package com.cscen.forum.service;

import com.cscen.forum.model.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Email delivery wrapper with two optional backends:
 * <ul>
 *   <li><b>Resend HTTP API</b> (set {@code RESEND_API_KEY}) — <b>preferred</b>. Sends over
 *       HTTPS/443, so it works on PaaS hosts like Railway that block outbound SMTP ports
 *       (25/465/587). The {@code MAIL_FROM} address must be from a Resend-verified domain
 *       (or {@code onboarding@resend.dev} while testing), else Resend returns 403.</li>
 *   <li><b>JavaMail SMTP</b> (set {@code SMTP_HOST}) — fallback for hosts that allow SMTP.</li>
 * </ul>
 * If neither is configured, {@link #isEnabled()} is false and every mail feature is a no-op.
 * All send paths swallow errors and return {@code false} — email never blocks a request.
 */
@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);
    private static final String RESEND_ENDPOINT = "https://api.resend.com/emails";

    private final JavaMailSenderImpl sender;   // SMTP backend, or null when SMTP_HOST unset
    private final String resendKey;            // Resend API key, "" when unset
    private final HttpClient http;             // for Resend, null when Resend unused
    private final ObjectMapper mapper = new ObjectMapper();
    private final String from;
    private final String appUrl;

    public MailService(Environment env) {
        this.appUrl = env.getProperty("APP_URL", "http://localhost:3000").replaceAll("/$", "");
        this.resendKey = env.getProperty("RESEND_API_KEY", "").trim();

        // MAIL_FROM is the canonical sender; fall back to SMTP_FROM for older configs.
        String mailFrom = env.getProperty("MAIL_FROM", "").trim();
        this.from = mailFrom.isBlank() ? env.getProperty("SMTP_FROM", "noreply@cscen.local") : mailFrom;

        String host = env.getProperty("SMTP_HOST", "");
        if (host.isBlank()) {
            this.sender = null;
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
        }

        if (!resendKey.isBlank()) {
            this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
            log.info("Email enabled via Resend HTTP API (from {})", from);
        } else {
            this.http = null;
            log.info(sender != null ? "Email enabled via SMTP {} (from {})" : "Email disabled (set RESEND_API_KEY or SMTP_HOST)",
                    host, from);
        }
    }

    /** True when at least one delivery backend (Resend or SMTP) is configured. */
    public boolean isEnabled() {
        return !resendKey.isBlank() || sender != null;
    }

    public String appUrl() {
        return appUrl;
    }

    /** Sends the same HTML to every recipient; returns the count delivered. */
    public int send(Collection<User> recipients, String subject, String html) {
        if (!isEnabled()) {
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
        return dispatch(to, subject, html, null, null);
    }

    /** Sends an HTML email with a calendar (.ics) invite attached. */
    public boolean sendWithCalendar(String to, String subject, String html, String ics, String filename) {
        return dispatch(to, subject, html, ics, filename);
    }

    /** Routes to Resend (preferred) then SMTP. Never throws; returns false on any failure. */
    private boolean dispatch(String to, String subject, String html, String ics, String icsFilename) {
        if (to == null || to.isBlank()) {
            return false;
        }
        if (!resendKey.isBlank()) {
            return sendViaResend(to, subject, html, ics, icsFilename);
        }
        if (sender != null) {
            return sendViaSmtp(to, subject, html, ics, icsFilename);
        }
        return false;
    }

    private boolean sendViaResend(String to, String subject, String html, String ics, String icsFilename) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("from", from);
            payload.put("to", List.of(to));
            payload.put("subject", subject);
            payload.put("html", html);
            if (ics != null) {
                String encoded = Base64.getEncoder().encodeToString(ics.getBytes(StandardCharsets.UTF_8));
                payload.put("attachments", List.of(Map.of(
                        "filename", icsFilename == null ? "invite.ics" : icsFilename,
                        "content", encoded)));
            }
            HttpRequest request = HttpRequest.newBuilder(URI.create(RESEND_ENDPOINT))
                    .header("Authorization", "Bearer " + resendKey)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(15))
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return true;
            }
            log.warn("Resend email to {} failed: HTTP {} - {}", to, response.statusCode(), response.body());
            return false;
        } catch (Exception e) {
            log.warn("Resend email to {} failed: {}", to, e.getMessage());
            return false;
        }
    }

    private boolean sendViaSmtp(String to, String subject, String html, String ics, String icsFilename) {
        try {
            MimeMessage message = sender.createMimeMessage();
            boolean multipart = ics != null;
            MimeMessageHelper helper = new MimeMessageHelper(message, multipart, "UTF-8");
            helper.setTo(to);
            helper.setFrom(from);
            helper.setSubject(subject);
            helper.setText(html, true);
            if (multipart) {
                helper.addAttachment(icsFilename == null ? "invite.ics" : icsFilename,
                        new ByteArrayResource(ics.getBytes(StandardCharsets.UTF_8)), "text/calendar");
            }
            sender.send(message);
            return true;
        } catch (Exception e) {
            log.warn("SMTP email to {} failed: {}", to, e.getMessage());
            return false;
        }
    }

    public static String escape(String text) {
        return text == null ? "" : text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
