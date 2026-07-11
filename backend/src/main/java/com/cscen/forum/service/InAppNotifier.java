package com.cscen.forum.service;

import com.cscen.forum.model.Notification;
import com.cscen.forum.model.User;
import com.cscen.forum.repo.NotificationRepository;
import com.cscen.forum.repo.UserRepository;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Creates the in-app (navbar bell) notifications: answers, replies, accepted
 * answers, and @mentions. Kept separate from {@code NotificationService} (email)
 * so the two channels evolve independently. Writes are cheap synchronous inserts
 * and always skip notifying yourself.
 */
@Service
public class InAppNotifier {

    /** @username tokens in post/comment bodies (usernames are [a-z0-9_], 3–30 chars). */
    private static final Pattern MENTION = Pattern.compile("@([A-Za-z0-9_]{3,30})");

    private final NotificationRepository notifications;
    private final UserRepository users;

    public InAppNotifier(NotificationRepository notifications, UserRepository users) {
        this.notifications = notifications;
        this.users = users;
    }

    private void save(String recipientId, String actorId, String type,
                      String questionId, String commentId, String text) {
        if (recipientId == null || recipientId.equals(actorId)) {
            return; // never notify yourself
        }
        Notification n = Notification.create();
        n.setUserId(recipientId);
        n.setActorId(actorId);
        n.setType(type);
        n.setQuestionId(questionId);
        n.setCommentId(commentId);
        n.setText(text);
        notifications.save(n);
    }

    private static String name(User u) {
        return u.getName() == null || u.getName().isBlank() ? "@" + u.getUsername() : u.getName();
    }

    /** Someone answered a question → notify its author. */
    public void answered(String questionAuthorId, User actor, String questionId) {
        save(questionAuthorId, actor.getId(), "ANSWER", questionId, null, name(actor) + " answered your question");
    }

    /** Someone replied under an answer → notify the answer's author. */
    public void replied(String answerAuthorId, User actor, String questionId, String commentId) {
        save(answerAuthorId, actor.getId(), "REPLY", questionId, commentId, name(actor) + " replied to your answer");
    }

    /** An answer was accepted → notify the answer's author. */
    public void accepted(String answerAuthorId, User actor, String questionId, String commentId) {
        save(answerAuthorId, actor.getId(), "ACCEPT", questionId, commentId, "Your answer was accepted ✓");
    }

    /**
     * Notify every @mentioned member found in {@code text}. Deduplicates, skips the
     * author, and skips anyone in {@code alreadyNotified} (so an answer's author isn't
     * pinged twice when they're also @mentioned).
     */
    public void mentions(String text, User actor, String questionId, String commentId, Set<String> alreadyNotified) {
        if (text == null || text.isBlank()) {
            return;
        }
        Matcher m = MENTION.matcher(text);
        Set<String> seenNames = new HashSet<>();
        while (m.find()) {
            String uname = m.group(1);
            if (!seenNames.add(uname.toLowerCase())) {
                continue;
            }
            users.findByUsername(uname).ifPresent(u -> {
                if (alreadyNotified != null && alreadyNotified.contains(u.getId())) {
                    return;
                }
                save(u.getId(), actor.getId(), "MENTION", questionId, commentId, name(actor) + " mentioned you");
            });
        }
    }
}
