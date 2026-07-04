package com.cscen.forum.service;

import com.cscen.forum.model.Question;
import com.cscen.forum.model.User;
import com.cscen.forum.repo.CommentRepository;
import com.cscen.forum.repo.UserRepository;
import com.cscen.forum.repo.VoteRepository;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class QuestionService {

    public static final Set<String> TAGS = Set.of(
            "GENERAL", "DEMAND_PLANNING", "PROCUREMENT", "MANUFACTURING", "LOGISTICS",
            "WAREHOUSING", "INVENTORY", "SUSTAINABILITY", "DIGITAL_AI", "RISK", "CAREERS");

    private final UserRepository users;
    private final CommentRepository comments;
    private final VoteRepository votes;

    public QuestionService(UserRepository users, CommentRepository comments, VoteRepository votes) {
        this.users = users;
        this.comments = comments;
        this.votes = votes;
    }

    /** Builds the question JSON list with author, counts, and viewer vote state. */
    public List<Map<String, Object>> toJson(List<Question> questionList, String viewerId) {
        if (questionList.isEmpty()) {
            return List.of();
        }
        List<String> ids = questionList.stream().map(Question::getId).toList();

        Map<String, User> authors = users
                .findAllById(questionList.stream().map(Question::getAuthorId).distinct().toList())
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));

        Map<String, Long> commentCounts = toCountMap(comments.countByQuestionIds(ids));
        Map<String, Long> voteCounts = toCountMap(votes.countByQuestionIds(ids));

        Set<String> viewerVoted = viewerId == null
                ? Set.of()
                : votes.findByUserIdAndQuestionIdIn(viewerId, ids).stream()
                        .map(v -> v.getQuestionId()).collect(Collectors.toSet());

        return questionList.stream()
                .map(q -> toJson(q, authors.get(q.getAuthorId()),
                        commentCounts.getOrDefault(q.getId(), 0L),
                        voteCounts.getOrDefault(q.getId(), 0L),
                        viewerVoted.contains(q.getId())))
                .toList();
    }

    public Map<String, Object> toJson(Question q, String viewerId) {
        return toJson(List.of(q), viewerId).get(0);
    }

    private Map<String, Object> toJson(Question q, User author, long commentCount,
                                       long voteCount, boolean viewerHasVoted) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("id", q.getId());
        json.put("title", q.getTitle());
        json.put("body", q.getBody());
        json.put("imageUrl", q.getImageUrl());
        json.put("shareCount", q.getShareCount());
        json.put("tag", q.getTag());
        json.put("acceptedCommentId", q.getAcceptedCommentId());
        json.put("createdAt", q.getCreatedAt());
        json.put("authorId", q.getAuthorId());
        json.put("author", Json.author(author));
        json.put("_count", Map.of("comments", commentCount, "votes", voteCount));
        json.put("voteCount", voteCount);
        json.put("viewerHasVoted", viewerHasVoted);
        return json;
    }

    private static Map<String, Long> toCountMap(List<Object[]> rows) {
        Map<String, Long> map = new HashMap<>();
        for (Object[] row : rows) {
            map.put((String) row[0], ((Number) row[1]).longValue());
        }
        return map;
    }
}
