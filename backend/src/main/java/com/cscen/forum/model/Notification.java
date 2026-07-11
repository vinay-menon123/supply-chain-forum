package com.cscen.forum.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * In-app activity notification (the navbar bell). One row per event the recipient
 * should see: someone answered their question, replied to their answer, accepted
 * their answer, or @mentioned them. Purely additive — email notifications are
 * unchanged and handled separately by {@code NotificationService}.
 */
@Entity
@Table(name = "Notification")
public class Notification {

    @Id
    private String id;
    private String userId;      // recipient
    private String actorId;     // who triggered it (nullable)
    private String type;        // ANSWER | REPLY | ACCEPT | MENTION
    private String questionId;  // deep-link target (nullable)
    private String commentId;
    private String text;
    private Instant readAt;
    private Instant createdAt = Instant.now();

    public static Notification create() {
        Notification n = new Notification();
        n.id = UUID.randomUUID().toString();
        return n;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getActorId() { return actorId; }
    public void setActorId(String actorId) { this.actorId = actorId; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getQuestionId() { return questionId; }
    public void setQuestionId(String questionId) { this.questionId = questionId; }
    public String getCommentId() { return commentId; }
    public void setCommentId(String commentId) { this.commentId = commentId; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public Instant getReadAt() { return readAt; }
    public void setReadAt(Instant readAt) { this.readAt = readAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
