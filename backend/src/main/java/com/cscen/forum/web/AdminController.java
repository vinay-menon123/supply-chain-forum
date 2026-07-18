package com.cscen.forum.web;

import com.cscen.forum.model.ModerationEvent;
import com.cscen.forum.model.User;
import com.cscen.forum.repo.ModerationEventRepository;
import com.cscen.forum.repo.UserRepository;
import com.cscen.forum.security.CurrentUser;
import com.cscen.forum.service.CommunityActivityService;
import com.cscen.forum.service.DigestService;
import com.cscen.forum.service.Json;
import com.cscen.forum.service.SeedService;
import com.cscen.forum.service.GeminiNewsSyncService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final UserRepository users;
    private final ModerationEventRepository events;
    private final CurrentUser currentUser;
    private final DigestService digest;
    private final SeedService seed;
    private final GeminiNewsSyncService newsSync;
    private final CommunityActivityService activity;

    public AdminController(UserRepository users, ModerationEventRepository events,
                           CurrentUser currentUser, DigestService digest, SeedService seed,
                           GeminiNewsSyncService newsSync, CommunityActivityService activity) {
        this.users = users;
        this.events = events;
        this.currentUser = currentUser;
        this.digest = digest;
        this.seed = seed;
        this.newsSync = newsSync;
        this.activity = activity;
    }

    /** Fire the weekly digest immediately (for testing SMTP configuration). */
    @PostMapping("/digest/test")
    public Map<String, Object> digestTest(HttpServletRequest http) {
        currentUser.requireAdmin(http);
        return Map.of("result", digest.sendDigest());
    }

    /** Trigger web search and synchronization for daily supply chain news. */
    @PostMapping("/news-sync")
    public Map<String, Object> newsSync(HttpServletRequest http) {
        currentUser.requireAdmin(http);
        return Map.of("result", newsSync.syncLatestNews());
    }

    /** Seed the starter supply-chain × AI discussions (idempotent). */
    @PostMapping("/seed")
    public Map<String, Object> seedContent(HttpServletRequest http) {
        currentUser.requireAdmin(http);
        return Map.of("result", seed.seed());
    }

    /**
     * Run one day of community activity immediately (a member asks the next pooled
     * question, others answer and upvote, the moderator verifies an older answer).
     * Same work the daily 09:15 IST scheduler does - useful for demoing without waiting.
     */
    @PostMapping("/activity/run")
    public Map<String, Object> runActivity(HttpServletRequest http) {
        currentUser.requireAdmin(http);
        return Map.of("result", activity.runOnce());
    }

    /** Members awaiting supply-chain verification review (PENDING or REJECTED). */
    @GetMapping("/pending")
    public Map<String, Object> pending(HttpServletRequest http) {
        currentUser.requireAdmin(http);
        List<Map<String, Object>> list = new ArrayList<>();
        for (User user : users.findByVerifyStatusOrderByCreatedAtDesc("PENDING")) {
            list.add(Json.publicUser(user));
        }
        for (User user : users.findByVerifyStatusOrderByCreatedAtDesc("REJECTED")) {
            list.add(Json.publicUser(user));
        }
        return Map.of("users", list);
    }

    public record VerifyRequest(String status) {
    }

    /** Admin override of a member's supply-chain verification status. */
    @PostMapping("/users/{id}/verify")
    public Map<String, Object> verify(@PathVariable String id,
                                      @RequestBody VerifyRequest request,
                                      HttpServletRequest http) {
        currentUser.requireAdmin(http);
        String status = request == null || request.status() == null ? "" : request.status().trim().toUpperCase();
        if (!status.equals("APPROVED") && !status.equals("REJECTED") && !status.equals("PENDING")) {
            throw ApiException.badRequest("Status must be APPROVED, REJECTED or PENDING");
        }
        User user = users.findById(id)
                .orElseThrow(() -> ApiException.notFound("User not found"));
        user.setVerifyStatus(status);
        users.save(user);
        return Map.of("id", user.getId(), "username", user.getUsername(), "verifyStatus", status);
    }

    /** Users with moderation history, worst offenders first. */
    @GetMapping("/flagged")
    public Map<String, Object> flagged(HttpServletRequest http) {
        currentUser.requireAdmin(http);

        List<Map<String, Object>> flagged = new ArrayList<>();
        for (User user : users.findFlagged()) {
            List<Map<String, Object>> recent = events
                    .findByUserIdOrderByCreatedAtDesc(user.getId(), PageRequest.of(0, 3))
                    .stream().map(AdminController::eventJson).toList();

            Map<String, Object> json = new LinkedHashMap<>();
            json.put("id", user.getId());
            json.put("username", user.getUsername());
            json.put("name", user.getName());
            json.put("avatarUrl", user.getAvatarUrl());
            json.put("flagCount", user.getFlagCount());
            json.put("isBanned", user.isBanned());
            json.put("createdAt", user.getCreatedAt());
            json.put("moderationEvents", recent);
            flagged.add(json);
        }
        return Map.of("users", flagged);
    }

    private static Map<String, Object> eventJson(ModerationEvent event) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("kind", event.getKind());
        json.put("content", event.getContent());
        json.put("createdAt", event.getCreatedAt());
        return json;
    }

    public record BanRequest(Boolean banned) {
    }

    /** Manually ban / unban. Unbanning resets the flag counter for a fresh start. */
    @PostMapping("/users/{id}/ban")
    public Map<String, Object> ban(@PathVariable String id,
                                   @RequestBody BanRequest request,
                                   HttpServletRequest http) {
        currentUser.requireAdmin(http);
        User user = users.findById(id)
                .orElseThrow(() -> ApiException.notFound("User not found"));

        boolean banned = request != null && Boolean.TRUE.equals(request.banned());
        user.setBanned(banned);
        if (!banned) {
            user.setFlagCount(0);
        }
        users.save(user);

        Map<String, Object> json = new LinkedHashMap<>();
        json.put("id", user.getId());
        json.put("username", user.getUsername());
        json.put("isBanned", user.isBanned());
        json.put("flagCount", user.getFlagCount());
        return json;
    }
}
