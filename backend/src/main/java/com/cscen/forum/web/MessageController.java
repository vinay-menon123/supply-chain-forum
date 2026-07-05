package com.cscen.forum.web;

import com.cscen.forum.model.Message;
import com.cscen.forum.model.User;
import com.cscen.forum.repo.MessageRepository;
import com.cscen.forum.repo.UserRepository;
import com.cscen.forum.security.CurrentUser;
import com.cscen.forum.service.ModerationService;
import com.cscen.forum.service.NotificationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/messages")
public class MessageController {

    // Anti-disintermediation: on the free plan we hide phone numbers and emails
    // shared in chat so the first real connection happens on-platform. PRO members
    // can exchange contact details freely.
    private static final Pattern EMAIL_RE =
            Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern PHONE_RE =
            Pattern.compile("(?:\\+?\\d[\\d\\s().-]{6,}\\d)");

    private final MessageRepository messages;
    private final UserRepository users;
    private final CurrentUser currentUser;
    private final ModerationService moderation;
    private final NotificationService notifications;

    public MessageController(MessageRepository messages, UserRepository users,
                             CurrentUser currentUser, ModerationService moderation,
                             NotificationService notifications) {
        this.messages = messages;
        this.users = users;
        this.currentUser = currentUser;
        this.moderation = moderation;
        this.notifications = notifications;
    }

    private static Map<String, Object> partnerJson(User user) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("id", user.getId());
        json.put("username", user.getUsername());
        json.put("name", user.getName());
        json.put("avatarUrl", user.getAvatarUrl());
        return json;
    }

    /** Total unread — polled by the navbar badge. */
    @GetMapping("/unread")
    public Map<String, Object> unread(HttpServletRequest http) {
        User me = currentUser.requireActiveUser(http);
        return Map.of("count", messages.countByToIdAndReadAtIsNull(me.getId()));
    }

    /** Conversation list: one entry per partner with last message + unread count. */
    @GetMapping
    public Map<String, Object> conversations(HttpServletRequest http) {
        User me = currentUser.requireActiveUser(http);
        List<Message> feed = messages.findConversationsFeed(me.getId(), PageRequest.of(0, 300));

        Map<String, User> partners = users.findAllById(feed.stream()
                        .map(m -> m.getFromId().equals(me.getId()) ? m.getToId() : m.getFromId())
                        .distinct().toList())
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));

        Map<String, Map<String, Object>> conversations = new LinkedHashMap<>();
        for (Message message : feed) {
            boolean fromMe = message.getFromId().equals(me.getId());
            String partnerId = fromMe ? message.getToId() : message.getFromId();
            User partner = partners.get(partnerId);
            if (partner == null) {
                continue;
            }
            Map<String, Object> convo = conversations.computeIfAbsent(partnerId, id -> {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("partner", partnerJson(partner));
                entry.put("lastMessage", Map.of(
                        "body", message.getBody(),
                        "createdAt", message.getCreatedAt(),
                        "fromMe", fromMe));
                entry.put("unread", 0L);
                return entry;
            });
            if (!fromMe && message.getReadAt() == null) {
                convo.put("unread", ((Number) convo.get("unread")).longValue() + 1);
            }
        }
        return Map.of("conversations", new ArrayList<>(conversations.values()));
    }

    /** Full thread with one user; opening it marks their messages as read. */
    @GetMapping("/{username}")
    public Map<String, Object> thread(@PathVariable String username, HttpServletRequest http) {
        User me = currentUser.requireActiveUser(http);
        User partner = users.findByUsername(username)
                .orElseThrow(() -> ApiException.notFound("User not found"));

        List<Message> thread = messages.findThread(me.getId(), partner.getId(), PageRequest.of(0, 200));
        messages.markRead(partner.getId(), me.getId(), Instant.now());

        List<Map<String, Object>> messageList = thread.stream().map(m -> {
            Map<String, Object> json = new LinkedHashMap<>();
            json.put("id", m.getId());
            json.put("body", m.getBody());
            json.put("createdAt", m.getCreatedAt());
            json.put("fromMe", m.getFromId().equals(me.getId()));
            return json;
        }).toList();

        return Map.of("partner", partnerJson(partner), "messages", messageList);
    }

    public record SendMessage(String body) {
    }

    @PostMapping("/{username}")
    public ResponseEntity<Map<String, Object>> send(@PathVariable String username,
                                                    @RequestBody SendMessage request,
                                                    HttpServletRequest http) {
        User me = currentUser.requireActiveUser(http);
        String body = request == null || request.body() == null ? "" : request.body().trim();
        if (body.isEmpty()) throw ApiException.badRequest("Message cannot be empty");
        if (body.length() > 2000) throw ApiException.badRequest("Message is too long");

        User partner = users.findByUsername(username)
                .orElseThrow(() -> ApiException.notFound("User not found"));
        if (partner.getId().equals(me.getId())) {
            throw ApiException.badRequest("You cannot message yourself");
        }

        String violation = moderation.rejectIfProfane(me, "message", body);
        if (violation != null) throw ApiException.badRequest(violation);

        // Free members can't share raw contact details in chat (keeps the deal on-platform).
        String stored = me.isPro() ? body : redactContacts(body);

        Message message = messages.save(Message.create(me.getId(), partner.getId(), stored));
        notifications.notifyNewMessage(me, partner, stored);
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("id", message.getId());
        json.put("body", message.getBody());
        json.put("createdAt", message.getCreatedAt());
        json.put("fromMe", true);
        return ResponseEntity.status(201).body(json);
    }

    /** Masks emails and phone-like number runs so free members can't share raw contacts. */
    private static String redactContacts(String body) {
        String out = EMAIL_RE.matcher(body).replaceAll("[contact hidden — upgrade to Pro]");
        out = PHONE_RE.matcher(out).replaceAll("[contact hidden — upgrade to Pro]");
        return out;
    }
}
