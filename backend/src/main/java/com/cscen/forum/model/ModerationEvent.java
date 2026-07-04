package com.cscen.forum.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ModerationEvent")
public class ModerationEvent {

    @Id
    private String id;
    private String kind;
    private String content;
    private Instant createdAt = Instant.now();
    private String userId;

    public static ModerationEvent create(String userId, String kind, String content) {
        ModerationEvent event = new ModerationEvent();
        event.id = UUID.randomUUID().toString();
        event.userId = userId;
        event.kind = kind;
        event.content = content;
        return event;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
}
