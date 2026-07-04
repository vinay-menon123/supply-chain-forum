package com.cscen.forum.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "Question")
public class Question {

    @Id
    private String id;
    private String title;
    private String body;
    private String imageUrl;
    private int shareCount = 0;
    private Instant createdAt = Instant.now();
    private String authorId;
    private String tag = "GENERAL";
    private String acceptedCommentId;

    public static Question create() {
        Question question = new Question();
        question.id = UUID.randomUUID().toString();
        return question;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public int getShareCount() { return shareCount; }
    public void setShareCount(int shareCount) { this.shareCount = shareCount; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public String getAuthorId() { return authorId; }
    public void setAuthorId(String authorId) { this.authorId = authorId; }
    public String getTag() { return tag; }
    public void setTag(String tag) { this.tag = tag; }
    public String getAcceptedCommentId() { return acceptedCommentId; }
    public void setAcceptedCommentId(String acceptedCommentId) { this.acceptedCommentId = acceptedCommentId; }
}
