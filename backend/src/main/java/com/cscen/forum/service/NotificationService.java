package com.cscen.forum.service;

import com.cscen.forum.model.Question;
import com.cscen.forum.model.User;
import com.cscen.forum.repo.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Emails members who follow a topic when a new question is posted under it.
 * Runs asynchronously so it never slows down posting, and is a no-op when SMTP
 * is unconfigured (MailService handles that).
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final UserRepository users;
    private final MailService mail;

    public NotificationService(UserRepository users, MailService mail) {
        this.users = users;
        this.mail = mail;
    }

    @Async
    public void notifyNewQuestion(Question question, User author, String tagLabel) {
        if (!mail.isEnabled()) {
            return;
        }
        // No substring collisions between tag tokens, so a plain LIKE is safe.
        List<User> followers = users.findTopicFollowers(author.getId(), "%" + question.getTag() + "%");
        if (followers.isEmpty()) {
            return;
        }
        String url = mail.appUrl() + "/questions/" + question.getId();
        String html = """
                <div style="font-family:sans-serif;max-width:560px;margin:0 auto">
                  <p style="color:#64748b;font-size:12px;text-transform:uppercase;letter-spacing:.05em">
                    New in %s</p>
                  <h2 style="color:#312e81;margin-top:4px">%s</h2>
                  <p style="color:#334155">%s asked a new question in a topic you follow.</p>
                  <p><a href="%s" style="background:#4f46e5;color:#fff;padding:10px 18px;border-radius:8px;text-decoration:none;font-weight:600">Read &amp; answer →</a></p>
                  <p style="color:#94a3b8;font-size:12px">You're getting this because you follow %s on CSCE Nexus. Manage your topics in Settings.</p>
                </div>
                """.formatted(MailService.escape(tagLabel), MailService.escape(question.getTitle()),
                MailService.escape(author.getName() == null ? author.getUsername() : author.getName()),
                url, MailService.escape(tagLabel));

        int sent = mail.send(followers, "New " + tagLabel + " question on CSCE Nexus", html);
        log.info("Notified {} follower(s) of new {} question", sent, question.getTag());
    }
}
