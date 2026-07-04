package com.cscen.forum.service;

import com.cscen.forum.model.Comment;
import com.cscen.forum.model.Question;
import com.cscen.forum.model.User;
import com.cscen.forum.repo.CommentRepository;
import com.cscen.forum.repo.QuestionRepository;
import com.cscen.forum.repo.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Seeds a couple of grounded, real-sounding supply-chain × AI discussions so a
 * fresh community doesn't look empty. Idempotent: re-running is a no-op once the
 * seed authors exist. Triggered by an admin via POST /api/admin/seed.
 */
@Service
public class SeedService {

    private final UserRepository users;
    private final QuestionRepository questions;
    private final CommentRepository comments;

    public SeedService(UserRepository users, QuestionRepository questions, CommentRepository comments) {
        this.users = users;
        this.comments = comments;
        this.questions = questions;
    }

    @Transactional
    public String seed() {
        if (users.findByEmail("priya.sharma.scm@gmail.com").isPresent()) {
            return "already seeded — nothing to do";
        }

        User priya = person("priya.sharma.scm@gmail.com", "priya_sharma", "Priya Sharma",
                "PROFESSIONAL", "Mahindra Logistics", "Demand Planning Lead",
                "DEMAND_PLANNING,DIGITAL_AI,INVENTORY",
                "Ten years in demand planning across FMCG and 3PL. I care about forecast "
                        + "accuracy that survives contact with reality.", true, false);
        User daniel = person("daniel.okafor.scm@gmail.com", "daniel_okafor", "Daniel Okafor",
                "INDUSTRY_PARTNER", "Kontinental Freight", "Head of Logistics Operations",
                "LOGISTICS,DIGITAL_AI,RISK",
                "Running cross-border road and ocean freight ops. Pragmatic about tech — it "
                        + "has to move a KPI or it doesn't ship.", false, false);

        // Q1 — agentic AI reality check (Digital & AI)
        Question q1 = question(priya, "DIGITAL_AI",
                "Is 'agentic AI' in supply chain planning actually delivering, or is it still mostly hype?",
                """
                Everywhere I look, 2026 is being called \"the year of AI agents\" — agents that \
                autonomously re-run RFQs when a supplier misses a commitment, or rebook shipments \
                when a lane gets disrupted. But I also keep seeing the Gartner-style warning that \
                a big share of agentic-AI projects will get scrapped by 2027 over cost and unclear ROI.

                For those of you who've actually piloted this: where is it genuinely earning its \
                keep, and where did it fall over? Trying to separate signal from vendor noise before \
                we commit budget.""", 6);
        answer(daniel, q1,
                """
                We piloted an exception-resolution agent on inbound lanes last quarter. Honest \
                verdict: it works, but only because we kept it on a tight leash. It *recommends*, \
                a planner approves — full autonomy was a bridge too far for our data quality. \
                Net was about 15 hours/week back to the team on chase-and-rebook busywork.

                The real gate isn't the model, it's master data. If your supplier lead times and \
                lane costs are dirty, the agent just makes confident wrong calls faster.""", 4);
        answer(priya, q1,
                """
                That matches what I'm hearing. My rule of thumb for anyone starting: pick ONE \
                narrow workflow (RFQ ranking, or expediting alerts) and instrument it against a \
                human baseline for a full quarter. The projects that get scrapped are the ones that \
                tried to automate the entire planning cycle on day one.""", 2);

        // Q2 — external signals in forecasting (Demand Planning)
        Question q2 = question(daniel, "DEMAND_PLANNING",
                "How are you feeding external signals (weather, promos, macro) into demand forecasts without overfitting?",
                """
                Our classical stat models are stable but blind to the outside world. The pitch for \
                ML-based forecasting is that it ingests external signals — weather, promotions, \
                even macro indicators — and continuously adjusts. My fear is we trade a boring, \
                explainable baseline for a black box that chases noise.

                What does a *practical* setup look like? How do you decide a signal is real and not \
                just a spurious correlation that looks great in a backtest?""", 5);
        answer(priya, q2,
                """
                Governance beats model choice here. We run champion/challenger: the classical \
                baseline is always live alongside the ML model, so we can see exactly what the extra \
                signals buy us. Any new external feature has to clear a 3-month rolling backtest AND \
                improve forecast value-add, not just in-sample error, before it touches production.

                Weather and promo lift are usually worth it. Macro indicators almost never survive \
                the backtest for us — too lagged and too noisy at SKU level.""", 3);
        answer(daniel, q2,
                """
                Feature-importance monitoring saved us from exactly the overfitting you're worried \
                about — we caught a 'signal' that was really just a data-pipeline artifact. Keep the \
                explainable baseline as your safety net and treat every fancy signal as guilty until \
                proven innocent.""", 1);

        return "seeded 2 members, 2 questions, 4 answers";
    }

    private User person(String email, String username, String name, String memberType,
                        String org, String headline, String topics, String bio,
                        boolean mentor, boolean seeking) {
        User u = User.create();
        u.setEmail(email);
        u.setUsername(username);
        u.setName(name);
        u.setMemberType(memberType);
        u.setOrganization(org);
        u.setHeadline(headline);
        u.setTopics(topics);
        u.setBio(bio);
        u.setVerifyStatus("APPROVED");
        u.setOpenToMentor(mentor);
        u.setSeekingMentor(seeking);
        u.setCreatedAt(Instant.now().minus(20, ChronoUnit.DAYS));
        return users.save(u);
    }

    private Question question(User author, String tag, String title, String body, int daysAgo) {
        Question q = Question.create();
        q.setTitle(title);
        q.setBody(body.stripIndent().trim());
        q.setTag(tag);
        q.setAuthorId(author.getId());
        q.setCreatedAt(Instant.now().minus(daysAgo, ChronoUnit.DAYS));
        return questions.save(q);
    }

    private void answer(User author, Question q, String body, int daysAgo) {
        Comment c = Comment.create();
        c.setBody(body.stripIndent().trim());
        c.setAuthorId(author.getId());
        c.setQuestionId(q.getId());
        c.setCreatedAt(Instant.now().minus(daysAgo, ChronoUnit.DAYS));
        comments.save(c);
    }
}
