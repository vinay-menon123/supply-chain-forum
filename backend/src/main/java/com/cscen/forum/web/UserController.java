package com.cscen.forum.web;

import com.cscen.forum.model.Question;
import com.cscen.forum.model.User;
import com.cscen.forum.repo.CommentRepository;
import com.cscen.forum.repo.QuestionRepository;
import com.cscen.forum.repo.UserRepository;
import com.cscen.forum.repo.VoteRepository;
import com.cscen.forum.security.CurrentUser;
import com.cscen.forum.service.Json;
import com.cscen.forum.service.QuestionService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserRepository users;
    private final QuestionRepository questions;
    private final CommentRepository comments;
    private final VoteRepository votes;
    private final QuestionService questionService;
    private final CurrentUser currentUser;

    public UserController(UserRepository users, QuestionRepository questions,
                          CommentRepository comments, VoteRepository votes,
                          QuestionService questionService, CurrentUser currentUser) {
        this.users = users;
        this.questions = questions;
        this.comments = comments;
        this.votes = votes;
        this.questionService = questionService;
        this.currentUser = currentUser;
    }

    public static long reputation(long questionCount, long commentCount, long upvotes, long accepted) {
        return questionCount * 5 + commentCount * 2 + upvotes * 10 + accepted * 15;
    }

    @GetMapping("/{username}")
    public Map<String, Object> profile(@PathVariable String username, HttpServletRequest http) {
        User user = users.findByUsername(username)
                .orElseThrow(() -> ApiException.notFound("User not found"));
        String viewerId = currentUser.optionalUserId(http);

        long questionCount = questions.countByAuthorId(user.getId());
        long commentCount = comments.countByAuthorId(user.getId());
        long upvotesReceived = votes.countReceivedByAuthor(user.getId());
        long accepted = questions.countAcceptedForAuthor(user.getId());

        List<Question> own = questions.findByAuthorIdOrderByCreatedAtDesc(
                user.getId(), PageRequest.of(0, 20));
        List<Question> commented = questions
                .findAllById(comments.questionIdsCommentedBy(user.getId())).stream()
                .sorted(Comparator.comparing(Question::getCreatedAt).reversed())
                .limit(20)
                .toList();

        Map<String, Object> userJson = Json.publicUser(user);
        userJson.put("reputation", reputation(questionCount, commentCount, upvotesReceived, accepted));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("user", userJson);
        response.put("stats", Map.of(
                "questions", questionCount,
                "comments", commentCount,
                "upvotesReceived", upvotesReceived,
                "accepted", accepted));
        response.put("questions", questionService.toJson(own, viewerId));
        response.put("commented", questionService.toJson(commented, viewerId));
        return response;
    }
}
