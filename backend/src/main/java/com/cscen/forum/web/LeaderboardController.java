package com.cscen.forum.web;

import com.cscen.forum.repo.UserRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/leaderboard")
public class LeaderboardController {

    private final UserRepository users;

    public LeaderboardController(UserRepository users) {
        this.users = users;
    }

    @GetMapping
    public Map<String, Object> leaderboard() {
        List<Map<String, Object>> leaders = new ArrayList<>();
        for (Object[] row : users.leaderboard(20)) {
            long questions = ((Number) row[6]).longValue();
            long comments = ((Number) row[7]).longValue();
            long upvotes = ((Number) row[8]).longValue();
            long accepted = ((Number) row[9]).longValue();

            Map<String, Object> user = new LinkedHashMap<>();
            user.put("id", row[0]);
            user.put("username", row[1]);
            user.put("name", row[2]);
            user.put("avatarUrl", row[3]);
            user.put("memberType", row[4]);
            user.put("role", row[5]);

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("user", user);
            entry.put("stats", Map.of(
                    "questions", questions,
                    "comments", comments,
                    "upvotesReceived", upvotes,
                    "accepted", accepted));
            entry.put("reputation", UserController.reputation(questions, comments, upvotes, accepted));
            leaders.add(entry);
        }
        return Map.of("leaders", leaders);
    }
}
