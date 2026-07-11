package com.cscen.forum.web;

import com.cscen.forum.model.Notification;
import com.cscen.forum.model.User;
import com.cscen.forum.repo.NotificationRepository;
import com.cscen.forum.repo.UserRepository;
import com.cscen.forum.security.CurrentUser;
import com.cscen.forum.service.Json;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** In-app activity feed for the navbar bell. */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private static final int LIMIT = 30;

    private final NotificationRepository notifications;
    private final UserRepository users;
    private final CurrentUser currentUser;

    public NotificationController(NotificationRepository notifications, UserRepository users,
                                  CurrentUser currentUser) {
        this.notifications = notifications;
        this.users = users;
        this.currentUser = currentUser;
    }

    @GetMapping
    public Map<String, Object> list(HttpServletRequest http) {
        User me = currentUser.requireActiveUser(http);
        List<Notification> recent =
                notifications.findByUserIdOrderByCreatedAtDesc(me.getId(), PageRequest.of(0, LIMIT));

        Map<String, User> actors = users.findAllById(recent.stream()
                        .map(Notification::getActorId).filter(java.util.Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));

        List<Map<String, Object>> list = recent.stream()
                .map(n -> Json.notification(n, actors.get(n.getActorId())))
                .toList();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("notifications", list);
        response.put("unread", notifications.countByUserIdAndReadAtIsNull(me.getId()));
        return response;
    }

    /** Lightweight badge poll. */
    @GetMapping("/unread")
    public Map<String, Object> unread(HttpServletRequest http) {
        User me = currentUser.requireActiveUser(http);
        return Map.of("count", notifications.countByUserIdAndReadAtIsNull(me.getId()));
    }

    @PostMapping("/read")
    public Map<String, Object> markRead(HttpServletRequest http) {
        User me = currentUser.requireActiveUser(http);
        notifications.markAllRead(me.getId(), Instant.now());
        return Map.of("ok", true);
    }
}
