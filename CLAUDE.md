# CLAUDE.md — CSCE Nexus Forum

> Persistent project context for Claude Code. Read this first; it saves re-deriving the
> whole history. **Keep it updated:** whenever we change architecture, add a feature, fix a
> notable bug, or change deploy/env details, edit the relevant section here in the same change.

---

## 1. What this is

**CSCE Nexus** — a Stack Overflow / Quora-style Q&A forum for the **Centre for Supply Chain
Excellence (CSCEN)**. Business goal: join a NASSCOM-like org, help small supply-chain
companies, help people learn, and make a profit. The forum is the community + engagement layer.

**Owner / admin:** Vinay Menon (GitHub `vinay-menon123`). Admin login email
`vinay.menon2707@gmail.com` (an editable parameter, not hard-coded — see `ADMIN_EMAILS`).

**Repo is PUBLIC.** Treat everything committed as world-readable. Real secrets NEVER go in the
repo — only in Railway Variables / runtime env. `k8s/01-secrets.yaml` holds dev placeholders only.

---

## 2. Live environments

| Env | URL | Notes |
|---|---|---|
| **Railway (production)** | https://supply-chain-forum-production.up.railway.app | Single all-in-one image (root `Dockerfile`). `PORT=4000` pinned. DB via `${{Postgres.DATABASE_URL}}`. |
| **Docker Compose (local)** | http://localhost:3000 (api :4000) | `docker-compose.yml`; separate frontend/backend images + postgres. |
| **Docker Desktop k8s (local)** | http://localhost:8080 | namespace `forum`, LoadBalancer. Images `forum-backend:vN` / `forum-frontend:vN`. |

**Railway = the real one.** Compose and k8s are local dev/demo targets.

---

## 3. Tech stack

**Frontend:** React 18 + Vite 5 + TypeScript (strict, `noUnusedLocals`), Tailwind v3
(`darkMode:"class"`), React Router 6, Google Identity Services (GIS) for sign-in, polling-based
chat. Custom keyframes: `fade-in-up`, `float`, `gradient-x`, `pulse-glow`, `wiggle`; respects
`prefers-reduced-motion`.

**Backend:** **Java 21 + Spring Boot 3.4.1**, Spring Data JPA, Maven. (Rewritten from an
original Node/Express/Prisma backend, which now lives only in git history.)
- Maps to the original Prisma quoted-camelCase tables via `PhysicalNamingStrategyStandardImpl`
  + `hibernate.globally_quoted_identifiers=true`.
- Plain `String` FK fields — **no JPA relations**. DTOs are hand-built `LinkedHashMap`s
  (in `service/Json.java`) matching the old Node JSON shapes exactly.
- Schema managed by idempotent `schema.sql` (`ddl-auto=none`, `sql.init.mode=always`).
- `hibernate.jdbc.time_zone=UTC`, `Instant` fields.
- Auth: **jjwt 0.12** — HS256. Key rule: if `JWT_SECRET` ≥ 32 bytes, raw bytes are the key
  (preserves Railway session continuity); otherwise SHA-256-stretched. Test tokens must match.
  Login = email/username + password (SHA-256 hash) + email OTP; see `web/AuthController.java`.
  (Google Sign-In removed — the `GoogleIdTokenVerifier`/google-api-client path is gone.)
- AI moderation: **Anthropic Java SDK** `com.anthropic:anthropic-java:2.34.0`. Model default
  `claude-opus-4-8` (override via `ANTHROPIC_MODEL`), `maxTokens=256`, no thinking param,
  **fails open** (never blocks on API error). See `service/AiModerationClient.java`.
- Email: spring-boot-starter-mail via shared `MailService` (no-op until `SMTP_HOST`). Weekly
  digest cron `0 0 9 * * MON` (`@EnableScheduling`); topic-follow new-question emails via
  `NotificationService` (`@EnableAsync`, both annotations on `ForumApplication`).
- AI verify: `AiModerationClient.isSupplyChainRelevant(headline, linkedin)` — same Anthropic client
  as moderation, `maxTokens=16`, RELEVANT/UNRELATED, empty on disabled/error → member stays PENDING.

---

## 4. Features (all shipped)

- **Auth:** **email + password with an email OTP** (2-step) — `AuthController` `/send-otp`,
  `/register`, `/login`, `/reset-password`. OTP is a 6-digit code, single-use, **5-min TTL**
  (`otpStorage`, in-memory), emailed via `MailService`. When mail can't send, the response includes a
  `devOtp` **only if `EXPOSE_DEV_OTP=true`** (local); in prod (flag off) it **fails closed**
  (`success:false`, no code leak) so verification can't be bypassed. JWT Bearer. Onboarding at
  `/welcome` (topic selection). (Google Sign-In was removed; `GOOGLE_CLIENT_ID` is now unused and
  `/api/auth/config` returns an empty client id.)
- **Forgot password:** "Forgot password?" on the Login page opens a reset panel — email →
  `/send-otp {intent:"reset"}` (rejects unregistered emails with `notRegistered`, bouncing to Create
  Account) → `/reset-password {email, otp, newPassword}`. Verifies the OTP (password length checked
  **before** consuming the single-use code), sets the new SHA-256 hash, and **auto-signs-in** (returns
  a token). Same email dependency as sign-in: works locally (devOtp) and in prod once Resend is set.
- **Member types (6, from CSCEN model):** Academician, Professional, Researcher, Student,
  Industry Partner, Startup & Tech Partner. Phone + organization **optional**. Editable any time
  at **`/settings`** (change your "role"/member type, headline, org, phone, LinkedIn, bio, topics,
  mentorship) — all persisted to the DB.
- **Supply-chain verification:** members give a professional **headline + LinkedIn URL**; an AI
  relevance check (`AiModerationClient.isSupplyChainRelevant`) sets `verifyStatus`
  APPROVED/REJECTED, else PENDING. **Soft gate** — never blocks posting; admin reviews/overrides
  at `/admin` (`GET /api/admin/pending`, `POST /api/admin/users/{id}/verify`). NOTE: true LinkedIn
  scraping is not done (ToS/API-gated) — the check reads the headline text the user provides.
  APPROVED shows a ✅ Verified badge. (Chosen approach: "AI check + admin review".)
- **Q&A:** questions (title/body, optional image), comments (optional image), 11 domain tags,
  upvotes, share count, **accepted answers**.
- **Topic subscriptions:** members follow topics (at onboarding, in `/settings`, the Feed
  "🔔 Topics you follow" widget, or their own **Profile** "Favored SCM Domains" editor). New
  question in a followed topic → email to followers. `/settings` also has a **"Notify me for every
  new question"** toggle (`User.notifyAllQuestions`) that emails on *all* new questions regardless of
  topic. All email is via `NotificationService` (`@Async`, no-op until SMTP set). NOTE: partial
  profile updates (Welcome/Profile pages) omit `notifyAllQuestions`; the endpoint only overwrites it
  when the field is present, so those screens don't reset the preference.
- **In-app notifications + @mentions (`/notifications`, navbar 🔔):** `Notification` table +
  `NotificationController` (`GET /api/notifications` list+unread, `GET /unread` badge poll,
  `POST /read` mark-all). Created by `service/InAppNotifier` on: someone **answers** your question,
  **replies** to your answer, **accepts** your answer, or **@mentions** you (parsed from question/
  comment bodies, same `@([A-Za-z0-9_]{3,30})` regex as `components/RichText.tsx`). Wired in
  `QuestionController` (create/comment/accept); never notifies yourself; mentions dedupe against the
  answer/question author already notified. Navbar bell polls `/unread` (20s, visibility-gated) and
  refreshes on the `notif:refresh` window event the Notifications page fires after marking read.
  `RichText` linkifies `@username` → profile in question/answer/reply bodies. (No autocomplete yet.)
- **Jobs board (`/jobs`):** `Job` table + `JobController` (`/api/jobs` list+filter by `q`/`tag`/
  `type`, create, delete). Any active member posts openings (title/company/location/employmentType
  FULL_TIME|PART_TIME|CONTRACT|INTERNSHIP / SCM `tag` / salary / description / optional `applyUrl`);
  wordlist/AI moderated; owner/admin delete. Apply = external `applyUrl` or DM the poster. Public
  (viewable signed-out). Posting a job emails followers of its domain tag (`NotificationService.notifyNewJob`
  → `findTopicFollowers`, @Async, no-op when mail off). *(No Indeed sync yet — member/admin-posted.)*
- **Interactive SCM calculators (Tools tab in `/templates`):** client-side, no backend — Safety Stock &
  Reorder Point (with optional lead-time variability), EOQ, Demurrage/Detention estimator, Inventory
  Turns & Days of Supply. Pure math in `frontend/src/scm.ts` (incl. `normInv` = Acklam inverse-normal
  for the service-level Z), rendered by `components/Calculators.tsx`. Templates page has a Downloads |
  Calculators toggle. Zero cold-start / no external deps — the lowest-risk of the "indispensable" ideas.
- **AI agent "control tower" (`/agents`, navbar "🤖 Agents"):** a **12-agent** disruption-recovery decision
  engine (redesigned 2026-07-11 from the original 5-agent/7-signal demo — the blueprint the owner approved was
  "Phase 1 + live signals"). Flow: **intake** (pick a shipment + optional free-text description of what
  happened) → **12 specialist agents run over the ERP + live signals** (Cargo, Asset, Driver/Labour, Carrier
  Sourcing, Route/Network, Mode-Shift, Inventory/Fulfillment, External Conditions, Compliance, Cost/Finance,
  Risk, ESG) → engine generates **every applicable recovery strategy** (external carrier, own-fleet reposition/
  standby, on-site repair, reroute around a HIGH blockage, mode-shift rail, mode-shift air, alt-DC re-fulfill)
  → ranks by **risk-adjusted expected landed cost** → returns top-4 options + a recommendation + an **evidence
  trail** for human approval.
  - **Data layer:** `service/erp/ErpPort` (interface = the SAP-swap seam) backed by
    `service/erp/SimulatedErpAdapter` over an **enriched** `resources/erp/erp-mock.json` (now also carries
    cities+lat/long, lanes with distance/toll/ghat/rail/air, service points, DC stock, and richer shipment
    fields: value, penaltyPerHour/cap, perishable/tempControlled/shelfLife, customerTier, `eWayBillIssuedHoursAgo`).
    **The old `ErpService` was deleted** — inject `ErpPort`. (Deliberate call: `ErpPort` is backed by the
    simulated adapter, **not** raw Postgres tables — the seam is what delivers SAP-readiness; persisting still-
    simulated reference data would be churn for no analytical gain. A real `SapS4Adapter` implements `ErpPort` later.)
  - **Live signals** (`service/agents/ExternalSignals`, genuinely change between runs, all **fail-safe** to a
    modeled fallback offline): **Open-Meteo** current weather per city (free, **no key**, ~6s timeout), a
    date-driven **festival calendar** (2026 dates, IST), and a **computed e-way-bill clock** (India basis: 1
    day / 200 km). Verified live: Mumbai `drizzle (+0.5h)` tagged `<LIVE>`, e-way-bill `16h left of 24h`.
  - **Decision model** (`AgentOrchestrator`, still **deterministic numbers / LLM only narrates**): ETA is a
    distribution (mean+σ from route/weather/ghat); `Phi`/`phi` via an erf approximation give `P(on-time)` and
    `E[max(0,ETA−SLA)]`. `E[landed cost] = transport + penaltyEV + serviceRisk + stockoutEV + spoilageEV +
    co2Cost`. **`serviceRisk`** = `pFail · priorityWeight · value · 0.06` where `pFail = 1 − pOnTime·(rel/100)`
    — this is the key tuning: it stops a HIGH/CRITICAL load being gambled on a cheap-but-flaky mover (before
    it, SHP-1042 mis-recommended 52%-on-time rail to save ₹15k; now it correctly picks BlueDart). Rank by
    `E[landed] + λ·priorityWeight·σ_cost`; display score 30–95, order-consistent. Every factor is tagged
    **● LIVE / ◐ MODELED / ○ ROADMAP** so we never overclaim. Spoilage kills non-reefer movers for pharma
    (₹value·0.30). `FACTOR_CATALOG_SIZE=118` (surfaced selectively per run).
  - **AI narration:** `AgentAiClient` unchanged — **Anthropic** `claude-opus-4-8` if `ANTHROPIC_API_KEY`, else
    **Gemini** `gemini-2.5-flash` via `GEMINI_API_KEY` (thinking disabled, `responseMimeType=application/json`),
    else templated. Now narrates only the recommendation rationale + 3 agent headlines (Cargo/Conditions/Risk)
    — small payload, robust — folded onto the deterministic reports. `aiPowered`/`aiProvider` expose the mode.
  - **Demand side / distribution planning (added 2026-07-12, from the owner's `use case- vehicle breakdown.xlsx`).**
    The tower was truck-centric; a real distributor also has to answer *which consumer channels still get served*.
    A shipment now optionally carries `loadLines` (**SKU × destination city × sales channel** units), and when it
    does (`Shipment.hasDistributionPlan()`) the run switches to the distribution branch: **18 agents, 158 factors**.
    - New ERP master data: `skus`, `salesChannels` (**Qc/Ec/MT/D2c/TT** with `priority`, `maxDelayHrs` promise
      window, `penaltyPerUnitInr`), `departments` (13, for comms), `rdcs` (regional DCs with **per-SKU
      `daysOfCover` + `dailyDemandUnits`**, `replenishInDays`, outbound `legs`), `transporters` (rate/kg + transit).
    - `service/agents/DistributionPlanner` — the demand brain. **Key insight it encodes: a stranded consignment is
      not lost stock, it is *late* stock.** So: (1) **lendable** per RDC = `(daysOfCover − replenishInDays −
      RDC_SAFETY_DAYS) × dailyDemandUnits` (a depot must keep its own cover until its inbound lands); (2) allocate
      scarce units **greedily by avoided penalty** (Qcommerce 12h promise first … traditional trade 72h last),
      routing each line to the **fastest source that has that SKU**; (3) price it (short-haul freight + per-unit
      channel penalty + handling); (4) compute the **backfill** the lending RDC is owed.
    - New strategies for these loads: repair & complete, cross-dock to a **replacement transporter** (best of the
      master), **nearest RDC only**, **pool all RDCs**, and **HYBRID** (RDCs serve urgent channels now, the
      recovered load follows and repays the depots).
    - 6 new agents spliced in after Fulfillment: **Demand & Channel Planner, Multi-Echelon Sourcing, Allocation &
      Fair-Share, Replenishment & Backfill, Transporter Sourcing, Stakeholder Comms**. Each option carries a written
      `summary` + a `FulfilmentPlan` (per-city/per-channel/per-SKU fill matrix, sources, backfills); the run carries
      a `stakeholders[]` list (13 depts × urgency).
    - **Verified against the Excel case** (`SHP-7001`, Hyderabad→Salem+Coimbatore, 60,000 units, breakdown *at the
      Hoskote RDC gate*): RDC pool alone = **exactly 50% fill** (Qc 100%, Ec 100%, MT 36%, TT/D2c 0%); truck alone
      (repair *or* replacement) = **Qc 0%** — it cannot make the 12h Qcommerce promise, costing ₹243k in penalties;
      **HYBRID wins at 100% fill, zero penalty, ₹89,882** vs ₹235k–₹311k, and the backfill balances exactly
      (Hoskote lends 16,666 + Trichur 13,334 = 30,000, fully repaid from the truck's surplus).
    - **Tunable modelling assumptions** (the Excel doesn't specify them; both live in the dataset, not the code):
      RDC `dailyDemandUnits` (derived: the load = 3 days of cover ⇒ daily = units/3) and the **channel priority /
      penalty-per-unit** ranking (Qc > Ec > MT > D2c > TT). Change these to change the allocation.
  - Endpoints: `GET /api/agents/erp` (+aiEnabled/aiProvider), `POST /api/agents/run {shipmentId, disruption?}`
    → `AgentRun {scenario, signals[], agents[12|18], options[≤4], recommendation{…,evidence[]}, stakeholders[],
    factorsConsidered, aiPowered, aiProvider}`. Auth-gated (run needs an active user). Frontend `pages/Agents.tsx`:
    intake box + shipment picker, live-signal strip, progressive agent trace with per-factor chips (impact colour +
    source tag), option cards with expandable **cost breakdown** + **channel-fill chips** + written summary,
    evidence trail, and for distribution loads a **fulfilment plan** (sources / per-city channel matrix / per-SKU
    split / backfill) plus a **"Notify the network"** stakeholder panel.
    Still **no DB** — pure computation over the adapter + live HTTP, verifiable without Postgres. Decision-support only.
- **Templates & resources library (`/templates`):** `Template` table + `TemplateController`
  (`/api/templates` list+filter, multipart upload, `POST /{id}/download` records+returns the file,
  `POST /{id}/vote` toggles an upvote, delete). Members upload downloadable docs (PDF/Excel/Word/PPT/
  CSV/ZIP/images ≤15MB) with title/description/category; download **and** upvote counts tracked.
  **Upvotes reuse the `Vote` table** (new nullable `templateId` FK, mirroring the answer-vote pattern;
  `Json.template` carries `voteCount`/`viewerHasVoted`). Cards **open a detail modal** (full
  description + download + upvote); Jobs cards likewise open a JD modal (no upvote). Files go through
  `UploadStorage.saveFile`/`saveBytes` (local disk default, Azure Blob when configured, doc-ext allowlist).
  Public; wordlist/AI moderated; owner/admin delete.
- **Marketplace + Pricing — REMOVED (2026-07-06).** Pulled pre-launch until there are real users
  (monetization before liquidity = friction). Deleted: `MarketplaceController`, `Listing`,
  `MarketplaceLead`, their repos, `Json.listing`, `notifyNewListing`, the `/admin/plan` endpoint +
  Admin "subscriptions" card, and the `MessageController` free-tier contact **redaction** (which the
  old note flagged as hurting community DMs). Dormant leftovers kept to avoid migration churn:
  `User.plan`/`planExpiresAt`/`isPro()` fields + the `Listing`/`MarketplaceLead` tables (harmless,
  unused). Re-introduce behind a seam later (buyer-free / seller-pays was the intended model).
- **Reputation:** `5*questions + 2*comments + 10*upvotes_received + 15*accepted`. Leaderboard.
- **Profiles:** click any username → their questions, comments ("Commented on" tab), upvotes,
  headline/bio/LinkedIn, verified + mentor badges. Self sees an "Edit profile" → `/settings`.
- **Direct messages:** 1:1 chat with read tracking, unread badge. Polling (4s thread / 20s unread).
  New DM → email to the recipient (`NotificationService.notifyNewMessage`); this also covers the
  mentorship "connect" flow (which happens over DMs).
- **Email notifications (all via `MailService`/SMTP):** OTP sign-in, weekly digest, new followed-topic
  / all-questions question, and new direct message. Everything degrades gracefully to a no-op when
  SMTP is off. (These are separate from the in-app 🔔 notifications above, which are DB-backed and
  always on.)
- **Moderation:** wordlist + optional AI. Flag → auto-ban at 5 flags. Admin dashboard.
- **Admin** (`ADMIN_EMAILS`): delete any question/comment, view flagged users, ban/unban,
  verification review, **seed starter content** (`POST /api/admin/seed`), send test digest.
  Seed uses **replace semantics**: each run deletes the legacy prototype accounts
  (`demo_user`/`user_two`) + prior seed members (content cascades away) then inserts 5 realistic
  members (Priya Sharma, Daniel Okafor, Rahul Verma, Ana Ferreira, Meera Iyer) with 5 practitioner-
  voice Q&As + 12 answers across Digital&AI / Demand Planning / Warehousing / Procurement / Careers.
  Safe to re-run. Seed text is ASCII-only (avoids cp1252 mojibake like the old "K�rber").
- **Events & webinars** (`/events`): admins create, members RSVP; upcoming/past sections.
- **Mentorship** (`/mentorship`): opt in as mentor/mentee at onboarding; browse + connect via DMs.
- **Weekly digest email:** top-5 questions of the week to all non-banned users, Mondays.
- **UI:** animated landing page (signed-out only, with feature grid), animated login/feed/navbar,
  **calm layered background** — floating aurora blobs (`.app-aurora`, `-z-20`) + a slow drifting
  connected-node particle canvas (`components/BackgroundNetwork.tsx`, `-z-10`, mounted globally in
  `App.tsx`), both respecting `prefers-reduced-motion`. **Mobile-responsive navbar (hamburger at
  `< lg`)**, dark mode.

---

## 5. File map

```
Dockerfile                      All-in-one image for Railway (node build → maven build → temurin-21-jre serves SPA from ./public)
docker-compose.yml              Local: postgres + backend(:4000) + frontend(:3000)
README.md                       Public-facing docs (stack, Railway steps, env table, API table)

backend/
  pom.xml                       Spring Boot 3.4.1, Java 21; jjwt, google-api-client, anthropic-java, starter-mail, azure-storage-blob
                                (NOTE: Java 21, not 25 — SB 3.4.1's repackage can't read Java 25 bytecode; a Java-25 bump needs SB 3.5.x+)
  Dockerfile                    Maven multi-stage (for compose/k8s)
  src/main/resources/
    application.properties       server.port=${PORT:4000}, ddl-auto=none, sql.init.mode=always, quoted identifiers, UTC
    schema.sql                   IDEMPOTENT migration — see §7. Safe from any prior version.
  src/main/java/com/cscen/forum/
    ForumApplication.java
    config/    DataSourceConfig (parses postgres:// URLs), WebConfig (uploads + SPA fallback), StorageConfig (picks Local vs Azure UploadStorage), GlobalExceptionHandler ({"error":msg})
    model/     User, Question, Comment, Vote, Message, ModerationEvent, Event, EventRsvp, Notification, Job, Template — UUID string ids, static create() factories (Listing/MarketplaceLead deleted; their tables remain dormant)
    repo/      *Repository — native leaderboard query, JPQL search, mentor/flag finders, findTopicFollowers, findByVerifyStatus, NotificationRepository (markAllRead), JobRepository.search, TemplateRepository.search
    security/  JwtService, CurrentUser (optionalUserId/requireUserId/requireUser/requireActiveUser/requireAdmin)
    service/   Json (DTO shaping incl. notification()/job()/template()), QuestionService (TAGS×11, toJson, tagLabel), ModerationService (wordlist||AI),
               AiModerationClient (Anthropic SDK — moderation + isSupplyChainRelevant verify),
               MailService (Resend HTTP API preferred + JavaMail SMTP fallback, no-op until configured), DigestService (@Scheduled + manual test),
               NotificationService (@Async new-question/DM emails), InAppNotifier (in-app 🔔: answers/replies/accepts/mentions), SeedService (idempotent starter content),
               UploadStorage (interface: saveImage + saveFile) + LocalUploadStorage (default, ./uploads) + AzureBlobUploadStorage (env-gated object storage),
               erp/ErpPort (SAP-swap interface; + skus/salesChannels/departments/rdcs/transporters/loadLines) + erp/SimulatedErpAdapter,
               agents/ExternalSignals (LIVE Open-Meteo weather + festival calendar + computed e-way-bill clock, fail-safe),
               agents/DistributionPlanner (demand side: RDC lendable capacity, penalty-greedy channel allocation, fill matrix, backfill),
               AgentAiClient (Anthropic|Gemini completion, fallback),
               AgentOrchestrator (12-agent risk-adjusted expected-landed-cost engine; 18 agents + fulfilment plan when the load has SKU x channel lines)
    web/       AuthController (google/profile/me — profile now takes topics/linkedin/headline/bio + runs verify),
               QuestionController (create/comment/accept fire in-app notifications + mentions), UserController, LeaderboardController, MessageController,
               NotificationController (/api/notifications), JobController (/api/jobs), TemplateController (/api/templates),
               AgentController (/api/agents — control-tower ERP snapshot + run),
               AdminController (flagged/ban/verify/pending/seed/digest), EventController, MentorshipController, HealthController, ApiException

frontend/
  vite.config.ts, tsconfig.json, tailwind config (in package/index.css component classes)
  src/
    App.tsx                     Routes (+/jobs +/templates +/notifications; /marketplace +/pricing removed); `/` = user ? Feed : Landing; aurora background
    api.ts, auth.tsx (updateUser), theme.ts, types.ts (+Notification/Job/Template; Listing removed), memberTypes.ts (6), tags.ts (11), time.ts,
    scm.ts (pure calculator math: normInv/safetyStock/eoq/demurrage/inventoryTurns),
    poll.ts (startVisibilityInterval — polls only while tab visible; used by Navbar unread + bell + MessageThread)
    components/  Navbar (responsive + hamburger + 🔔 bell), QuestionCard, VoteButton, ShareButton, MemberTypeBadge, TopicPicker, RichText (@mention linkify), Calculators (SCM Tools tab)
    pages/       Landing, Login, Feed (+follow-topics widget), Ask, QuestionDetail, Profile (+verify/badges),
                 Leaderboard, Events, Mentorship, Jobs, Templates, Agents (AI control tower), Notifications, Welcome (+topics/linkedin), Settings,
                 Messages, MessageThread, Admin (+verify review + seed/digest tools)

k8s/  00-namespace, 01-secrets (dev placeholders), 02-postgres, 03-backend (image :vN), 04-frontend (image :vN)
```

---

## 6. Environment variables

| Var | Required | Default / notes |
|---|---|---|
| `DATABASE_URL` | yes | `postgresql://…`. Railway: `${{Postgres.DATABASE_URL}}`. |
| `PORT` | yes on Railway | `4000`. **Pin it** — Railway's domain targets 4000; injected 8080 caused a 502. |
| `JWT_SECRET` | yes | ≥32 bytes → raw key (session continuity). Dev: `change-me-in-production`. |
| `GOOGLE_CLIENT_ID` | no | **Unused now** (Google Sign-In removed). Safe to drop. |
| `ADMIN_EMAILS` | no | comma-separated; default `vinay.menon2707@gmail.com`. Editable parameter. |
| `ANTHROPIC_API_KEY` | no | enables AI moderation. Unset → wordlist only (fails open). |
| `ANTHROPIC_MODEL` | no | default `claude-opus-4-8`. Set `claude-haiku-4-5` for cheaper/faster. |
| `RESEND_API_KEY` | recommended (prod) | **Preferred email backend.** Sends via the Resend HTTP API over 443, so it works on Railway (which **blocks outbound SMTP** ports 25/465/587 — Gmail SMTP times out there). Set this + `MAIL_FROM` and all email works. Takes priority over SMTP when both are set. |
| `MAIL_FROM` | with Resend | Sender address. For Resend it **must be a verified-domain address** (or `onboarding@resend.dev` while testing) or Resend returns 403. Falls back to `SMTP_FROM` when unset. |
| `SMTP_HOST`/`_PORT`/`_USER`/`_PASS`/`_FROM` | fallback | SMTP backend for hosts that allow it. Powers the same email set. **On Railway SMTP is blocked → use `RESEND_API_KEY` instead.** |
| `EXPOSE_DEV_OTP` | no | **Default `false`.** When `true` (local `docker-compose` only), an OTP that couldn't be emailed is returned as `devOtp` in the API response for testing. **Never set in prod** — with it off, if email fails the API **fails closed** (no code leak) instead of exposing the OTP. |
| `APP_URL` | no | used in email links. Prod = the Railway URL. |
| `AZURE_STORAGE_CONNECTION_STRING` | no | **Unset → uploads go to local `./uploads` disk** (fine for one instance/Railway). Set it → images upload to Azure Blob instead (`AzureBlobUploadStorage`), returning absolute blob URLs. This is the config-only switch that unblocks running multiple replicas (local disk isn't shared). Relaxed-binds to `azure.storage.connection-string`. |
| `AZURE_STORAGE_CONTAINER` | no | default `forum-uploads`. Blob container name; auto-created (public-blob read) on first boot when Azure storage is enabled. |

> **Email on Railway = Resend, not SMTP.** Railway (like most PaaS) **blocks outbound SMTP ports**,
> so `smtp.gmail.com:587` fails with a `MailConnectException` connection timeout — no App Password
> fixes it. Use `RESEND_API_KEY` + `MAIL_FROM` (Resend sends over HTTPS/443). `MailService` prefers
> Resend when the key is set and falls back to SMTP otherwise. **Local Gmail SMTP gotcha (still
> applies to compose):** `SMTP_USER=…@gmail.com` needs a **16-char App Password** for `SMTP_PASS`
> (Google → 2-Step Verification → App passwords), not the normal password (else `535` auth failure).
> Compose auto-loads the root `.env`; restart the backend after changing it.
>
> **OTP fail-open is now gated.** When mail can't send, the API only returns a `devOtp` if
> `EXPOSE_DEV_OTP=true` (local); in prod (flag off) it **fails closed** so email verification can't be
> bypassed. (Before this fix, prod leaked the OTP in the response whenever SMTP wasn't working.)

**Two optional switches to turn on later (in Railway Variables):**
1. AI moderation → set `ANTHROPIC_API_KEY`.
2. Weekly digest → set `SMTP_*` + `APP_URL`, then test via `POST /api/admin/digest/test` (admin).

> **Scaling prep (done, dormant until Azure).** Owner plans to move to Azure when DAU > ~2k. Two
> changes landed ahead of that so the migration is low-friction: (1) **uploads are pluggable** —
> `UploadStorage` is an interface; local disk is the default and Azure Blob turns on purely by
> setting `AZURE_STORAGE_CONNECTION_STRING` (no code change), which is the one thing that was
> blocking >1 replica; (2) **frontend polling pauses when the tab is hidden** (`frontend/src/poll.ts`
> `startVisibilityInterval`, used by the Navbar unread poll + MessageThread), cutting idle API load
> from backgrounded tabs. The Azure path is **untested** (no account yet) — only the local fallback
> is verified; validate the blob upload + public-URL assumptions when the account exists.

---

## 7. schema.sql — the critical file

Idempotent, runs on every boot. **Order matters:** all `ADD COLUMN IF NOT EXISTS` run BEFORE
any `ALTER … TYPE`/`SET DEFAULT`, so a DB from *any* prior app version migrates cleanly.
This was the root cause of a k8s CrashLoopBackOff (`column "role" does not exist`) — the old
DB predated moderation columns and the ALTER ran against a missing column. Fixed in `b39a116`.
If you add a column, add it as a guarded `ADD COLUMN IF NOT EXISTS` and never assume it exists
before adding it.

Tables: `User, Question, Comment, Vote, Message, ModerationEvent, Event, EventRsvp, Notification,
Job, Template` (+ dormant `Listing, MarketplaceLead` — marketplace removed but tables kept to avoid
migration churn). `User` still carries `plan`/`planExpiresAt` (guarded ADD COLUMN, default 'FREE',
now unused). `User` grew `topics, linkedinUrl, headline, bio, verifyStatus` (all guarded ADD COLUMN).
Newest additions are fresh `CREATE TABLE IF NOT EXISTS` blocks at the end of `schema.sql`:
`Notification` (userId/actorId/type/questionId/commentId/text/readAt), `Job` (title/company/location/
employmentType/tag/description/applyUrl/salary/authorId), `Template` (title/description/category/
fileUrl/fileName/fileType/downloadCount/authorId). `Vote` gained a nullable `templateId` FK (+ unique
`(userId,templateId)` index) so template upvotes reuse the same table as question/answer votes — the
ALTER sits after the `Template` CREATE so the FK resolves. `spring.servlet.multipart.max-file-size`
bumped to 15MB for template uploads.

---

## 8. Deploy / release procedure

- **Railway:** push to `master` → auto-build root `Dockerfile` → deploy. Verify with
  `/api/health` (`{"status":"ok"}`) and check the content-hashed bundle name changed
  (`index-XXXX.js`) to confirm the new build actually rolled out. **Rollout gotcha:** the new
  static bundle starts being served ~40–60s *before* the backend fully accepts requests, so an API
  check fired the instant the bundle hash changes can return a transient `500 {"error":"Something
  went wrong"}` (seen twice). **Wait ~60s after the hash change before trusting API checks** — it
  clears on its own; it is not a code bug.
- **k8s (local):** mutable tags (`:latest`/`:v3`) got stale on kind nodes **twice**. Always
  **bump the versioned tag** (`vN`) in `k8s/03-backend.yaml` + `04-frontend.yaml`, rebuild,
  `docker tag … :vN`, `kubectl apply`, wait for rollout. Current tag: **v4**.
- **Compose:** `docker compose build backend && docker compose up -d`.

---

## 9. Testing / getting a token

**Easiest:** drive the real auth flow. `POST /api/auth/send-otp {email}` — when SMTP is off or
Gmail auth fails it returns a `devOtp` in the JSON — then `POST /api/auth/register {…,otp}` hands
back a JWT. No Google, no SQL. (Use an `@example.com` email so no real mail is attempted; clean the
row up afterwards.)

**Or** mint HS256 JWTs by hand (sub = userId). Key rule = §3 (secret <32 bytes → `sha256(secret)`
as the HMAC key). **The old `demo_user`/`user_two` prototype accounts are now DELETED by the
seed** (their content looked fake). To bootstrap an admin, insert a throwaway one via SQL and
mint a token for its id, e.g.:
```sql
INSERT INTO "User" (id,email,username,role,"memberType","verifyStatus")
VALUES ('smoketestadmin0000000000001','smoke@test.local','smoke_admin','ADMIN','PROFESSIONAL','APPROVED')
ON CONFLICT (id) DO UPDATE SET role='ADMIN';
```
Then `sub=smoketestadmin0000000000001`. Tokens have a 2h TTL — re-mint if you get blanket 401s.
The 5 real seed members (priya_sharma, daniel_okafor, rahul_verma, ana_ferreira, meera_iyer) have
no `passwordHash`, so nobody can log in as them — they're content authors only.

---

## 10. Environment gotchas (Windows / this machine)

- Shell is **PowerShell 5.1**; Bash tool also available. PowerShell mangles embedded double
  quotes → for git commits use `git commit -F <file>` (write the message to a scratch file).
- Windows `curl -F "image=@/tmp/…"` can't read Git Bash `/tmp` paths → use Windows scratchpad paths.
- Don't `print("▲")` in Python test scripts → cp1252 `UnicodeEncodeError`.
- `sed` first-match extraction when pulling ids from JSON (greedy match grabbed the wrong id once).

---

## 11. Notable fixes (history, so we don't repeat them)

- `b39a116` — made `schema.sql` idempotent from any prior version (fixed k8s `role`-column crash).
- Railway 502 — injected `PORT=8080` vs domain on 4000 → pinned `PORT=4000`.
- Stale `:latest` on kind nodes (×2) → versioned image tags.
- Wrong Google import (`openidconnect` → `oauth2`).
- TS `moduleResolution` — frontend is `bundler`; the nodenext pairing was for a different context.

---

## 12. Working agreements

- Repo is public → no real secrets in commits, ever.
- `ADMIN_EMAILS` stays a parameter (don't hard-code admin).
- Preserve production data on backend changes (exact quoted-camelCase mapping, guarded migrations).
- One paid service on Railway → keep the single all-in-one image (Spring serves the SPA).
- Commit messages via `git commit -F`; end with the `Co-Authored-By` trailer.
- **Update this file when the project changes.**
