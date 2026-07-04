package com.cscen.forum.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Marketplace listing — warehouse space, transport capacity, equipment or
 * services, either offered or sought. Contact happens via direct messages to
 * the author, so no contact details are stored on the row.
 */
@Entity
@Table(name = "Listing")
public class Listing {

    @Id
    private String id;
    private String kind;        // OFFER | SEEK
    private String category;    // WAREHOUSE | TRANSPORT | EQUIPMENT | SERVICES | GENERAL
    private String title;
    private String description;
    private String location;
    private String price;       // free text, e.g. "₹28/sq ft/mo" or "Negotiable"
    private String size;        // free text, e.g. "12,000 sq ft"
    private String imageUrl;
    private Instant createdAt = Instant.now();
    private String authorId;

    public static Listing create() {
        Listing listing = new Listing();
        listing.id = UUID.randomUUID().toString();
        return listing;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public String getPrice() { return price; }
    public void setPrice(String price) { this.price = price; }
    public String getSize() { return size; }
    public void setSize(String size) { this.size = size; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public String getAuthorId() { return authorId; }
    public void setAuthorId(String authorId) { this.authorId = authorId; }
}
