package com.cscen.forum.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * A buyer expressing interest in a listing ("contacting the seller"). This is
 * the monetized unit of the marketplace: free members get a limited number per
 * month; a PRO subscription lifts the cap and reveals lead details to sellers.
 */
@Entity
@Table(name = "MarketplaceLead")
public class MarketplaceLead {

    @Id
    private String id;
    private String listingId;
    private String sellerId;   // listing owner who receives the lead
    private String buyerId;    // member who initiated contact
    private Instant createdAt = Instant.now();

    public static MarketplaceLead create(String listingId, String sellerId, String buyerId) {
        MarketplaceLead lead = new MarketplaceLead();
        lead.id = UUID.randomUUID().toString();
        lead.listingId = listingId;
        lead.sellerId = sellerId;
        lead.buyerId = buyerId;
        return lead;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getListingId() { return listingId; }
    public void setListingId(String listingId) { this.listingId = listingId; }
    public String getSellerId() { return sellerId; }
    public void setSellerId(String sellerId) { this.sellerId = sellerId; }
    public String getBuyerId() { return buyerId; }
    public void setBuyerId(String buyerId) { this.buyerId = buyerId; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
