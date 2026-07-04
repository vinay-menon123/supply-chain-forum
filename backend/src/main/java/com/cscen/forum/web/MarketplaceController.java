package com.cscen.forum.web;

import com.cscen.forum.model.Listing;
import com.cscen.forum.model.User;
import com.cscen.forum.repo.ListingRepository;
import com.cscen.forum.repo.UserRepository;
import com.cscen.forum.security.CurrentUser;
import com.cscen.forum.service.Json;
import com.cscen.forum.service.ModerationService;
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

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/listings")
public class MarketplaceController {

    private static final Set<String> KINDS = Set.of("OFFER", "SEEK");
    private static final Set<String> CATEGORIES = Set.of(
            "WAREHOUSE", "TRANSPORT", "EQUIPMENT", "SERVICES", "GENERAL");

    private final ListingRepository listings;
    private final UserRepository users;
    private final ModerationService moderation;
    private final UploadStorage uploads;
    private final CurrentUser currentUser;

    public MarketplaceController(ListingRepository listings, UserRepository users,
                                 ModerationService moderation, UploadStorage uploads,
                                 CurrentUser currentUser) {
        this.listings = listings;
        this.users = users;
        this.moderation = moderation;
        this.uploads = uploads;
        this.currentUser = currentUser;
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

        return Map.of("listings", results.stream()
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

        return ResponseEntity.status(201).body(Json.listing(listing, user));
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
