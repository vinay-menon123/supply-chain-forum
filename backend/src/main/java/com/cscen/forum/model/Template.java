package com.cscen.forum.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * A downloadable resource shared by a member — S&OP decks, RFQ sheets, planning
 * models, cheat-sheets, etc. The uploaded file lives in {@code UploadStorage}
 * ({@code fileUrl}); we track downloads to surface the most useful ones.
 */
@Entity
@Table(name = "Template")
public class Template {

    @Id
    private String id;
    private String title;
    private String description;
    private String category = "GENERAL";
    private String fileUrl;
    private String fileName;   // original name, shown to users
    private String fileType;   // content type / extension label
    private int downloadCount = 0;
    private Instant createdAt = Instant.now();
    private String authorId;

    public static Template create() {
        Template t = new Template();
        t.id = UUID.randomUUID().toString();
        return t;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getFileUrl() { return fileUrl; }
    public void setFileUrl(String fileUrl) { this.fileUrl = fileUrl; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public String getFileType() { return fileType; }
    public void setFileType(String fileType) { this.fileType = fileType; }
    public int getDownloadCount() { return downloadCount; }
    public void setDownloadCount(int downloadCount) { this.downloadCount = downloadCount; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public String getAuthorId() { return authorId; }
    public void setAuthorId(String authorId) { this.authorId = authorId; }
}
