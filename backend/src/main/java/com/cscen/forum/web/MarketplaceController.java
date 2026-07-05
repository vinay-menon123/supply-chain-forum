package com.cscen.forum.web;

import com.cscen.forum.model.Listing;
import com.cscen.forum.model.MarketplaceLead;
import com.cscen.forum.model.User;
import com.cscen.forum.repo.ListingRepository;
import com.cscen.forum.repo.MarketplaceLeadRepository;
import com.cscen.forum.repo.UserRepository;
import com.cscen.forum.security.CurrentUser;
import com.cscen.forum.service.Json;
import com.cscen.forum.service.ModerationService;
import com.cscen.forum.service.NotificationService;
import com.cscen.forum.service.UploadStorage;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/listings")
public class MarketplaceController {

    private static final Set<String> KINDS = Set.of("OFFER", "SEEK");
    private static final Set<String> CATEGORIES = Set.of(
            "WAREHOUSE", "TRANSPORT", "EQUIPMENT", "SERVICES", "GENERAL");

    /** Free members may contact this many distinct suppliers per rolling 30 days. */
    private static final int FREE_MONTHLY_CONTACTS = 5;

    private final ListingRepository listings;
    private final UserRepository users;
    private final MarketplaceLeadRepository leads;
    private final ModerationService moderation;
    private final UploadStorage uploads;
    private final CurrentUser currentUser;
    private final NotificationService notifications;

    public MarketplaceController(ListingRepository listings, UserRepository users,
                                 MarketplaceLeadRepository leads,
                                 ModerationService moderation, UploadStorage uploads,
                                 CurrentUser currentUser, NotificationService notifications) {
        this.listings = listings;
        this.users = users;
        this.leads = leads;
        this.moderation = moderation;
        this.uploads = uploads;
        this.currentUser = currentUser;
        this.notifications = notifications;
    }

    @GetMapping
    public Map<String, Object> list(@RequestParam(defaultValue = "") String kind,
                                    @RequestParam(defaultValue = "") String category) {
        String k = KINDS.contains(kind) ? kind : "";
        String c = CATEGORIES.contains(category) ? category : "";
        List<Listing> results = listings.search(k, c, PageRequest.of(0, 100));

        Map<String, User> authors = users
                .findAllById(results.stream().map(Listing::getAuthorId).distinct().toList())
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));

        // Priority placement: PRO suppliers' listings rank first (stable sort keeps
        // newest-first order within each group).
        List<Listing> ranked = results.stream()
                .sorted(Comparator.comparingInt(l -> {
                    User a = authors.get(l.getAuthorId());
                    return a != null && a.isPro() ? 0 : 1;
                }))
                .toList();

        return Map.of("listings", ranked.stream()
                .map(l -> Json.listing(l, authors.get(l.getAuthorId()))).toList());
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(
            @RequestParam String kind,
            @RequestParam String category,
            @RequestParam String title,
            @RequestParam String description,
            @RequestParam(defaultValue = "") String location,
            @RequestParam(defaultValue = "") String price,
            @RequestParam(defaultValue = "") String size,
            @RequestParam(value = "image", required = false) MultipartFile image,
            HttpServletRequest http) {
        User user = currentUser.requireActiveUser(http);

        String k = kind == null ? "" : kind.trim().toUpperCase(Locale.ROOT);
        String c = category == null ? "" : category.trim().toUpperCase(Locale.ROOT);
        if (!KINDS.contains(k)) throw ApiException.badRequest("Choose whether you're offering or seeking");
        if (!CATEGORIES.contains(c)) throw ApiException.badRequest("Choose a valid category");

        String cleanTitle = title == null ? "" : title.trim();
        String cleanDesc = description == null ? "" : description.trim();
        if (cleanTitle.length() < 6) throw ApiException.badRequest("Title must be at least 6 characters");
        if (cleanTitle.length() > 160) throw ApiException.badRequest("Title is too long");
        if (cleanDesc.isEmpty()) throw ApiException.badRequest("Description is required");
        if (cleanDesc.length() > 4000) throw ApiException.badRequest("Description is too long");

        String violation = moderation.rejectIfProfane(user, "listing", cleanTitle, cleanDesc);
        if (violation != null) throw ApiException.badRequest(violation);

        Listing listing = Listing.create();
        listing.setKind(k);
        listing.setCategory(c);
        listing.setTitle(cleanTitle);
        listing.setDescription(cleanDesc);
        listing.setLocation(blankToNull(location, 120));
        listing.setPrice(blankToNull(price, 60));
        listing.setSize(blankToNull(size, 60));
        listing.setImageUrl(uploads.saveImage(image));
        listing.setAuthorId(user.getId());
        listings.save(listing);
        notifications.notifyNewListing(listing, user);

        return ResponseEntity.status(201).body(Json.listing(listing, user));
    }

    /**
     * Gated contact: records a lead and returns the seller's username so the
     * client can open the DM thread. Free members are capped at
     * {@link #FREE_MONTHLY_CONTACTS} distinct suppliers per 30 days; PRO is
     * unlimited. Re-contacting a listing you already reached is always free.
     */
    @PostMapping("/{id}/contact")
    public Map<String, Object> contact(@PathVariable String id, HttpServletRequest http) {
        User user = currentUser.requireActiveUser(http);
        Listing listing = listings.findById(id)
                .orElseThrow(() -> ApiException.notFound("Listing not found"));
        User seller = users.findById(listing.getAuthorId())
                .orElseThrow(() -> ApiException.notFound("Seller not found"));
        if (seller.getId().equals(user.getId())) {
            throw ApiException.badRequest("This is your own listing");
        }

        Optional<MarketplaceLead> existing = leads.findByListingIdAndBuyerId(id, user.getId());
        if (existing.isEmpty()) {
            if (!user.isPro()) {
                long used = leads.countByBuyerIdAndCreatedAtAfter(
                        user.getId(), Instant.now().minus(30, ChronoUnit.DAYS));
                if (used >= FREE_MONTHLY_CONTACTS) {
                    throw ApiException.forbidden("You've used your " + FREE_MONTHLY_CONTACTS
                            + " free supplier contacts this month. Upgrade to Pro for unlimited contacts.");
                }
            }
            leads.save(MarketplaceLead.create(id, seller.getId(), user.getId()));
        }

        long used = leads.countByBuyerIdAndCreatedAtAfter(
                user.getId(), Instant.now().minus(30, ChronoUnit.DAYS));
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("ok", true);
        res.put("sellerUsername", seller.getUsername());
        res.put("pro", user.isPro());
        res.put("used", used);
        res.put("limit", user.isPro() ? -1 : FREE_MONTHLY_CONTACTS);
        return res;
    }

    /** The requesting member's received leads (their "buyer interest" inbox). */
    @GetMapping("/leads")
    public Map<String, Object> myLeads(HttpServletRequest http) {
        User user = currentUser.requireActiveUser(http);
        List<MarketplaceLead> received = leads.findBySellerIdOrderByCreatedAtDesc(user.getId());

        // Free sellers see only the count; PRO reveals who + which listing.
        if (!user.isPro()) {
            return Map.of("count", received.size(), "pro", false, "leads", List.of());
        }

        Map<String, User> buyers = users
                .findAllById(received.stream().map(MarketplaceLead::getBuyerId).distinct().toList())
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));
        Map<String, Listing> listingMap = listings
                .findAllById(received.stream().map(MarketplaceLead::getListingId).distinct().toList())
                .stream().collect(Collectors.toMap(Listing::getId, Function.identity()));

        List<Map<String, Object>> items = received.stream().map(l -> {
            Map<String, Object> m = new LinkedHashMap<>();
            Listing lst = listingMap.get(l.getListingId());
            m.put("buyer", Json.author(buyers.get(l.getBuyerId())));
            m.put("listingId", l.getListingId());
            m.put("listingTitle", lst == null ? "(listing removed)" : lst.getTitle());
            m.put("createdAt", l.getCreatedAt());
            return m;
        }).toList();
        return Map.of("count", received.size(), "pro", true, "leads", items);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id, HttpServletRequest http) {
        User user = currentUser.requireActiveUser(http);
        Listing listing = listings.findById(id)
                .orElseThrow(() -> ApiException.notFound("Listing not found"));
        if (!listing.getAuthorId().equals(user.getId()) && !"ADMIN".equals(user.getRole())) {
            throw ApiException.forbidden("You can only remove your own listings");
        }
        listings.delete(listing);
        return ResponseEntity.noContent().build();
    }

    private static String blankToNull(String value, int max) {
        if (value == null) return null;
        String trimmed = value.trim();
        if (trimmed.isEmpty()) return null;
        return trimmed.length() > max ? trimmed.substring(0, max) : trimmed;
    }
}
