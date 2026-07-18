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
import com.cscen.forum.service.SeedContent.Topic;
import com.cscen.forum.service.SeedRoster.Member;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * Seeds a community that looks lived-in rather than empty: the full
 * {@link SeedRoster} of members plus a moderator, and a back-dated history of
 * discussions drawn from {@link SeedContent} - with answers from varied members,
 * upvotes, and answers marked verified by the moderator.
 *
 * <p>Replace semantics: each run first removes the legacy prototype accounts and
 * every roster account (their content cascading away via FK ON DELETE CASCADE),
 * then re-inserts. Safe to re-run. Triggered by an admin via POST /api/admin/seed.
 *
 * <p>Day-to-day life after seeding is handled by {@link CommunityActivityService},
 * which continues the same content pool one question per day.
 */
@Service
public class SeedService {

    /** Legacy placeholder accounts from the original prototype. */
    private static final List<String> LEGACY_USERNAMES = List.of("demo_user", "user_two");

    /**
     * Shared demo password for the seeded MEMBER accounts so the owner can sign in
     * as them while demoing. The moderator deliberately gets none - see
     * {@link #person}. NOTE: this repository is public, so these member logins are
     * effectively public too; set to false to make every seeded account
     * content-only and impossible to log into.
     */
    private static final boolean SEED_MEMBERS_HAVE_DEMO_PASSWORD = true;
    private static final String DEMO_PASSWORD = "password123";

    /** Fixed so a re-seed reproduces the same believable spread of activity. */
    private static final long RANDOM_SEED = 20260714L;

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
        int removed = reset();

        // ── the cast ──
        Map<String, User> byUsername = new HashMap<>();
        List<User> members = new ArrayList<>();
        for (Member m : SeedRoster.MEMBERS) {
            User u = person(m, SEED_MEMBERS_HAVE_DEMO_PASSWORD, false);
            members.add(u);
            byUsername.put(m.username(), u);
        }
        User moderator = person(SeedRoster.MODERATOR, false, true);
        byUsername.put(SeedRoster.MODERATOR.username(), moderator);

        // ── the back-dated history ──
        Random rnd = new Random(RANDOM_SEED);
        int q = 0, a = 0, upvotes = 0, verified = 0;

        int backlog = Math.min(SeedContent.BACKLOG_SIZE, SeedContent.TOPICS.size());
        for (int i = 0; i < backlog; i++) {
            Topic t = SeedContent.TOPICS.get(i);

            // Spread authorship across the roster rather than clustering on a few names.
            User author = members.get((i * 7 + 3) % members.size());
            long daysAgo = Math.max(1, 44 - (i * 3L));
            Instant posted = Instant.now().minus(daysAgo, ChronoUnit.DAYS);

            Question qn = question(author, t.tag(), t.title(), t.body(), posted);
            q++;

            // 2-3 answers from other members, staggered over the following day or two.
            int wanted = Math.min(t.answers().size(), 2 + rnd.nextInt(2));
            List<User> answerers = sample(members, List.of(author.getId()), wanted, rnd);
            List<Comment> answers = new ArrayList<>();
            for (int k = 0; k < answerers.size(); k++) {
                Instant at = posted.plus(2L + k * 9L + rnd.nextInt(8), ChronoUnit.HOURS);
                answers.add(writeComment(answerers.get(k), qn, t.answers().get(k), at));
                a++;
            }

            // Upvotes - the question, then the answers with the earliest weighted highest.
            upvotes += upvoteQuestion(qn, sample(members, List.of(author.getId()), rnd.nextInt(9), rnd));
            for (int k = 0; k < answers.size(); k++) {
                Comment c = answers.get(k);
                int n = Math.max(0, (answers.size() - k) * 3 + rnd.nextInt(5) - 1);
                upvotes += upvoteComment(c, sample(members, List.of(c.getAuthorId()), n, rnd));
            }

            // The moderator marks the most-endorsed answer as verified on most threads.
            if (!answers.isEmpty() && rnd.nextInt(10) < 6) {
                qn.setAcceptedCommentId(answers.get(0).getId());
                questions.save(qn);
                verified++;
            }
        }

        int j = seedJobs(byUsername);
        int t = seedTemplates(byUsername, members, rnd);

        return "reset " + removed + " prior accounts; seeded " + members.size() + " members + 1 moderator, "
                + q + " questions, " + a + " answers, " + upvotes + " upvotes, "
                + verified + " verified answers, " + j + " jobs, " + t + " templates. "
                + "Daily activity will continue from topic " + backlog + " of " + SeedContent.TOPICS.size() + ".";
    }

    /** Remove legacy prototype accounts and any prior roster accounts (content cascades). */
    private int reset() {
        int removed = 0;
        for (String username : LEGACY_USERNAMES) {
            var legacy = users.findByUsername(username);
            if (legacy.isPresent()) {
                users.delete(legacy.get());
                removed++;
            }
        }
        for (String email : SeedRoster.allEmails()) {
            var prior = users.findByEmail(email);
            if (prior.isPresent()) {
                users.delete(prior.get());
                removed++;
            }
        }
        // Force the deletes (and cascades) to hit the DB before re-inserting so the
        // unique email/username constraints don't clash.
        users.flush();
        return removed;
    }

    // ── people ──

    private User person(Member m, boolean withPassword, boolean admin) {
        User u = User.create();
        u.setEmail(m.email());
        u.setUsername(m.username());
        u.setName(m.name());
        u.setMemberType(m.memberType());
        u.setOrganization(m.org());
        u.setHeadline(m.headline());
        u.setTopics(m.topics());
        u.setBio(m.bio());
        u.setVerifyStatus("APPROVED");
        u.setOpenToMentor(m.mentor());
        u.setSeekingMentor(m.seeking());
        u.setCreatedAt(Instant.now().minus(50, ChronoUnit.DAYS));
        if (admin) {
            u.setRole("ADMIN");
        }
        // The moderator is privileged, so it must never carry a known password.
        if (withPassword && !admin) {
            u.setPasswordHash(sha256(DEMO_PASSWORD));
        }
        return users.save(u);
    }

    private static String sha256(String raw) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            return java.util.Base64.getEncoder().encodeToString(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return null;
        }
    }

    // ── content helpers ──

    private Question question(User author, String tag, String title, String body, Instant createdAt) {
        Question qn = Question.create();
        qn.setTitle(title);
        qn.setBody(body.stripIndent().trim());
        qn.setTag(tag);
        qn.setAuthorId(author.getId());
        qn.setCreatedAt(createdAt);
        return questions.save(qn);
    }

    private Comment writeComment(User author, Question q, String body, Instant createdAt) {
        Comment c = Comment.create();
        c.setBody(body.stripIndent().trim());
        c.setAuthorId(author.getId());
        c.setQuestionId(q.getId());
        c.setCreatedAt(createdAt);
        return comments.save(c);
    }

    private int upvoteQuestion(Question q, List<User> voters) {
        for (User v : voters) {
            votes.save(Vote.create(v.getId(), q.getId()));
        }
        return voters.size();
    }

    private int upvoteComment(Comment c, List<User> voters) {
        for (User v : voters) {
            votes.save(Vote.createCommentVote(v.getId(), c.getId()));
        }
        return voters.size();
    }

    /** Distinct users, excluding the given ids - so nobody upvotes their own post twice. */
    private static List<User> sample(List<User> pool, List<String> excludeIds, int n, Random rnd) {
        if (n <= 0) return List.of();
        List<User> copy = new ArrayList<>(pool);
        copy.removeIf(u -> excludeIds.contains(u.getId()));
        Collections.shuffle(copy, rnd);
        return copy.subList(0, Math.min(n, copy.size()));
    }

    // ── jobs board ──

    private int seedJobs(Map<String, User> by) {
        int n = 0;
        n += job(by.get("priya_sharma"), "Senior Demand Planner - FMCG", "Mahindra Logistics", "Mumbai",
                "FULL_TIME", "DEMAND_PLANNING", "18-26 LPA",
                """
                Own the monthly S&OP forecast for a national FMCG portfolio. You'll run \
                statistical baselines, fold in promo and seasonality signals, and drive the \
                consensus meeting with sales and finance. 5+ years in demand planning and strong \
                Excel/planning-tool skills expected.""", 2);
        n += job(by.get("rahul_verma"), "Warehouse Operations Manager", "Delhivery", "Bhiwandi, Maharashtra",
                "FULL_TIME", "WAREHOUSING", "14-20 LPA",
                """
                Lead a multi-client fulfilment centre (300k sq ft): slotting, labour planning, \
                peak readiness and SLA adherence. Hands-on WMS experience and a track record of \
                running a P&L-sensitive operation required.""", 3);
        n += job(by.get("ana_ferreira"), "Strategic Sourcing Manager - Packaging", "Nestle", "Gurugram",
                "FULL_TIME", "PROCUREMENT", "20-28 LPA",
                """
                Own category strategy for packaging and logistics spend. Negotiate contracts, \
                manage supplier risk, and structure index-linked pricing with caps and collars. \
                Strong commercial and negotiation skills required.""", 4);
        n += job(by.get("daniel_okafor"), "Transportation Planner (6-month contract)", "Kontinental Freight", "Pune",
                "CONTRACT", "LOGISTICS", "Day rate - negotiable",
                """
                Short-term contract to optimise cross-border road and ocean lanes during a network \
                redesign. Build lane cost models, run carrier RFQs, and stand up an exception \
                dashboard. Contract with potential to convert.""", 1);
        n += job(by.get("arjun_nair"), "Control Tower Analyst", "Flipkart", "Bengaluru",
                "FULL_TIME", "LOGISTICS", "12-18 LPA",
                """
                Join the national control tower monitoring live shipments, resolving exceptions and \
                driving root-cause analysis on repeat failures. Comfortable with data, calm under \
                pressure, and happy to own a 2am escalation when a lane goes down.""", 5);
        n += job(by.get("fatima_sheikh"), "Cold Chain Quality Specialist", "Cipla", "Goa",
                "FULL_TIME", "MANUFACTURING", "10-16 LPA",
                """
                Own temperature-controlled distribution quality: lane validation, excursion \
                investigation and regulatory audit readiness. Pharma or food cold-chain experience \
                and a solid grasp of GDP requirements expected.""", 6);
        n += job(by.get("divya_pillai"), "Dark Store Operations Lead", "Zepto", "Hyderabad",
                "FULL_TIME", "INVENTORY", "10-15 LPA",
                """
                Run a cluster of dark stores against 10-minute delivery promises. Own availability, \
                wastage and replenishment cadence, and work with central planning on assortment. \
                Fast-paced; strong ownership required.""", 3);
        n += job(by.get("priya_sharma"), "Demand Planning Intern", "Mahindra Logistics", "Remote (India)",
                "INTERNSHIP", "DEMAND_PLANNING", "Rs 25,000/month stipend",
                """
                6-month internship for a final-year student or fresh grad. Support forecast \
                accuracy tracking, clean master data, and help run champion/challenger backtests. \
                Great first step into supply chain analytics; mentorship included.""", 7);
        n += job(by.get("meera_iyer"), "Industry Mentor / Guest Faculty", "IIM Bangalore", "Remote",
                "PART_TIME", "CAREERS", "Honorarium per session",
                """
                We're inviting experienced practitioners to mentor students and deliver occasional \
                guest sessions on real-world supply chain problems. A few hours a month; ideal for \
                senior professionals who want to give back to the next generation.""", 8);
        return n;
    }

    private int job(User author, String title, String company, String location, String type,
                    String tag, String salary, String description, long daysAgo) {
        if (author == null) return 0;
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

    // ── templates library (real small files written via UploadStorage) ──

    private int seedTemplates(Map<String, User> by, List<User> members, Random rnd) {
        Template t1 = template(by.get("rahul_verma"), "Safety Stock Calculator", "INVENTORY",
                "Leadtime and service-level based safety stock by SKU. Fill in demand variability and lead time; the reorder point columns update the same way in your sheet.",
                "safety-stock-calculator.csv", "text/csv",
                """
                sku,avg_daily_demand,demand_std,lead_time_days,service_level_z,safety_stock,reorder_point
                A100,120,25,7,1.65,109,949
                B200,60,15,14,1.65,92,932
                C300,300,60,5,1.65,221,1721
                D400,45,10,21,1.65,76,1021
                """);
        Template t2 = template(by.get("ana_ferreira"), "RFQ Supplier Comparison Sheet", "PROCUREMENT",
                "Side-by-side scoring for a multi-supplier RFQ: unit price, lead time, payment terms, quality and risk. Weight the columns to fit your category.",
                "rfq-supplier-comparison.csv", "text/csv",
                """
                supplier,unit_price,lead_time_days,payment_terms_days,quality_score,risk_score,weighted_total
                Supplier A,10.20,21,60,8.5,7.0,7.8
                Supplier B,9.80,35,45,7.5,6.0,7.1
                Supplier C,10.50,14,30,9.0,8.5,8.4
                """);
        Template t3 = template(by.get("priya_sharma"), "Forecast Bias & MAPE Tracker", "PLANNING",
                "Track forecast vs actuals by month with bias and MAPE so over/under-forecasting shows up early. Drop in your own history to start.",
                "forecast-bias-mape-tracker.csv", "text/csv",
                """
                month,forecast,actual,error,abs_pct_error,cumulative_bias
                2026-01,1000,1080,-80,8.0%,-80
                2026-02,1100,1050,50,4.5%,-30
                2026-03,1200,1310,-110,8.4%,-140
                2026-04,1150,1120,30,2.6%,-110
                """);
        Template t4 = template(by.get("daniel_okafor"), "Incoterms 2020 Quick Reference", "LOGISTICS",
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
        Template t5 = template(by.get("vikram_singh"), "Carrier Scorecard Template", "LOGISTICS",
                "Quarterly carrier performance scorecard: on-time pickup and delivery, tender acceptance, claims and billing accuracy, with a weighted total you can share with the carrier.",
                "carrier-scorecard.csv", "text/csv",
                """
                carrier,on_time_pickup_pct,on_time_delivery_pct,tender_acceptance_pct,claims_pct,billing_accuracy_pct,weighted_score
                Carrier A,96,94,98,0.4,99,94.6
                Carrier B,89,91,86,1.2,95,89.3
                Carrier C,93,88,92,0.8,97,91.0
                """);
        Template t6 = template(by.get("ritu_agarwal"), "Freight Emissions (tonne-km) Calculator", "SUSTAINABILITY",
                "Activity-based Scope 3 transport emissions by lane using tonne-km and modal factors - the basis auditors accept, unlike spend-based estimates.",
                "freight-emissions-tonne-km.csv", "text/csv",
                """
                lane,mode,tonnes,distance_km,tonne_km,factor_kg_per_tonne_km,emissions_kg_co2e
                Mumbai-Delhi,Road,18,1400,25200,0.062,1562
                Chennai-Kolkata,Rail,22,1670,36740,0.022,808
                Mundra-Rotterdam,Sea,25,11200,280000,0.008,2240
                Delhi-Bengaluru,Air,2,1750,3500,0.602,2107
                """);
        int count = 6;

        // A few upvotes so the library looks alive on first view.
        for (Template t : List.of(t1, t2, t3, t4, t5, t6)) {
            if (t == null) { count--; continue; }
            for (User v : sample(members, List.of(), 2 + rnd.nextInt(6), rnd)) {
                votes.save(Vote.createTemplateVote(v.getId(), t.getId()));
            }
        }
        return count;
    }

    private Template template(User author, String title, String category, String description,
                             String filename, String contentType, String content) {
        if (author == null) return null;
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
}
