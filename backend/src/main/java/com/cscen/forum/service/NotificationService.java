package com.cscen.forum.service;

import com.cscen.forum.model.Event;
import com.cscen.forum.model.Listing;
import com.cscen.forum.model.Question;
import com.cscen.forum.model.User;
import com.cscen.forum.repo.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
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
        // Topic followers + users who want ALL new questions (deduplicated)
        List<User> topicFollowers = users.findTopicFollowers(author.getId(), "%" + question.getTag() + "%");
        List<User> allQuestionsUsers = users.findAllQuestionNotifyUsers(author.getId());

        java.util.LinkedHashMap<String, User> merged = new java.util.LinkedHashMap<>();
        topicFollowers.forEach(u -> merged.put(u.getId(), u));
        allQuestionsUsers.forEach(u -> merged.put(u.getId(), u));

        List<User> recipients = new java.util.ArrayList<>(merged.values());
        if (recipients.isEmpty()) {
            return;
        }
        String url = mail.appUrl() + "/questions/" + question.getId();
        String html = """
                <div style="font-family:sans-serif;max-width:560px;margin:0 auto">
                  <p style="color:#64748b;font-size:12px;text-transform:uppercase;letter-spacing:.05em">
                    New in %s</p>
                  <h2 style="color:#312e81;margin-top:4px">%s</h2>
                  <p style="color:#334155">%s asked a new question in a topic you follow.</p>
                  <p><a href="%s" style="background:#5e6ad2;color:#fff;padding:10px 18px;border-radius:8px;text-decoration:none;font-weight:600">Read &amp; answer →</a></p>
                  <p style="color:#94a3b8;font-size:12px">You're getting this because you follow %s on CSCE Nexus. Manage your topics in Settings.</p>
                </div>
                """.formatted(MailService.escape(tagLabel), MailService.escape(question.getTitle()),
                MailService.escape(author.getName() == null ? author.getUsername() : author.getName()),
                url, MailService.escape(tagLabel));

        int sent = mail.send(recipients, "New " + tagLabel + " question on CSCE Nexus", html);
        log.info("Notified {} member(s) of new {} question", sent, question.getTag());
    }

    @Async
    public void notifyNewMessage(User sender, User recipient, String bodySnippet) {
        if (!mail.isEnabled()) {
            return;
        }
        String url = mail.appUrl() + "/messages/" + sender.getUsername();
        String html = """
                <div style="font-family:sans-serif;max-width:560px;margin:0 auto">
                  <p style="color:#64748b;font-size:12px;text-transform:uppercase;letter-spacing:.05em">
                    New Direct Message</p>
                  <h2 style="color:#312e81;margin-top:4px">Message from %s</h2>
                  <p style="color:#334155">"%s"</p>
                  <p><a href="%s" style="background:#5e6ad2;color:#fff;padding:10px 18px;border-radius:8px;text-decoration:none;font-weight:600">Reply →</a></p>
                  <p style="color:#94a3b8;font-size:12px">You're getting this because you're a member of CSCE Nexus. You can disable email notifications in Settings.</p>
                </div>
                """.formatted(MailService.escape(sender.getName() == null ? sender.getUsername() : sender.getName()),
                MailService.escape(bodySnippet.length() > 60 ? bodySnippet.substring(0, 57) + "..." : bodySnippet),
                url);

        mail.sendOne(recipient.getEmail(), "New message from " + (sender.getName() == null ? sender.getUsername() : sender.getName()) + " on CSCE Nexus", html);
        log.info("Notified user {} of new DM from {}", recipient.getUsername(), sender.getUsername());
    }

    @Async
    public void notifyNewListing(Listing listing, User author) {
        if (!mail.isEnabled()) {
            return;
        }
        List<User> recipients = users.findByIsBannedFalse();
        if (recipients.isEmpty()) {
            return;
        }
        String url = mail.appUrl() + "/marketplace";
        String html = """
                <div style="font-family:sans-serif;max-width:560px;margin:0 auto">
                  <p style="color:#64748b;font-size:12px;text-transform:uppercase;letter-spacing:.05em">
                    New SCM Marketplace Listing</p>
                  <h2 style="color:#312e81;margin-top:4px">%s</h2>
                  <p style="color:#334155">%s posted a new listing in category: <strong>%s</strong></p>
                  <p style="color:#334155;background:#f8fafc;padding:12px;border-radius:6px;border-left:4px solid #5e6ad2">
                    %s
                  </p>
                  <p><a href="%s" style="background:#5e6ad2;color:#fff;padding:10px 18px;border-radius:8px;text-decoration:none;font-weight:600">View Listing →</a></p>
                  <p style="color:#94a3b8;font-size:12px">You're getting this because you're a member of CSCE Nexus SCM Marketplace.</p>
                </div>
                """.formatted(MailService.escape(listing.getTitle()),
                MailService.escape(author.getName() == null ? author.getUsername() : author.getName()),
                MailService.escape(listing.getCategory()),
                MailService.escape(listing.getDescription().length() > 120 ? listing.getDescription().substring(0, 117) + "..." : listing.getDescription()),
                url);

        int sent = mail.send(recipients, "New SCM Marketplace Listing: " + listing.getTitle(), html);
        log.info("Notified {} member(s) of new SCM Marketplace listing", sent);
    }

    private static final DateTimeFormatter ICS_STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter HUMAN_WHEN =
            DateTimeFormatter.ofPattern("EEE, d MMM yyyy 'at' h:mm a 'UTC'").withZone(ZoneOffset.UTC);

    /** On a new RSVP, email the member a confirmation with a .ics calendar invite attached. */
    @Async
    public void notifyEventRsvp(User user, Event event) {
        if (!mail.isEnabled() || user.getEmail() == null) {
            return;
        }
        String url = event.getLink() != null && !event.getLink().isBlank()
                ? event.getLink()
                : mail.appUrl() + "/events";
        String html = """
                <div style="font-family:sans-serif;max-width:560px;margin:0 auto">
                  <p style="color:#64748b;font-size:12px;text-transform:uppercase;letter-spacing:.05em">You're going 🎉</p>
                  <h2 style="color:#312e81;margin-top:4px">%s</h2>
                  <p style="color:#334155">%s</p>
                  <p style="color:#334155"><strong>When:</strong> %s</p>
                  <p><a href="%s" style="background:#5e6ad2;color:#fff;padding:10px 18px;border-radius:8px;text-decoration:none;font-weight:600">Event details →</a></p>
                  <p style="color:#94a3b8;font-size:12px">The attached invite (invite.ics) adds this to your calendar. You RSVP'd on CSCE Nexus — open the event to cancel.</p>
                </div>
                """.formatted(MailService.escape(event.getTitle()),
                MailService.escape(event.getDescription() == null ? "" : event.getDescription()),
                HUMAN_WHEN.format(event.getStartsAt()), url);

        mail.sendWithCalendar(user.getEmail(),
                "You're confirmed: " + event.getTitle(), html, buildIcs(event), "invite.ics");
        log.info("Sent event invite for {} to {}", event.getId(), user.getUsername());
    }

    /** Minimal RFC 5545 VEVENT; events have no end time, so we assume a 1-hour slot. */
    private static String buildIcs(Event event) {
        String start = ICS_STAMP.format(event.getStartsAt());
        String end = ICS_STAMP.format(event.getStartsAt().plus(1, ChronoUnit.HOURS));
        String stamp = ICS_STAMP.format(Instant.now());
        String desc = event.getDescription() == null ? "" : event.getDescription();
        if (event.getLink() != null && !event.getLink().isBlank()) {
            desc = desc + "\n\n" + event.getLink();
        }
        String location = event.getLink() != null && !event.getLink().isBlank() ? event.getLink() : "Online";
        return String.join("\r\n",
                "BEGIN:VCALENDAR",
                "VERSION:2.0",
                "PRODID:-//CSCE Nexus//Events//EN",
                "CALSCALE:GREGORIAN",
                "METHOD:PUBLISH",
                "BEGIN:VEVENT",
                "UID:" + event.getId() + "@cscen",
                "DTSTAMP:" + stamp,
                "DTSTART:" + start,
                "DTEND:" + end,
                "SUMMARY:" + icsEscape(event.getTitle()),
                "DESCRIPTION:" + icsEscape(desc),
                "LOCATION:" + icsEscape(location),
                "END:VEVENT",
                "END:VCALENDAR");
    }

    private static String icsEscape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace("\n", "\\n");
    }
}
