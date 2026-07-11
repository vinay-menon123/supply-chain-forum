package com.cscen.forum.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * A supply-chain job posting. Any active member can post an opening; the poster
 * (or an admin) can delete it. Members apply either via an external {@code applyUrl}
 * or by DMing the poster when no link is given.
 */
@Entity
@Table(name = "Job")
public class Job {

    @Id
    private String id;
    private String title;
    private String company;
    private String location;
    private String employmentType = "FULL_TIME"; // FULL_TIME | PART_TIME | CONTRACT | INTERNSHIP
    private String tag = "GENERAL";              // SCM domain tag (see QuestionService.TAGS)
    private String description;
    private String applyUrl;                     // external apply link (nullable → DM poster)
    private String salary;
    private Instant createdAt = Instant.now();
    private String authorId;

    public static Job create() {
        Job job = new Job();
        job.id = UUID.randomUUID().toString();
        return job;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getCompany() { return company; }
    public void setCompany(String company) { this.company = company; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public String getEmploymentType() { return employmentType; }
    public void setEmploymentType(String employmentType) { this.employmentType = employmentType; }
    public String getTag() { return tag; }
    public void setTag(String tag) { this.tag = tag; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getApplyUrl() { return applyUrl; }
    public void setApplyUrl(String applyUrl) { this.applyUrl = applyUrl; }
    public String getSalary() { return salary; }
    public void setSalary(String salary) { this.salary = salary; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public String getAuthorId() { return authorId; }
    public void setAuthorId(String authorId) { this.authorId = authorId; }
}
