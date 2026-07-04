package com.cscen.forum.service;

import com.cscen.forum.model.ModerationEvent;
import com.cscen.forum.model.User;
import com.cscen.forum.repo.ModerationEventRepository;
import com.cscen.forum.repo.UserRepository;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

@Service
public class ModerationService {

    // Common English profanity, aligned with the leo-profanity dictionary
    // the original Node backend used
    private static final Set<String> BLOCKED = new HashSet<>(Arrays.asList(
            "fuck", "fucking", "fucked", "fucker", "motherfucker", "shit", "shitty",
            "bullshit", "bitch", "bitches", "bastard", "asshole", "arsehole", "ass",
            "arse", "dick", "dickhead", "cock", "cunt", "pussy", "whore", "slut",
            "douche", "douchebag", "jackass", "wanker", "bollocks", "prick", "twat",
            "nigger", "nigga", "faggot", "fag", "retard", "retarded", "damn",
            "goddamn", "goddamned", "crap", "piss", "pissed"
    ));

    private final UserRepository users;
    private final ModerationEventRepository events;
    private final AiModerationClient ai;
    private final int banThreshold;

    public ModerationService(UserRepository users, ModerationEventRepository events,
                             AiModerationClient ai, Environment env) {
        this.users = users;
        this.events = events;
        this.ai = ai;
        this.banThreshold = env.getProperty("MODERATION_BAN_THRESHOLD", Integer.class, 5);
    }

    private boolean isProfane(String text) {
        String normalized = text.toLowerCase(Locale.ROOT)
                .replace('@', 'a').replace('$', 's')
                .replace('0', 'o').replace('1', 'i').replace('3', 'e');
        for (String word : normalized.split("[^a-z']+")) {
            if (BLOCKED.contains(word.replace("'", ""))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks text against the blocklist. If it violates, the content is
     * rejected (never stored), a moderation event is recorded, the user's flag
     * count is incremented, and the account is suspended once it reaches the
     * threshold. Returns the rejection message, or null when clean.
     */
    @Transactional
    public String rejectIfProfane(User user, String kind, String... texts) {
        String combined = String.join(" ", Arrays.stream(texts)
                .filter(t -> t != null && !t.isBlank()).toList());
        if (combined.isBlank()) {
            return null;
        }
        // Fast, free wordlist first; AI classifier (when configured) catches
        // what a wordlist can't. AI errors fail open — posting never blocks on it.
        boolean violating = isProfane(combined) || ai.isViolating(combined).orElse(false);
        if (!violating) {
            return null;
        }

        user.setFlagCount(user.getFlagCount() + 1);
        events.save(ModerationEvent.create(user.getId(), kind,
                combined.length() > 500 ? combined.substring(0, 500) : combined));

        if (user.getFlagCount() >= banThreshold && !user.isBanned()) {
            user.setBanned(true);
            users.save(user);
            return "This content violates our community guidelines and was removed. "
                    + "Your account has been suspended due to repeated violations.";
        }
        users.save(user);
        if (user.isBanned()) {
            return "Your account is suspended.";
        }
        return "This content violates our community guidelines and was removed. Warning "
                + user.getFlagCount() + "/" + banThreshold
                + " — repeated violations lead to suspension.";
    }
}
