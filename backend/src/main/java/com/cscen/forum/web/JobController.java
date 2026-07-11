package com.cscen.forum.web;

import com.cscen.forum.model.Job;
import com.cscen.forum.model.User;
import com.cscen.forum.repo.JobRepository;
import com.cscen.forum.repo.UserRepository;
import com.cscen.forum.security.CurrentUser;
import com.cscen.forum.service.Json;
import com.cscen.forum.service.ModerationService;
import com.cscen.forum.service.NotificationService;
import com.cscen.forum.service.QuestionService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Supply-chain jobs board. Any active member can post; poster/admin can delete. */
@RestController
@RequestMapping("/api/jobs")
public class JobController {

    private static final int PAGE_SIZE = 20;
    private static final Set<String> TYPES =
            Set.of("FULL_TIME", "PART_TIME", "CONTRACT", "INTERNSHIP");

    private final JobRepository jobs;
    private final UserRepository users;
    private final CurrentUser currentUser;
    private final ModerationService moderation;
    private final NotificationService notifications;

    public JobController(JobRepository jobs, UserRepository users,
                         CurrentUser currentUser, ModerationService moderation,
                         NotificationService notifications) {
        this.jobs = jobs;
        this.users = users;
        this.currentUser = currentUser;
        this.moderation = moderation;
        this.notifications = notifications;
    }

    @GetMapping
    public Map<String, Object> list(@RequestParam(defaultValue = "") String q,
                                    @RequestParam(defaultValue = "") String tag,
                                    @RequestParam(defaultValue = "") String type,
                                    @RequestParam(defaultValue = "1") int page) {
        String query = q.trim();
        String like = "%" + query.toLowerCase(Locale.ROOT) + "%";
        int safePage = Math.max(1, page);
        PageRequest pageable = PageRequest.of(safePage - 1, PAGE_SIZE);

        List<Job> results = jobs.search(query, like, tag, type, pageable);
        long total = jobs.searchCount(query, like, tag, type);

        Map<String, User> authors = users.findAllById(
                        results.stream().map(Job::getAuthorId).distinct().toList())
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));

        List<Map<String, Object>> list = results.stream()
                .map(j -> Json.job(j, authors.get(j.getAuthorId())))
                .toList();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("jobs", list);
        response.put("total", total);
        response.put("page", safePage);
        response.put("pageSize", PAGE_SIZE);
        return response;
    }

    public record JobRequest(String title, String company, String location, String employmentType,
                             String tag, String description, String applyUrl, String salary) {
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@RequestBody JobRequest req, HttpServletRequest http) {
        User user = currentUser.requireActiveUser(http);

        String title = trim(req == null ? null : req.title());
        String company = trim(req == null ? null : req.company());
        String description = trim(req == null ? null : req.description());
        if (title.length() < 4) throw ApiException.badRequest("Job title is required");
        if (title.length() > 160) throw ApiException.badRequest("Job title is too long");
        if (company.isEmpty()) throw ApiException.badRequest("Company name is required");
        if (description.isEmpty()) throw ApiException.badRequest("Job description is required");
        if (description.length() > 8000) throw ApiException.badRequest("Job description is too long");

        String type = req.employmentType() == null ? "FULL_TIME" : req.employmentType().trim().toUpperCase(Locale.ROOT);
        if (!TYPES.contains(type)) type = "FULL_TIME";
        String tag = req.tag() == null || !QuestionService.TAGS.contains(req.tag()) ? "GENERAL" : req.tag();

        String violation = moderation.rejectIfProfane(user, "job", title, company, description);
        if (violation != null) throw ApiException.badRequest(violation);

        Job job = Job.create();
        job.setTitle(title);
        job.setCompany(company);
        job.setLocation(trimOrNull(req.location()));
        job.setEmploymentType(type);
        job.setTag(tag);
        job.setDescription(description);
        job.setApplyUrl(trimOrNull(req.applyUrl()));
        job.setSalary(trimOrNull(req.salary()));
        job.setAuthorId(user.getId());
        jobs.save(job);

        // Email members who follow this domain (async, no-op when mail is off).
        notifications.notifyNewJob(job, user, QuestionService.tagLabel(tag));

        return ResponseEntity.status(201).body(Json.job(job, user));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id, HttpServletRequest http) {
        User user = currentUser.requireActiveUser(http);
        Job job = jobs.findById(id).orElseThrow(() -> ApiException.notFound("Job not found"));
        if (!job.getAuthorId().equals(user.getId()) && !"ADMIN".equals(user.getRole())) {
            throw ApiException.forbidden("You can only delete your own job posts");
        }
        jobs.delete(job);
        return ResponseEntity.noContent().build();
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }

    private static String trimOrNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
