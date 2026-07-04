package com.cscen.forum.web;

import com.cscen.forum.model.ModerationEvent;
import com.cscen.forum.model.User;
import com.cscen.forum.repo.ModerationEventRepository;
import com.cscen.forum.repo.UserRepository;
import com.cscen.forum.security.CurrentUser;
import com.cscen.forum.service.DigestService;
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

    public AdminController(UserRepository users, ModerationEventRepository events,
                           CurrentUser currentUser, DigestService digest) {
        this.users = users;
        this.events = events;
        this.currentUser = currentUser;
        this.digest = digest;
    }

    /** Fire the weekly digest immediately (for testing SMTP configuration). */
    @PostMapping("/digest/test")
    public Map<String, Object> digestTest(HttpServletRequest http) {
        currentUser.requireAdmin(http);
        return Map.of("result", digest.sendDigest());
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
