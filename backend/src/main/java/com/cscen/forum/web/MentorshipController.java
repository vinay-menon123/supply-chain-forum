package com.cscen.forum.web;

import com.cscen.forum.repo.UserRepository;
import com.cscen.forum.service.Json;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/mentorship")
public class MentorshipController {

    private final UserRepository users;

    public MentorshipController(UserRepository users) {
        this.users = users;
    }

    /** Members who opted in as mentors / mentees; matching happens via DMs. */
    @GetMapping
    public Map<String, Object> board() {
        return Map.of(
                "mentors", users.findByOpenToMentorTrueAndIsBannedFalseOrderByCreatedAtAsc()
                        .stream().map(Json::publicUser).toList(),
                "mentees", users.findBySeekingMentorTrueAndIsBannedFalseOrderByCreatedAtAsc()
                        .stream().map(Json::publicUser).toList());
    }
}
