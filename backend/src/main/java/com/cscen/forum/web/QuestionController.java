package com.cscen.forum.web;

import com.cscen.forum.model.Comment;
import com.cscen.forum.model.Question;
import com.cscen.forum.model.User;
import com.cscen.forum.model.Vote;
import com.cscen.forum.repo.CommentRepository;
import com.cscen.forum.repo.QuestionRepository;
import com.cscen.forum.repo.UserRepository;
import com.cscen.forum.repo.VoteRepository;
import com.cscen.forum.security.CurrentUser;
import com.cscen.forum.service.InAppNotifier;
import com.cscen.forum.service.Json;
import com.cscen.forum.service.ModerationService;
import com.cscen.forum.service.NotificationService;
import com.cscen.forum.service.QuestionService;
import com.cscen.forum.service.UploadStorage;
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
import org.springframework.web.multipart.MultipartFile;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/questions")
public class QuestionController {

    private static final int PAGE_SIZE = 20;

    private final QuestionRepository questions;
    private final CommentRepository comments;
    private final VoteRepository votes;
    private final UserRepository users;
    private final QuestionService questionService;
    private final ModerationService moderation;
    private final NotificationService notifications;
    private final InAppNotifier inApp;
    private final UploadStorage uploads;
    private final CurrentUser currentUser;

    public QuestionController(QuestionRepository questions, CommentRepository comments,
                              VoteRepository votes, UserRepository users,
                              QuestionService questionService, ModerationService moderation,
                              NotificationService notifications, InAppNotifier inApp,
                              UploadStorage uploads, CurrentUser currentUser) {
        this.questions = questions;
        this.comments = comments;
        this.votes = votes;
        this.users = users;
        this.questionService = questionService;
        this.moderation = moderation;
        this.notifications = notifications;
        this.inApp = inApp;
        this.uploads = uploads;
        this.currentUser = currentUser;
    }

    @GetMapping
    public Map<String, Object> list(@RequestParam(defaultValue = "") String q,
                                    @RequestParam(defaultValue = "1") int page,
                                    @RequestParam(defaultValue = "") String sort,
                                    @RequestParam(defaultValue = "") String tag,
                                    HttpServletRequest http) {
        String viewerId = currentUser.optionalUserId(http);
        String query = q.trim();
        String like = "%" + query.toLowerCase(Locale.ROOT) + "%";
        int safePage = Math.max(1, page);
        PageRequest pageable = PageRequest.of(safePage - 1, PAGE_SIZE);

        List<Question> results = "top".equals(sort)
                ? questions.searchTop(query, like, tag, pageable)
                : questions.searchNewest(query, like, tag, pageable);
        long total = questions.searchCount(query, like, tag);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("questions", questionService.toJson(results, viewerId));
        response.put("total", total);
        response.put("page", safePage);
        response.put("pageSize", PAGE_SIZE);
        return response;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(
            @RequestParam String title,
            @RequestParam String body,
            @RequestParam(defaultValue = "GENERAL") String tag,
            @RequestParam(value = "image", required = false) MultipartFile image,
            HttpServletRequest http) {
        User user = currentUser.requireActiveUser(http);

        String cleanTitle = title == null ? "" : title.trim();
        String cleanBody = body == null ? "" : body.trim();
        if (cleanTitle.length() < 8) throw ApiException.badRequest("Title must be at least 8 characters");
        if (cleanTitle.length() > 200) throw ApiException.badRequest("Title is too long");
        if (cleanBody.isEmpty()) throw ApiException.badRequest("Question body is required");
        if (cleanBody.length() > 20000) throw ApiException.badRequest("Body is too long");
        if (!QuestionService.TAGS.contains(tag)) throw ApiException.badRequest("Unknown topic tag");

        String violation = moderation.rejectIfProfane(user, "question", cleanTitle, cleanBody);
        if (violation != null) throw ApiException.badRequest(violation);

        Question question = Question.create();
        question.setTitle(cleanTitle);
        question.setBody(cleanBody);
        question.setTag(tag);
        question.setImageUrl(uploads.saveImage(image));
        question.setAuthorId(user.getId());
        questions.save(question);

        // Email members who follow this topic (async, no-op when SMTP is off)
        notifications.notifyNewQuestion(question, user, QuestionService.tagLabel(tag));
        // In-app @mentions in the question body
        inApp.mentions(cleanBody, user, question.getId(), null, null);

        return ResponseEntity.status(201).body(questionService.toJson(question, user.getId()));
    }

    @GetMapping("/{id}")
    public Map<String, Object> detail(@PathVariable String id, HttpServletRequest http) {
        Question question = questions.findById(id)
                .orElseThrow(() -> ApiException.notFound("Question not found"));

        String viewerId = currentUser.optionalUserId(http);
        Map<String, Object> json = questionService.toJson(question, viewerId);

        List<Comment> commentList = comments.findByQuestionIdOrderByCreatedAtAsc(id);
        Map<String, User> authors = users
                .findAllById(commentList.stream().map(Comment::getAuthorId).distinct().toList())
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));

        List<Comment> answersList = commentList.stream()
                .filter(c -> c.getParentId() == null)
                .toList();

        List<String> answerIds = answersList.stream().map(Comment::getId).toList();

        Map<String, Long> commentVoteCounts = toCountMap(votes.countByCommentIds(answerIds));
        Set<String> viewerVotedComments = viewerId == null
                ? Set.of()
                : votes.findByUserIdAndCommentIdIn(viewerId, answerIds).stream()
                        .map(Vote::getCommentId).collect(Collectors.toSet());

        String acceptedId = question.getAcceptedCommentId();

        List<Comment> sortedAnswers = answersList.stream()
                .sorted((a, b) -> {
                    if (a.getId().equals(acceptedId)) return -1;
                    if (b.getId().equals(acceptedId)) return 1;
                    long countA = commentVoteCounts.getOrDefault(a.getId(), 0L);
                    long countB = commentVoteCounts.getOrDefault(b.getId(), 0L);
                    return Long.compare(countB, countA);
                })
                .toList();

        Map<String, List<Comment>> repliesByParent = commentList.stream()
                .filter(c -> c.getParentId() != null)
                .collect(Collectors.groupingBy(Comment::getParentId));

        List<Map<String, Object>> answersJson = sortedAnswers.stream().map(ans -> {
            Map<String, Object> ansMap = Json.comment(
                    ans,
                    authors.get(ans.getAuthorId()),
                    commentVoteCounts.getOrDefault(ans.getId(), 0L),
                    viewerVotedComments.contains(ans.getId())
            );
            List<Comment> replies = repliesByParent.getOrDefault(ans.getId(), List.of());
            List<Map<String, Object>> repliesJson = replies.stream()
                    .map(r -> Json.comment(r, authors.get(r.getAuthorId())))
                    .toList();
            ansMap.put("comments", repliesJson);
            return ansMap;
        }).toList();

        json.put("comments", answersJson);
        return json;
    }

    private static Map<String, Long> toCountMap(List<Object[]> rows) {
        Map<String, Long> map = new LinkedHashMap<>();
        for (Object[] row : rows) {
            map.put((String) row[0], ((Number) row[1]).longValue());
        }
        return map;
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id, HttpServletRequest http) {
        User user = currentUser.requireActiveUser(http);
        Question question = questions.findById(id)
                .orElseThrow(() -> ApiException.notFound("Question not found"));
        if (!question.getAuthorId().equals(user.getId()) && !"ADMIN".equals(user.getRole())) {
            throw ApiException.forbidden("You can only delete your own questions");
        }
        questions.delete(question);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/vote")
    public Map<String, Object> vote(@PathVariable String id, HttpServletRequest http) {
        User user = currentUser.requireActiveUser(http);
        if (!questions.existsById(id)) {
            throw ApiException.notFound("Question not found");
        }
        Optional<Vote> existing = votes.findByUserIdAndQuestionId(user.getId(), id);
        if (existing.isPresent()) {
            votes.delete(existing.get());
        } else {
            votes.save(Vote.create(user.getId(), id));
        }
        return Map.of("voteCount", votes.countByQuestionId(id), "viewerHasVoted", existing.isEmpty());
    }

    @PostMapping("/{id}/comments/{commentId}/vote")
    public Map<String, Object> voteComment(@PathVariable String id,
                                           @PathVariable String commentId,
                                           HttpServletRequest http) {
        User user = currentUser.requireActiveUser(http);
        Comment comment = comments.findById(commentId)
                .filter(c -> c.getQuestionId().equals(id))
                .orElseThrow(() -> ApiException.notFound("Answer not found"));

        Optional<Vote> existing = votes.findByUserIdAndCommentId(user.getId(), commentId);
        if (existing.isPresent()) {
            votes.delete(existing.get());
        } else {
            votes.save(Vote.createCommentVote(user.getId(), commentId));
        }
        return Map.of("voteCount", votes.countByCommentId(commentId), "viewerHasVoted", existing.isEmpty());
    }

    @PostMapping("/{id}/comments")
    public ResponseEntity<Map<String, Object>> comment(
            @PathVariable String id,
            @RequestParam String body,
            @RequestParam(required = false) String parentId,
            @RequestParam(value = "image", required = false) MultipartFile image,
            HttpServletRequest http) {
        User user = currentUser.requireActiveUser(http);
        String cleanBody = body == null ? "" : body.trim();
        if (cleanBody.isEmpty()) throw ApiException.badRequest("Comment cannot be empty");
        if (cleanBody.length() > 5000) throw ApiException.badRequest("Comment is too long");
        Question question = questions.findById(id)
                .orElseThrow(() -> ApiException.notFound("Question not found"));

        String parentAuthorId = null;
        if (parentId != null) {
            Comment parent = comments.findById(parentId)
                    .orElseThrow(() -> ApiException.notFound("Answer not found"));
            if (parent.getParentId() != null) {
                throw ApiException.badRequest("Only answers can have comments");
            }
            parentAuthorId = parent.getAuthorId();
        }

        String violation = moderation.rejectIfProfane(user, "comment", cleanBody);
        if (violation != null) throw ApiException.badRequest(violation);

        Comment comment = Comment.create();
        comment.setBody(cleanBody);
        comment.setImageUrl(uploads.saveImage(image));
        comment.setAuthorId(user.getId());
        comment.setQuestionId(id);
        comment.setParentId(parentId);
        comments.save(comment);

        // In-app notifications: a reply pings the answer's author; a top-level
        // answer pings the question's author. @mentions ping anyone else named.
        Set<String> notified = new HashSet<>();
        if (parentAuthorId != null) {
            inApp.replied(parentAuthorId, user, id, comment.getId());
            notified.add(parentAuthorId);
        } else {
            inApp.answered(question.getAuthorId(), user, id);
            notified.add(question.getAuthorId());
        }
        inApp.mentions(cleanBody, user, id, comment.getId(), notified);

        return ResponseEntity.status(201).body(Json.comment(comment, user));
    }

    @DeleteMapping("/{id}/comments/{commentId}")
    public ResponseEntity<Void> deleteComment(@PathVariable String id,
                                              @PathVariable String commentId,
                                              HttpServletRequest http) {
        User user = currentUser.requireActiveUser(http);
        Comment comment = comments.findById(commentId)
                .filter(c -> c.getQuestionId().equals(id))
                .orElseThrow(() -> ApiException.notFound("Comment not found"));
        if (!comment.getAuthorId().equals(user.getId()) && !"ADMIN".equals(user.getRole())) {
            throw ApiException.forbidden("You can only delete your own comments");
        }
        // Clear the accepted marker if the accepted answer is being removed
        Question question = questions.findById(id).orElse(null);
        if (question != null && commentId.equals(question.getAcceptedCommentId())) {
            question.setAcceptedCommentId(null);
            questions.save(question);
        }
        comments.delete(comment);
        return ResponseEntity.noContent().build();
    }

    /** Question author (or admin) marks the accepted answer; calling again unmarks. */
    @PostMapping("/{id}/comments/{commentId}/accept")
    public Map<String, Object> accept(@PathVariable String id,
                                      @PathVariable String commentId,
                                      HttpServletRequest http) {
        User user = currentUser.requireActiveUser(http);
        Question question = questions.findById(id)
                .orElseThrow(() -> ApiException.notFound("Question not found"));
        if (!question.getAuthorId().equals(user.getId()) && !"ADMIN".equals(user.getRole())) {
            throw ApiException.forbidden("Only the question author can accept an answer");
        }
        Map<String, Object> response = new LinkedHashMap<>();
        if (commentId.equals(question.getAcceptedCommentId())) {
            question.setAcceptedCommentId(null);
        } else {
            Comment accepted = comments.findById(commentId)
                    .filter(c -> c.getQuestionId().equals(id))
                    .orElseThrow(() -> ApiException.notFound("Comment not found"));
            question.setAcceptedCommentId(commentId);
            inApp.accepted(accepted.getAuthorId(), user, id, commentId);
        }
        questions.save(question);
        response.put("acceptedCommentId", question.getAcceptedCommentId());
        return response;
    }

    @PostMapping("/{id}/share")
    public Map<String, Object> share(@PathVariable String id) {
        Question question = questions.findById(id)
                .orElseThrow(() -> ApiException.notFound("Question not found"));
        question.setShareCount(question.getShareCount() + 1);
        questions.save(question);
        return Map.of("id", question.getId(), "shareCount", question.getShareCount());
    }
}
