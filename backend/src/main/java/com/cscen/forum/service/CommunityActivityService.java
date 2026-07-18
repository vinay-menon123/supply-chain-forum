package com.cscen.forum.service;

import com.cscen.forum.model.Comment;
import com.cscen.forum.model.Question;
import com.cscen.forum.model.User;
import com.cscen.forum.model.Vote;
import com.cscen.forum.repo.CommentRepository;
import com.cscen.forum.repo.QuestionRepository;
import com.cscen.forum.repo.UserRepository;
import com.cscen.forum.repo.VoteRepository;
import com.cscen.forum.service.SeedContent.Topic;
import com.cscen.forum.service.SeedRoster.Member;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * Keeps the seeded community alive day to day: each morning a member from the
 * {@link SeedRoster} asks the next question from {@link SeedContent}, a few other
 * members answer it, the thread picks up upvotes, and the moderator marks a strong
 * answer on an older thread as verified.
 *
 * <p>Deliberately writes straight to the repositories rather than going through
 * {@code QuestionController}, so it <b>never emails real members</b> about
 * auto-generated content.
 *
 * <p>Disable with {@code COMMUNITY_ACTIVITY_ENABLED=false} - worth doing once the
 * forum has genuine traffic of its own.
 */
@Service
public class CommunityActivityService {

    private static final Logger log = LoggerFactory.getLogger(CommunityActivityService.class);
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    /** How far back the moderator looks for a thread that still needs a verified answer. */
    private static final int VERIFY_LOOKBACK_DAYS = 7;

    private final UserRepository users;
    private final QuestionRepository questions;
    private final CommentRepository comments;
    private final VoteRepository votes;
    private final boolean enabled;

    public CommunityActivityService(UserRepository users, QuestionRepository questions,
                                    CommentRepository comments, VoteRepository votes,
                                    Environment env) {
        this.users = users;
        this.questions = questions;
        this.comments = comments;
        this.votes = votes;
        this.enabled = !"false".equalsIgnoreCase(
                env.getProperty("COMMUNITY_ACTIVITY_ENABLED", "true").trim());
        log.info("Community activity {}", enabled ? "enabled (one discussion per day)" : "disabled");
    }

    /** 09:15 IST every day. */
    @Scheduled(cron = "0 15 9 * * *", zone = "Asia/Kolkata")
    public void dailyActivity() {
        if (!enabled) {
            return;
        }
        try {
            log.info("Community activity: {}", runOnce());
        } catch (Exception e) {
            log.warn("Community activity run failed: {}", e.getMessage());
        }
    }

    /**
     * Posts one discussion and refreshes engagement. Idempotent for the day: if the
     * next topic has already been posted it will simply move to the following one,
     * and when the pool is exhausted it does nothing.
     */
    @Transactional
    public String runOnce() {
        List<User> roster = rosterMembers();
        if (roster.size() < 3) {
            return "skipped - community not seeded yet (run POST /api/admin/seed first)";
        }
        User moderator = users.findByUsername(SeedRoster.MODERATOR.username()).orElse(null);

        long day = LocalDate.now(IST).toEpochDay();
        StringBuilder summary = new StringBuilder();

        int idx = nextUnpostedTopicIndex();
        if (idx < 0) {
            summary.append("no unused topics left in the content pool (add more to SeedContent); ");
        } else {
            Topic t = SeedContent.TOPICS.get(idx);

            // Seed on the topic as well as the day: the scheduler runs once a day (so the
            // day drives it), but a manual re-run picks the NEXT topic and must not
            // reproduce the same author and the same vote counts.
            Random rnd = new Random(day * 31L + idx);
            User author = roster.get(Math.floorMod(idx * 7 + 3, roster.size()));

            // Back-date the thread a few hours so the answers below it read naturally
            // rather than every timestamp being "just now".
            Instant asked = Instant.now().minus(8, ChronoUnit.HOURS);
            Question qn = Question.create();
            qn.setTitle(t.title());
            qn.setBody(t.body().stripIndent().trim());
            qn.setTag(t.tag());
            qn.setAuthorId(author.getId());
            qn.setCreatedAt(asked);
            questions.save(qn);

            int wanted = Math.min(t.answers().size(), 2 + rnd.nextInt(2));
            List<User> answerers = sample(roster, List.of(author.getId()), wanted, rnd);
            List<Comment> posted = new ArrayList<>();
            long[] offsets = {3, 6, 7};
            for (int k = 0; k < answerers.size(); k++) {
                Comment c = Comment.create();
                c.setBody(t.answers().get(k).stripIndent().trim());
                c.setAuthorId(answerers.get(k).getId());
                c.setQuestionId(qn.getId());
                c.setCreatedAt(asked.plus(offsets[Math.min(k, offsets.length - 1)], ChronoUnit.HOURS));
                posted.add(comments.save(c));
            }

            int upvotes = 0;
            for (User v : sample(roster, List.of(author.getId()), rnd.nextInt(7), rnd)) {
                votes.save(Vote.create(v.getId(), qn.getId()));
                upvotes++;
            }
            for (int k = 0; k < posted.size(); k++) {
                Comment c = posted.get(k);
                int n = Math.max(0, (posted.size() - k) * 2 + rnd.nextInt(4));
                for (User v : sample(roster, List.of(c.getAuthorId()), n, rnd)) {
                    votes.save(Vote.createCommentVote(v.getId(), c.getId()));
                    upvotes++;
                }
            }
            summary.append(String.format("%s asked \"%s\" (%d answers, %d upvotes); ",
                    author.getUsername(), shorten(t.title()), posted.size(), upvotes));
        }

        summary.append(verifyOneAnswer(moderator));
        return summary.toString();
    }

    /**
     * The moderator reviews an older thread that has answers but no verified one, and
     * marks the most-upvoted answer as the one the community can rely on.
     */
    private String verifyOneAnswer(User moderator) {
        if (moderator == null) {
            return "no moderator account - nothing verified";
        }
        Instant since = Instant.now().minus(VERIFY_LOOKBACK_DAYS, ChronoUnit.DAYS);
        List<Question> pending =
                questions.findByAcceptedCommentIdIsNullAndCreatedAtAfterOrderByCreatedAtDesc(since);
        for (Question q : pending) {
            List<Comment> answers = comments.findByQuestionIdOrderByCreatedAtAsc(q.getId());
            if (answers.isEmpty()) {
                continue;
            }
            Comment best = answers.stream()
                    .max(Comparator.comparingLong(c -> votes.countByCommentId(c.getId())))
                    .orElse(answers.get(0));
            q.setAcceptedCommentId(best.getId());
            questions.save(q);
            return String.format("moderator verified an answer on \"%s\"", shorten(q.getTitle()));
        }
        return "nothing pending verification";
    }

    /** Index of the first topic in the pool that hasn't been posted yet, or -1 if exhausted. */
    private int nextUnpostedTopicIndex() {
        for (int i = 0; i < SeedContent.TOPICS.size(); i++) {
            if (!questions.existsByTitle(SeedContent.TOPICS.get(i).title())) {
                return i;
            }
        }
        return -1;
    }

    private List<User> rosterMembers() {
        List<User> out = new ArrayList<>();
        for (Member m : SeedRoster.MEMBERS) {
            users.findByUsername(m.username()).ifPresent(out::add);
        }
        return out;
    }

    private static List<User> sample(List<User> pool, List<String> excludeIds, int n, Random rnd) {
        if (n <= 0) return List.of();
        List<User> copy = new ArrayList<>(pool);
        copy.removeIf(u -> excludeIds.contains(u.getId()));
        Collections.shuffle(copy, rnd);
        return copy.subList(0, Math.min(n, copy.size()));
    }

    private static String shorten(String s) {
        return s == null ? "" : (s.length() <= 60 ? s : s.substring(0, 57) + "...");
    }
}
