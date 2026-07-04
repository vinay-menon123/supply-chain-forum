package com.cscen.forum.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "User")
public class User {

    @Id
    private String id;
    private String email;
    private String username;
    private String googleId;
    private String name;
    private String avatarUrl;
    private String role = "USER";
    private int flagCount = 0;
    private boolean isBanned = false;
    private Instant createdAt = Instant.now();
    private String memberType;
    private String phone;
    private String organization;
    private boolean openToMentor = false;
    private boolean seekingMentor = false;
    private String topics;         // comma-separated tag values the user follows
    private String linkedinUrl;
    private String headline;       // professional role/headline (used for verification)
    private String bio;
    private String verifyStatus = "PENDING"; // PENDING | APPROVED | REJECTED

    public static User create() {
        User user = new User();
        user.id = UUID.randomUUID().toString();
        return user;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getGoogleId() { return googleId; }
    public void setGoogleId(String googleId) { this.googleId = googleId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public int getFlagCount() { return flagCount; }
    public void setFlagCount(int flagCount) { this.flagCount = flagCount; }
    public boolean isBanned() { return isBanned; }
    public void setBanned(boolean banned) { isBanned = banned; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public String getMemberType() { return memberType; }
    public void setMemberType(String memberType) { this.memberType = memberType; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getOrganization() { return organization; }
    public void setOrganization(String organization) { this.organization = organization; }
    public boolean isOpenToMentor() { return openToMentor; }
    public void setOpenToMentor(boolean openToMentor) { this.openToMentor = openToMentor; }
    public boolean isSeekingMentor() { return seekingMentor; }
    public void setSeekingMentor(boolean seekingMentor) { this.seekingMentor = seekingMentor; }
    public String getTopics() { return topics; }
    public void setTopics(String topics) { this.topics = topics; }
    public String getLinkedinUrl() { return linkedinUrl; }
    public void setLinkedinUrl(String linkedinUrl) { this.linkedinUrl = linkedinUrl; }
    public String getHeadline() { return headline; }
    public void setHeadline(String headline) { this.headline = headline; }
    public String getBio() { return bio; }
    public void setBio(String bio) { this.bio = bio; }
    public String getVerifyStatus() { return verifyStatus; }
    public void setVerifyStatus(String verifyStatus) { this.verifyStatus = verifyStatus; }
}
