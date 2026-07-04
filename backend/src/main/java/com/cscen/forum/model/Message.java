package com.cscen.forum.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "Message")
public class Message {

    @Id
    private String id;
    private String body;
    private Instant createdAt = Instant.now();
    private Instant readAt;
    private String fromId;
    private String toId;

    public static Message create(String fromId, String toId, String body) {
        Message message = new Message();
        message.id = UUID.randomUUID().toString();
        message.fromId = fromId;
        message.toId = toId;
        message.body = body;
        return message;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getReadAt() { return readAt; }
    public void setReadAt(Instant readAt) { this.readAt = readAt; }
    public String getFromId() { return fromId; }
    public void setFromId(String fromId) { this.fromId = fromId; }
    public String getToId() { return toId; }
    public void setToId(String toId) { this.toId = toId; }
}
