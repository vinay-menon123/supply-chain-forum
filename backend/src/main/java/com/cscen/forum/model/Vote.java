package com.cscen.forum.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "Vote")
public class Vote {

    @Id
    private String id;
    private Instant createdAt = Instant.now();
    private String userId;
    private String questionId;

    public static Vote create(String userId, String questionId) {
        Vote vote = new Vote();
        vote.id = UUID.randomUUID().toString();
        vote.userId = userId;
        vote.questionId = questionId;
        return vote;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getQuestionId() { return questionId; }
    public void setQuestionId(String questionId) { this.questionId = questionId; }
}
