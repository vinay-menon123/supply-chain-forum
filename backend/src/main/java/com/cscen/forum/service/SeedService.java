package com.cscen.forum.service;

import com.cscen.forum.model.Comment;
import com.cscen.forum.model.Job;
import com.cscen.forum.model.Question;
import com.cscen.forum.model.Template;
import com.cscen.forum.model.User;
import com.cscen.forum.model.Vote;
import com.cscen.forum.repo.CommentRepository;
import com.cscen.forum.repo.JobRepository;
import com.cscen.forum.repo.QuestionRepository;
import com.cscen.forum.repo.TemplateRepository;
import com.cscen.forum.repo.UserRepository;
import com.cscen.forum.repo.VoteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;

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
    private final JobRepository jobs;
    private final TemplateRepository templates;
    private final VoteRepository votes;
    private final UploadStorage uploads;

    public SeedService(UserRepository users, QuestionRepository questions, CommentRepository comments,
                       JobRepository jobs, TemplateRepository templates, VoteRepository votes,
                       UploadStorage uploads) {
        this.users = users;
        this.comments = comments;
        this.questions = questions;
        this.jobs = jobs;
        this.templates = templates;
        this.votes = votes;
        this.uploads = uploads;
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

        int j = seedJobs(priya, daniel, rahul, ana, meera);
        int t = seedTemplates(priya, daniel, rahul, ana, meera);

        String result = "reset " + removed + " placeholder/old members; seeded 5 members, "
                + q + " questions, " + a + " answers, " + j + " jobs, " + t + " templates";
        return result;
    }

    // ── Sample jobs board (authored by the seed members; cascade-cleaned on re-seed) ──
    private int seedJobs(User priya, User daniel, User rahul, User ana, User meera) {
        int n = 0;
        n += job(priya, "Senior Demand Planner - FMCG", "Mahindra Logistics", "Mumbai",
                "FULL_TIME", "DEMAND_PLANNING", "18-26 LPA",
                """
                Own the monthly S&OP forecast for a national FMCG portfolio. You'll run \
                statistical baselines, fold in promo and seasonality signals, and drive the \
                consensus meeting with sales and finance. 5+ years in demand planning and strong \
                Excel/planning-tool skills expected.""", 2);
        n += job(rahul, "Warehouse Operations Manager", "Delhivery", "Bhiwandi, Maharashtra",
                "FULL_TIME", "WAREHOUSING", "14-20 LPA",
                """
                Lead a multi-client fulfilment centre (300k sq ft): slotting, labour planning, \
                peak readiness and SLA adherence. Hands-on WMS experience and a track record of \
                running a P&L-sensitive operation required.""", 3);
        n += job(ana, "Strategic Sourcing Manager - Packaging", "Nestle", "Gurugram",
                "FULL_TIME", "PROCUREMENT", "20-28 LPA",
                """
                Own category strategy for packaging and logistics spend. Negotiate contracts, \
                manage supplier risk, and structure index-linked pricing with caps and collars. \
                Strong commercial and negotiation skills; category management background preferred.""", 4);
        n += job(daniel, "Transportation Planner (6-month contract)", "Kontinental Freight", "Pune",
                "CONTRACT", "LOGISTICS", "Day rate - negotiable",
                """
                Short-term contract to optimise cross-border road and ocean lanes during a network \
                redesign. Build lane cost models, run carrier RFQs, and stand up an exception \
                dashboard. Contract with potential to convert.""", 1);
        n += job(priya, "Demand Planning Intern", "Mahindra Logistics", "Remote (India)",
                "INTERNSHIP", "DEMAND_PLANNING", "Rs 25,000/month stipend",
                """
                6-month internship for a final-year student or fresh grad. Support forecast \
                accuracy tracking, clean master data, and help run champion/challenger backtests. \
                Great first step into supply chain analytics; mentorship included.""", 5);
        n += job(meera, "Industry Mentor / Guest Faculty", "IIM Bangalore", "Remote",
                "PART_TIME", "CAREERS", "Honorarium per session",
                """
                We're inviting experienced practitioners to mentor students and deliver occasional \
                guest sessions on real-world supply chain problems. A few hours a month; ideal for \
                senior professionals who want to give back to the next generation.""", 6);
        return n;
    }

    private int job(User author, String title, String company, String location, String type,
                    String tag, String salary, String description, long daysAgo) {
        Job job = Job.create();
        job.setTitle(title);
        job.setCompany(company);
        job.setLocation(location);
        job.setEmploymentType(type);
        job.setTag(tag);
        job.setSalary(salary);
        job.setDescription(description.stripIndent().trim());
        job.setAuthorId(author.getId());
        job.setCreatedAt(Instant.now().minus(daysAgo, ChronoUnit.DAYS));
        jobs.save(job);
        return 1;
    }

    // ── Sample templates library (real small files written via UploadStorage) ──
    private int seedTemplates(User priya, User daniel, User rahul, User ana, User meera) {
        Template t1 = template(rahul, "Safety Stock Calculator", "INVENTORY",
                "Leadtime and service-level based safety stock by SKU. Fill in demand variability and lead time; the reorder point columns update the same way in your sheet.",
                "safety-stock-calculator.csv", "text/csv",
                """
                sku,avg_daily_demand,demand_std,lead_time_days,service_level_z,safety_stock,reorder_point
                A100,120,25,7,1.65,109,949
                B200,60,15,14,1.65,92,932
                C300,300,60,5,1.65,221,1721
                D400,45,10,21,1.65,76,1021
                """);
        Template t2 = template(ana, "RFQ Supplier Comparison Sheet", "PROCUREMENT",
                "Side-by-side scoring for a multi-supplier RFQ: unit price, lead time, payment terms, quality and risk. Weight the columns to fit your category.",
                "rfq-supplier-comparison.csv", "text/csv",
                """
                supplier,unit_price,lead_time_days,payment_terms_days,quality_score,risk_score,weighted_total
                Supplier A,10.20,21,60,8.5,7.0,7.8
                Supplier B,9.80,35,45,7.5,6.0,7.1
                Supplier C,10.50,14,30,9.0,8.5,8.4
                """);
        Template t3 = template(priya, "Forecast Bias & MAPE Tracker", "PLANNING",
                "Track forecast vs actuals by month with bias and MAPE so over/under-forecasting shows up early. Drop in your own history to start.",
                "forecast-bias-mape-tracker.csv", "text/csv",
                """
                month,forecast,actual,error,abs_pct_error,cumulative_bias
                2026-01,1000,1080,-80,8.0%,-80
                2026-02,1100,1050,50,4.5%,-30
                2026-03,1200,1310,-110,8.4%,-140
                2026-04,1150,1120,30,2.6%,-110
                """);
        Template t4 = template(daniel, "Incoterms 2020 Quick Reference", "LOGISTICS",
                "One-page cheat-sheet of the 11 Incoterms 2020 rules and where risk transfers, for quick reference in freight and contract discussions.",
                "incoterms-2020-cheatsheet.txt", "text/plain",
                """
                INCOTERMS 2020 - QUICK REFERENCE

                Any mode of transport:
                  EXW - Ex Works: buyer bears everything from seller's door.
                  FCA - Free Carrier: seller hands goods to buyer's carrier.
                  CPT - Carriage Paid To: seller pays carriage; risk passes at first carrier.
                  CIP - Carriage & Insurance Paid To: CPT + seller insures (all-risk).
                  DAP - Delivered At Place: seller delivers, ready for unloading.
                  DPU - Delivered At Place Unloaded: seller unloads at destination.
                  DDP - Delivered Duty Paid: seller covers duties and clearance.

                Sea and inland waterway only:
                  FAS - Free Alongside Ship: risk passes alongside the vessel.
                  FOB - Free On Board: risk passes once goods are on board.
                  CFR - Cost & Freight: seller pays freight; risk passes on board.
                  CIF - Cost, Insurance & Freight: CFR + seller insures (minimum cover).
                """);
        // A few upvotes so the library looks alive on first view.
        voteTemplate(t1, priya, ana, meera);
        voteTemplate(t2, priya, daniel);
        voteTemplate(t3, ana, rahul, meera, daniel);
        voteTemplate(t4, priya, rahul);
        return 4;
    }

    private Template template(User author, String title, String category, String description,
                             String filename, String contentType, String content) {
        String url = uploads.saveBytes(content.stripIndent().trim().getBytes(StandardCharsets.UTF_8),
                filename, contentType);
        Template tpl = Template.create();
        tpl.setTitle(title);
        tpl.setDescription(description);
        tpl.setCategory(category);
        tpl.setFileUrl(url);
        tpl.setFileName(filename);
        int dot = filename.lastIndexOf('.');
        tpl.setFileType(dot >= 0 ? filename.substring(dot + 1).toUpperCase(Locale.ROOT) : "FILE");
        tpl.setAuthorId(author.getId());
        tpl.setCreatedAt(Instant.now().minus(3, ChronoUnit.DAYS));
        return templates.save(tpl);
    }

    private void voteTemplate(Template t, User... voters) {
        for (User v : voters) {
            votes.save(Vote.createTemplateVote(v.getId(), t.getId()));
        }
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
        
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest("password123".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            u.setPasswordHash(java.util.Base64.getEncoder().encodeToString(hash));
        } catch (Exception e) {
            // no-op
        }
        
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
