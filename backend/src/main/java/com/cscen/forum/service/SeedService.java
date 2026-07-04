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
import java.util.List;

/**
 * Seeds grounded, real-sounding supply-chain discussions so a fresh community
 * doesn't look empty. Replace semantics: each run first removes the legacy
 * prototype accounts (demo_user / user_two) and any previous seed members —
 * with their content cascading away via FK ON DELETE CASCADE — then inserts the
 * current authentic set. Safe to re-run. Triggered by an admin via
 * POST /api/admin/seed.
 */
@Service
public class SeedService {

    // Legacy placeholder accounts from the original prototype; their content is
    // the "fake"-looking test data we want gone.
    private static final List<String> LEGACY_USERNAMES = List.of("demo_user", "user_two");

    private static final List<String> SEED_EMAILS = List.of(
            "priya.sharma.scm@gmail.com", "daniel.okafor.scm@gmail.com",
            "rahul.verma.ops@gmail.com", "ana.ferreira.sourcing@gmail.com",
            "meera.iyer.phd@gmail.com");

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
        int removed = 0;
        for (String username : LEGACY_USERNAMES) {
            var legacy = users.findByUsername(username);
            if (legacy.isPresent()) {
                users.delete(legacy.get());  // questions/comments/votes cascade in the DB
                removed++;
            }
        }
        for (String email : SEED_EMAILS) {
            var prior = users.findByEmail(email);
            if (prior.isPresent()) {
                users.delete(prior.get());
                removed++;
            }
        }
        // Force the deletes (and their cascades) to hit the DB before we re-insert,
        // so the unique email/username constraints don't clash.
        users.flush();

        User priya = person("priya.sharma.scm@gmail.com", "priya_sharma", "Priya Sharma",
                "PROFESSIONAL", "Mahindra Logistics", "Demand Planning Lead",
                "DEMAND_PLANNING,DIGITAL_AI,INVENTORY",
                "Ten years in demand planning across FMCG and 3PL. I care about forecast "
                        + "accuracy that survives contact with reality.", true, false);
        User daniel = person("daniel.okafor.scm@gmail.com", "daniel_okafor", "Daniel Okafor",
                "INDUSTRY_PARTNER", "Kontinental Freight", "Head of Logistics Operations",
                "LOGISTICS,DIGITAL_AI,RISK",
                "Running cross-border road and ocean freight ops. Pragmatic about tech - it "
                        + "has to move a KPI or it doesn't ship.", false, false);
        User rahul = person("rahul.verma.ops@gmail.com", "rahul_verma", "Rahul Verma",
                "PROFESSIONAL", "Delhivery", "Warehouse Operations Manager",
                "WAREHOUSING,INVENTORY,DIGITAL_AI",
                "I run a 300k sq ft multi-client fulfilment centre. Slotting, labour and "
                        + "peak planning are my daily reality.", false, false);
        User ana = person("ana.ferreira.sourcing@gmail.com", "ana_ferreira", "Ana Ferreira",
                "PROFESSIONAL", "Nestle", "Strategic Sourcing Manager",
                "PROCUREMENT,RISK,SUSTAINABILITY",
                "Category and sourcing lead for packaging and logistics spend. Contracts, "
                        + "risk and supplier relationships are my world.", true, false);
        User meera = person("meera.iyer.phd@gmail.com", "meera_iyer", "Meera Iyer",
                "ACADEMICIAN", "IIM Bangalore", "Professor of Supply Chain Management",
                "CAREERS,DIGITAL_AI,SUSTAINABILITY",
                "I research and teach supply chain strategy, and I mentor students moving "
                        + "into the profession.", true, false);

        int q = 0, a = 0;

        // Q1 - agentic AI reality check (Digital & AI)
        Question q1 = question(priya, "DIGITAL_AI",
                "Is 'agentic AI' in supply chain planning actually delivering, or is it still mostly hype?",
                """
                Everywhere I look, 2026 is being called \"the year of AI agents\" - agents that \
                autonomously re-run RFQs when a supplier misses a commitment, or rebook shipments \
                when a lane gets disrupted. But I also keep seeing the warning that a large share \
                of agentic-AI projects will get scrapped by 2027 over cost and unclear ROI.

                For those of you who've actually piloted this: where is it genuinely earning its \
                keep, and where did it fall over? Trying to separate signal from vendor noise \
                before we commit budget.""", 6);
        q++;
        a += answer(daniel, q1,
                """
                We piloted an exception-resolution agent on inbound lanes last quarter. Honest \
                verdict: it works, but only because we kept it on a tight leash. It recommends, a \
                planner approves - full autonomy was a bridge too far for our data quality. Net was \
                about 15 hours/week back to the team on chase-and-rebook busywork.

                The real gate isn't the model, it's master data. If your supplier lead times and \
                lane costs are dirty, the agent just makes confident wrong calls faster.""", 5, 20);
        a += answer(rahul, q1,
                """
                Same story on the warehouse side. We tried an agent for labour rebalancing across \
                zones during peak. Great in simulation, but it kept over-reacting to short spikes \
                and thrashing people between stations. We added a cooldown and a human sign-off and \
                only then did it help. Start with a narrow, reversible decision.""", 5, 6);
        a += answer(priya, q1,
                """
                This all lines up with what I'm hearing. My rule of thumb for anyone starting: pick \
                ONE narrow workflow and instrument it against a human baseline for a full quarter. \
                The projects that get scrapped are the ones that tried to automate the entire \
                planning cycle on day one.""", 4, 12);

        // Q2 - external signals in forecasting (Demand Planning)
        Question q2 = question(daniel, "DEMAND_PLANNING",
                "How are you feeding external signals (weather, promos, macro) into demand forecasts without overfitting?",
                """
                Our classical stat models are stable but blind to the outside world. The pitch for \
                ML-based forecasting is that it ingests external signals - weather, promotions, even \
                macro indicators - and continuously adjusts. My fear is we trade a boring, \
                explainable baseline for a black box that chases noise.

                What does a practical setup look like? How do you decide a signal is real and not \
                just a spurious correlation that looked great in a backtest?""", 4);
        q++;
        a += answer(priya, q2,
                """
                Governance beats model choice here. We run champion/challenger: the classical \
                baseline stays live alongside the ML model, so we can see exactly what the extra \
                signals buy us. Any new external feature has to clear a 3-month rolling backtest AND \
                improve forecast value-add, not just in-sample error, before it touches production.

                Weather and promo lift are usually worth it. Macro indicators almost never survive \
                the backtest for us - too lagged and too noisy at SKU level.""", 3, 18);
        a += answer(ana, q2,
                """
                From the sourcing side, the promo signal is only as good as the promo calendar \
                behind it. We got a big lift just by making marketing share locked promo plans with \
                planning two weeks earlier. Cleaner input beat a fancier model.""", 3, 4);

        // Q3 - WMS selection (Warehousing) - the authentic version of the old placeholder
        Question q3 = question(rahul, "WAREHOUSING",
                "WMS selection for a 50k-SKU 3PL: Manhattan Active vs Blue Yonder vs Korber - what actually matters?",
                """
                We're re-platforming the WMS for a multi-client fulfilment operation - roughly 50k \
                active SKUs, e-commerce plus B2B, moderate budget. Shortlist is Manhattan Active WM, \
                Blue Yonder and Korber. Every demo looks great; I want to hear from people who run \
                these in production.

                Specifically: how well does multi-client billing actually work, how painful was the \
                implementation and SI dependency, and how much of the 'AI slotting / labour \
                orchestration' is real vs slideware? What would you weight most?""", 2);
        q++;
        a += answer(daniel, q3,
                """
                Having lived through a Manhattan Active rollout: the product is genuinely strong and \
                the evergreen (no re-platform) model is real, but the total cost and the \
                implementation partner dependency are heavier than the sales cycle admits. For a \
                mid-size 3PL with a moderate budget, Korber tends to give you multi-client billing \
                and 3PL-specific features out of the box with less customisation.

                Weight multi-client billing and wave/waveless flexibility heavily - that's where 3PLs \
                actually bleed margin, not on the AI slotting bullet points.""", 2, 8);
        a += answer(priya, q3,
                """
                One thing that gets underweighted: the implementation partner matters more than the \
                badge on the box. Two clients on the same WMS can have wildly different outcomes based \
                on the SI. Ask each vendor for a reference customer in your exact vertical and volume \
                band, and talk to them without the vendor in the room.""", 2, 2);

        // Q4 - supplier price volatility (Procurement)
        Question q4 = question(ana, "PROCUREMENT",
                "How are you structuring contracts for supplier price volatility right now?",
                """
                With input costs still swinging, the old fixed-price annual contract feels like \
                gambling. I'm weighing index-linked pricing (tied to a public commodity index) vs \
                fixed price with a risk buffer vs shorter contract terms with more frequent \
                re-negotiation.

                What are you actually using in 2026, and how do you keep index-linked clauses from \
                turning into a one-way ratchet in the supplier's favour?""", 20, ChronoUnit.HOURS);
        q++;
        a += answerHours(daniel, q4,
                """
                Index-linked, but always with a cap and a collar so it can't run away in either \
                direction, plus a quarterly reopen window. The collar is the part people forget - it \
                protects the supplier too, which makes them far more willing to sign.""", 20);
        a += answerHours(meera, q4,
                """
                Worth stepping back to total cost and relationship, not just the price clause. \
                Dual-sourcing the volatile categories gives you a credible outside option, which does \
                more for your effective price than any clause. The literature on relational contracts \
                is pretty clear that flexibility plus a real alternative beats a clever formula.""", 4);

        // Q5 - careers / skills (Careers)
        Question q5 = question(meera, "CAREERS",
                "What should new supply chain grads learn for the AI era - beyond Excel?",
                """
                My students keep asking what makes them valuable if AI takes over routine planning \
                and expediting. I have my own view, but I'd love to hear from practitioners who are \
                actually hiring.

                If you were starting today, what two or three skills would you build to stay \
                relevant and rise - and what's overrated?""", 30, ChronoUnit.HOURS);
        q++;
        a += answerHours(priya, q5,
                """
                Data literacy plus judgment. Not coding necessarily, but the ability to read a model, \
                know when its assumptions have broken, and have the confidence to override it and \
                explain why. The planners who thrive are the ones the AI can't replace on the \
                exception calls.""", 26);
        a += answerHours(ana, q5,
                """
                The commercial and negotiation side stays very human. Supplier relationships, \
                influencing stakeholders, structuring a deal - AI drafts, people close. I'd take a \
                grad who's sharp on negotiation and cost breakdowns over one who only knows tools.""", 12);
        a += answerHours(rahul, q5,
                """
                Learn the physical operation first. Spend time on the floor of a warehouse or a \
                yard. The best analysts I've hired understood why a pick path or a dock schedule is \
                the way it is - the tech makes sense a lot faster once you've felt the constraint.""", 3);

        String result = "reset " + removed + " placeholder/old members; seeded 5 members, "
                + q + " questions, " + a + " answers";
        return result;
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
        u.setCreatedAt(Instant.now().minus(25, ChronoUnit.DAYS));
        return users.save(u);
    }

    private Question question(User author, String tag, String title, String body, long amountDays) {
        return question(author, tag, title, body, amountDays, ChronoUnit.DAYS);
    }

    private Question question(User author, String tag, String title, String body,
                             long amount, ChronoUnit unit) {
        Question qn = Question.create();
        qn.setTitle(title);
        qn.setBody(body.stripIndent().trim());
        qn.setTag(tag);
        qn.setAuthorId(author.getId());
        qn.setCreatedAt(Instant.now().minus(amount, unit));
        return questions.save(qn);
    }

    private int answer(User author, Question q, String body, long days, long hoursAfter) {
        writeComment(author, q, body, Instant.now().minus(days, ChronoUnit.DAYS)
                .plus(hoursAfter, ChronoUnit.HOURS));
        return 1;
    }

    private int answerHours(User author, Question q, String body, long hoursAgo) {
        writeComment(author, q, body, Instant.now().minus(hoursAgo, ChronoUnit.HOURS));
        return 1;
    }

    private void writeComment(User author, Question q, String body, Instant createdAt) {
        Comment c = Comment.create();
        c.setBody(body.stripIndent().trim());
        c.setAuthorId(author.getId());
        c.setQuestionId(q.getId());
        c.setCreatedAt(createdAt);
        comments.save(c);
    }
}
