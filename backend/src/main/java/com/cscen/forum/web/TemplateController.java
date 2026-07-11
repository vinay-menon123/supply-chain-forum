package com.cscen.forum.web;

import com.cscen.forum.model.Template;
import com.cscen.forum.model.User;
import com.cscen.forum.model.Vote;
import com.cscen.forum.repo.TemplateRepository;
import com.cscen.forum.repo.UserRepository;
import com.cscen.forum.repo.VoteRepository;
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

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Member-contributed templates/resources library (downloadable files). */
@RestController
@RequestMapping("/api/templates")
public class TemplateController {

    private static final int PAGE_SIZE = 60;

    private final TemplateRepository templates;
    private final UserRepository users;
    private final VoteRepository votes;
    private final CurrentUser currentUser;
    private final ModerationService moderation;
    private final UploadStorage uploads;

    public TemplateController(TemplateRepository templates, UserRepository users, VoteRepository votes,
                              CurrentUser currentUser, ModerationService moderation,
                              UploadStorage uploads) {
        this.templates = templates;
        this.users = users;
        this.votes = votes;
        this.currentUser = currentUser;
        this.moderation = moderation;
        this.uploads = uploads;
    }

    @GetMapping
    public Map<String, Object> list(@RequestParam(defaultValue = "") String q,
                                    @RequestParam(defaultValue = "") String category,
                                    HttpServletRequest http) {
        String viewerId = currentUser.optionalUserId(http);
        String query = q.trim();
        String like = "%" + query.toLowerCase(Locale.ROOT) + "%";
        List<Template> results = templates.search(query, like, category, PageRequest.of(0, PAGE_SIZE));

        List<String> ids = results.stream().map(Template::getId).toList();
        Map<String, Long> voteCounts = toCountMap(votes.countByTemplateIds(ids));
        Set<String> viewerVoted = viewerId == null ? Set.of()
                : votes.findByUserIdAndTemplateIdIn(viewerId, ids).stream()
                        .map(Vote::getTemplateId).collect(Collectors.toSet());

        Map<String, User> authors = users.findAllById(
                        results.stream().map(Template::getAuthorId).distinct().toList())
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));

        List<Map<String, Object>> list = results.stream()
                .map(t -> Json.template(t, authors.get(t.getAuthorId()),
                        voteCounts.getOrDefault(t.getId(), 0L),
                        viewerVoted.contains(t.getId())))
                .toList();
        return Map.of("templates", list);
    }

    private static Map<String, Long> toCountMap(List<Object[]> rows) {
        Map<String, Long> map = new HashMap<>();
        for (Object[] row : rows) {
            map.put((String) row[0], ((Number) row[1]).longValue());
        }
        return map;
    }

    /** Toggle an upvote on a template. */
    @PostMapping("/{id}/vote")
    public Map<String, Object> vote(@PathVariable String id, HttpServletRequest http) {
        User user = currentUser.requireActiveUser(http);
        if (!templates.existsById(id)) throw ApiException.notFound("Template not found");
        Optional<Vote> existing = votes.findByUserIdAndTemplateId(user.getId(), id);
        if (existing.isPresent()) {
            votes.delete(existing.get());
        } else {
            votes.save(Vote.createTemplateVote(user.getId(), id));
        }
        return Map.of("voteCount", votes.countByTemplateId(id), "viewerHasVoted", existing.isEmpty());
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(
            @RequestParam String title,
            @RequestParam(defaultValue = "") String description,
            @RequestParam(defaultValue = "GENERAL") String category,
            @RequestParam("file") MultipartFile file,
            HttpServletRequest http) {
        User user = currentUser.requireActiveUser(http);

        String cleanTitle = title == null ? "" : title.trim();
        String cleanDesc = description == null ? "" : description.trim();
        if (cleanTitle.length() < 4) throw ApiException.badRequest("Title is required");
        if (cleanTitle.length() > 160) throw ApiException.badRequest("Title is too long");
        if (cleanDesc.length() > 4000) throw ApiException.badRequest("Description is too long");
        if (file == null || file.isEmpty()) throw ApiException.badRequest("A file is required");

        String violation = moderation.rejectIfProfane(user, "template", cleanTitle, cleanDesc);
        if (violation != null) throw ApiException.badRequest(violation);

        String cat = category == null || category.isBlank() ? "GENERAL" : category.trim();
        if (cat.length() > 40) cat = cat.substring(0, 40);

        String original = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename();
        String fileUrl = uploads.saveFile(file);

        Template template = Template.create();
        template.setTitle(cleanTitle);
        template.setDescription(cleanDesc);
        template.setCategory(cat);
        template.setFileUrl(fileUrl);
        template.setFileName(original);
        template.setFileType(extLabel(original));
        template.setAuthorId(user.getId());
        templates.save(template);

        return ResponseEntity.status(201).body(Json.template(template, user));
    }

    /** Records a download and returns the file to fetch. */
    @PostMapping("/{id}/download")
    public Map<String, Object> download(@PathVariable String id) {
        Template template = templates.findById(id)
                .orElseThrow(() -> ApiException.notFound("Template not found"));
        template.setDownloadCount(template.getDownloadCount() + 1);
        templates.save(template);
        return Map.of("fileUrl", template.getFileUrl(),
                "fileName", template.getFileName(),
                "downloadCount", template.getDownloadCount());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id, HttpServletRequest http) {
        User user = currentUser.requireActiveUser(http);
        Template template = templates.findById(id)
                .orElseThrow(() -> ApiException.notFound("Template not found"));
        if (!template.getAuthorId().equals(user.getId()) && !"ADMIN".equals(user.getRole())) {
            throw ApiException.forbidden("You can only delete your own templates");
        }
        templates.delete(template);
        return ResponseEntity.noContent().build();
    }

    /** "report.xlsx" → "XLSX" (a short badge label). */
    private static String extLabel(String name) {
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) return "FILE";
        return name.substring(dot + 1).toUpperCase(Locale.ROOT);
    }
}
