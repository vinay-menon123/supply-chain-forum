package com.cscen.forum.web;

import com.cscen.forum.model.Event;
import com.cscen.forum.model.EventRsvp;
import com.cscen.forum.model.User;
import com.cscen.forum.repo.EventRepository;
import com.cscen.forum.repo.EventRsvpRepository;
import com.cscen.forum.repo.UserRepository;
import com.cscen.forum.security.CurrentUser;
import com.cscen.forum.service.Json;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RestController
@RequestMapping("/api/events")
public class EventController {

    private final EventRepository events;
    private final EventRsvpRepository rsvps;
    private final UserRepository users;
    private final CurrentUser currentUser;

    public EventController(EventRepository events, EventRsvpRepository rsvps,
                           UserRepository users, CurrentUser currentUser) {
        this.events = events;
        this.rsvps = rsvps;
        this.users = users;
        this.currentUser = currentUser;
    }

    @GetMapping
    public Map<String, Object> list(HttpServletRequest http) {
        String viewerId = currentUser.optionalUserId(http);
        Instant now = Instant.now();
        List<Event> upcoming = events.findByStartsAtGreaterThanEqualOrderByStartsAtAsc(now);
        List<Event> past = events.findByStartsAtBeforeOrderByStartsAtDesc(now, PageRequest.of(0, 10));

        List<String> ids = Stream.concat(upcoming.stream(), past.stream())
                .map(Event::getId).toList();
        Map<String, Long> counts = new HashMap<>();
        if (!ids.isEmpty()) {
            for (Object[] row : rsvps.countByEventIds(ids)) {
                counts.put((String) row[0], ((Number) row[1]).longValue());
            }
        }
        Set<String> viewerRsvped = viewerId == null || ids.isEmpty()
                ? Set.of()
                : rsvps.findByUserIdAndEventIdIn(viewerId, ids).stream()
                        .map(EventRsvp::getEventId).collect(Collectors.toSet());
        Map<String, User> hosts = users.findAllById(Stream.concat(upcoming.stream(), past.stream())
                        .map(Event::getCreatedBy).distinct().toList())
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));

        return Map.of(
                "upcoming", upcoming.stream()
                        .map(e -> toJson(e, hosts, counts, viewerRsvped)).toList(),
                "past", past.stream()
                        .map(e -> toJson(e, hosts, counts, viewerRsvped)).toList());
    }

    private static Map<String, Object> toJson(Event event, Map<String, User> hosts,
                                              Map<String, Long> counts, Set<String> viewerRsvped) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("id", event.getId());
        json.put("title", event.getTitle());
        json.put("description", event.getDescription());
        json.put("link", event.getLink());
        json.put("startsAt", event.getStartsAt());
        json.put("createdAt", event.getCreatedAt());
        json.put("host", Json.author(hosts.get(event.getCreatedBy())));
        json.put("rsvpCount", counts.getOrDefault(event.getId(), 0L));
        json.put("viewerRsvped", viewerRsvped.contains(event.getId()));
        return json;
    }

    public record CreateEvent(String title, String description, String link, String startsAt) {
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@RequestBody CreateEvent request,
                                                      HttpServletRequest http) {
        User admin = currentUser.requireAdmin(http);

        String title = request.title() == null ? "" : request.title().trim();
        String description = request.description() == null ? "" : request.description().trim();
        String link = request.link() == null ? "" : request.link().trim();
        if (title.length() < 4) throw ApiException.badRequest("Title must be at least 4 characters");
        if (title.length() > 200) throw ApiException.badRequest("Title is too long");
        if (description.isEmpty()) throw ApiException.badRequest("Description is required");
        if (!link.isEmpty() && !link.startsWith("http://") && !link.startsWith("https://")) {
            throw ApiException.badRequest("Link must start with http:// or https://");
        }

        Instant startsAt;
        try {
            startsAt = Instant.parse(request.startsAt());
        } catch (DateTimeParseException | NullPointerException e) {
            throw ApiException.badRequest("Enter a valid date and time");
        }

        Event event = Event.create();
        event.setTitle(title);
        event.setDescription(description);
        event.setLink(link.isEmpty() ? null : link);
        event.setStartsAt(startsAt);
        event.setCreatedBy(admin.getId());
        events.save(event);

        Map<String, User> hosts = Map.of(admin.getId(), admin);
        return ResponseEntity.status(201).body(toJson(event, hosts, Map.of(), Set.of()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id, HttpServletRequest http) {
        currentUser.requireAdmin(http);
        Event event = events.findById(id)
                .orElseThrow(() -> ApiException.notFound("Event not found"));
        events.delete(event);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/rsvp")
    public Map<String, Object> rsvp(@PathVariable String id, HttpServletRequest http) {
        User user = currentUser.requireActiveUser(http);
        if (!events.existsById(id)) {
            throw ApiException.notFound("Event not found");
        }
        Optional<EventRsvp> existing = rsvps.findByUserIdAndEventId(user.getId(), id);
        if (existing.isPresent()) {
            rsvps.delete(existing.get());
        } else {
            rsvps.save(EventRsvp.create(id, user.getId()));
        }
        return Map.of("rsvpCount", rsvps.countByEventId(id), "viewerRsvped", existing.isEmpty());
    }
}
