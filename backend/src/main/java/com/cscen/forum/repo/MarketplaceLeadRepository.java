package com.cscen.forum.repo;

import com.cscen.forum.model.MarketplaceLead;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface MarketplaceLeadRepository extends JpaRepository<MarketplaceLead, String> {

    /** A buyer's contacts in a rolling window — used to enforce the free-tier cap. */
    long countByBuyerIdAndCreatedAtAfter(String buyerId, Instant since);

    /** Whether this buyer has already contacted this listing (re-contact is free). */
    Optional<MarketplaceLead> findByListingIdAndBuyerId(String listingId, String buyerId);

    /** Leads a seller has received, newest first (the seller's lead inbox). */
    List<MarketplaceLead> findBySellerIdOrderByCreatedAtDesc(String sellerId);

    long countBySellerId(String sellerId);
}
