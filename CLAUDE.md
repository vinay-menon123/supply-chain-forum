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
- Google verify: `com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier`
  (google-api-client 2.7.0). **Not** the `openidconnect` package.
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

- **Auth:** Google Sign-In only (verified emails). JWT Bearer. Onboarding at `/welcome`.
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
- **Topic subscriptions:** members follow topics (at onboarding, in `/settings`, or the Feed
  "🔔 Topics you follow" widget). New question in a followed topic → email to followers
  (`NotificationService`, `@Async`, no-op until SMTP set).
- **Marketplace (`/marketplace`):** OFFER/SEEK listings across 5 categories (Warehouse, Transport,
  Equipment, Services, General Logistics) with location/size/price/photo; contact seller via DMs.
  Backed by the `Listing` table; wordlist/AI moderated; owner/admin delete.
- **Reputation:** `5*questions + 2*comments + 10*upvotes_received + 15*accepted`. Leaderboard.
- **Profiles:** click any username → their questions, comments ("Commented on" tab), upvotes,
  headline/bio/LinkedIn, verified + mentor badges. Self sees an "Edit profile" → `/settings`.
- **Direct messages:** 1:1 chat with read tracking, unread badge. Polling (4s thread / 20s unread).
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
  aurora background, **mobile-responsive navbar (hamburger menu at `< lg`)**, dark mode.

---

## 5. File map

```
Dockerfile                      All-in-one image for Railway (node build → maven build → temurin-21-jre serves SPA from ./public)
docker-compose.yml              Local: postgres + backend(:4000) + frontend(:3000)
README.md                       Public-facing docs (stack, Railway steps, env table, API table)

backend/
  pom.xml                       Spring Boot 3.4.1, Java 21; jjwt, google-api-client, anthropic-java, starter-mail
  Dockerfile                    Maven multi-stage (for compose/k8s)
  src/main/resources/
    application.properties       server.port=${PORT:4000}, ddl-auto=none, sql.init.mode=always, quoted identifiers, UTC
    schema.sql                   IDEMPOTENT migration — see §7. Safe from any prior version.
  src/main/java/com/cscen/forum/
    ForumApplication.java
    config/    DataSourceConfig (parses postgres:// URLs), WebConfig (uploads + SPA fallback), GlobalExceptionHandler ({"error":msg})
    model/     User, Question, Comment, Vote, Message, ModerationEvent, Event, EventRsvp, Listing — UUID string ids, static create() factories
    repo/      *Repository — native leaderboard query, JPQL search, mentor/flag finders, findTopicFollowers, findByVerifyStatus, ListingRepository.search
    security/  JwtService, CurrentUser (optionalUserId/requireUserId/requireUser/requireActiveUser/requireAdmin)
    service/   Json (DTO shaping incl. listing()), QuestionService (TAGS×11, toJson, tagLabel), ModerationService (wordlist||AI),
               AiModerationClient (Anthropic SDK — moderation + isSupplyChainRelevant verify),
               MailService (shared JavaMail wrapper, no-op until SMTP), DigestService (@Scheduled + manual test),
               NotificationService (@Async new-question topic emails), SeedService (idempotent starter content), UploadStorage
    web/       AuthController (google/profile/me — profile now takes topics/linkedin/headline/bio + runs verify),
               QuestionController (create fires notifications), UserController, LeaderboardController, MessageController,
               AdminController (flagged/ban/verify/pending/seed/digest), EventController, MentorshipController,
               MarketplaceController (/api/listings CRUD+filter), HealthController, ApiException

frontend/
  vite.config.ts, tsconfig.json, tailwind config (in package/index.css component classes)
  src/
    App.tsx                     Routes (+/settings +/marketplace); `/` = user ? Feed : Landing; aurora background
    api.ts, auth.tsx (updateUser), theme.ts, types.ts (+Listing), memberTypes.ts (6), tags.ts (11), marketplace.ts (5 cats/2 kinds), time.ts
    components/  Navbar (responsive + hamburger), QuestionCard, VoteButton, ShareButton, MemberTypeBadge, TopicPicker
    pages/       Landing, Login, Feed (+follow-topics widget), Ask, QuestionDetail, Profile (+verify/badges),
                 Leaderboard, Events, Mentorship, Marketplace, Welcome (+topics/linkedin), Settings,
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
| `GOOGLE_CLIENT_ID` | yes | OAuth Web Client ID. **Non-secret** (public by design). |
| `ADMIN_EMAILS` | no | comma-separated; default `vinay.menon2707@gmail.com`. Editable parameter. |
| `ANTHROPIC_API_KEY` | no | enables AI moderation. Unset → wordlist only (fails open). |
| `ANTHROPIC_MODEL` | no | default `claude-opus-4-8`. Set `claude-haiku-4-5` for cheaper/faster. |
| `SMTP_HOST`/`_PORT`/`_USER`/`_PASS`/`_FROM` | no | enables weekly digest. Unset → digest disabled. |
| `APP_URL` | no | used in digest email links. Prod = the Railway URL. |

**Two optional switches to turn on later (in Railway Variables):**
1. AI moderation → set `ANTHROPIC_API_KEY`.
2. Weekly digest → set `SMTP_*` + `APP_URL`, then test via `POST /api/admin/digest/test` (admin).

---

## 7. schema.sql — the critical file

Idempotent, runs on every boot. **Order matters:** all `ADD COLUMN IF NOT EXISTS` run BEFORE
any `ALTER … TYPE`/`SET DEFAULT`, so a DB from *any* prior app version migrates cleanly.
This was the root cause of a k8s CrashLoopBackOff (`column "role" does not exist`) — the old
DB predated moderation columns and the ALTER ran against a missing column. Fixed in `b39a116`.
If you add a column, add it as a guarded `ADD COLUMN IF NOT EXISTS` and never assume it exists
before adding it.

Tables: `User, Question, Comment, Vote, Message, ModerationEvent, Event, EventRsvp, Listing`.
`User` grew `topics, linkedinUrl, headline, bio, verifyStatus` (all guarded ADD COLUMN). `Listing`
is a fresh `CREATE TABLE IF NOT EXISTS` (kind/category/title/description/location/price/size/imageUrl/authorId).

---

## 8. Deploy / release procedure

- **Railway:** push to `master` → auto-build root `Dockerfile` → deploy. Verify with
  `/api/health` (`{"status":"ok"}`) and check the content-hashed bundle name changed
  (`index-XXXX.js`) to confirm the new build actually rolled out.
- **k8s (local):** mutable tags (`:latest`/`:v3`) got stale on kind nodes **twice**. Always
  **bump the versioned tag** (`vN`) in `k8s/03-backend.yaml` + `04-frontend.yaml`, rebuild,
  `docker tag … :vN`, `kubectl apply`, wait for rollout. Current tag: **v4**.
- **Compose:** `docker compose build backend && docker compose up -d`.

---

## 9. Testing without Google

Mint HS256 JWTs by hand (sub = userId). Key rule = §3 (secret <32 bytes → `sha256(secret)`
as the HMAC key). **The old `demo_user`/`user_two` prototype accounts are now DELETED by the
seed** (their content looked fake). To test without Google, insert a throwaway admin via SQL and
mint a token for its id, e.g.:
```sql
INSERT INTO "User" (id,email,username,role,"memberType","verifyStatus")
VALUES ('smoketestadmin0000000000001','smoke@test.local','smoke_admin','ADMIN','PROFESSIONAL','APPROVED')
ON CONFLICT (id) DO UPDATE SET role='ADMIN';
```
Then `sub=smoketestadmin0000000000001`. Tokens have a 2h TTL — re-mint if you get blanket 401s.
The 5 real seed members (priya_sharma, daniel_okafor, rahul_verma, ana_ferreira, meera_iyer) have
no `googleId`, so nobody can log in as them — they're content authors only.

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
