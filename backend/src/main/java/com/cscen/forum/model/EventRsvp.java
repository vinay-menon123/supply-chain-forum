package com.cscen.forum.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "EventRsvp")
public class EventRsvp {

    @Id
    private String id;
    private String eventId;
    private String userId;
    private Instant createdAt = Instant.now();

    public static EventRsvp create(String eventId, String userId) {
        EventRsvp rsvp = new EventRsvp();
        rsvp.id = UUID.randomUUID().toString();
        rsvp.eventId = eventId;
        rsvp.userId = userId;
        return rsvp;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
