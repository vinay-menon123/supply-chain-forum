package com.cscen.forum.service;

import com.cscen.forum.model.Question;
import com.cscen.forum.repo.QuestionRepository;
import com.cscen.forum.repo.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Weekly community digest: the top questions of the last 7 days, mailed every
 * Monday morning. A no-op (with a log line) until SMTP_HOST is configured.
 */
@Service
public class DigestService {

    private static final Logger log = LoggerFactory.getLogger(DigestService.class);

    private final UserRepository users;
    private final QuestionRepository questions;
    private final MailService mail;

    public DigestService(UserRepository users, QuestionRepository questions, MailService mail) {
        this.users = users;
        this.questions = questions;
        this.mail = mail;
    }

    @Scheduled(cron = "0 0 9 * * MON")
    public void weeklyDigest() {
        log.info("Weekly digest: {}", sendDigest());
    }

    /** Returns a human-readable result; also used by the admin test endpoint. */
    public String sendDigest() {
        if (!mail.isEnabled()) {
            return "skipped — SMTP is not configured (set SMTP_HOST)";
        }
        List<String> topIds = questions.topQuestionIdsSince(Instant.now().minus(7, ChronoUnit.DAYS));
        if (topIds.isEmpty()) {
            return "skipped — no new questions this week";
        }
        List<Question> top = questions.findAllById(topIds);

        StringBuilder items = new StringBuilder();
        for (Question q : top) {
            items.append("<li style=\"margin-bottom:10px\"><a href=\"").append(mail.appUrl())
                    .append("/questions/").append(q.getId())
                    .append("\" style=\"color:#4f46e5;font-weight:600;text-decoration:none\">")
                    .append(MailService.escape(q.getTitle())).append("</a></li>");
        }
        String html = """
                <div style="font-family:sans-serif;max-width:560px;margin:0 auto">
                  <h2 style="color:#312e81">CSCE Nexus — this week in supply chain</h2>
                  <p>The questions your community engaged with most this week:</p>
                  <ol>%s</ol>
                  <p><a href="%s" style="color:#4f46e5">Jump back into the discussion →</a></p>
                  <p style="color:#94a3b8;font-size:12px">One Mission. One Ecosystem. Limitless Impact.</p>
                </div>
                """.formatted(items, mail.appUrl());

        int sent = mail.send(users.findByIsBannedFalse(), "Your weekly CSCE Nexus digest", html);
        return "sent to " + sent + " members";
    }
}
