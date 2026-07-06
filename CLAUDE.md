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
  `/register`, `/login`. OTP is a 6-digit code, single-use, **5-min TTL** (`otpStorage`, in-memory),
  emailed via `MailService`. When mail can't send, the response includes a `devOtp` **only if
  `EXPOSE_DEV_OTP=true`** (local); in prod (flag off) it **fails closed** (`success:false`, no code
  leak) so verification can't be bypassed. JWT Bearer. Onboarding at `/welcome` (topic selection).
  (Google Sign-In was removed; `GOOGLE_CLIENT_ID` is now unused and `/api/auth/config` returns an
  empty client id.)
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
- **Marketplace (`/marketplace`):** OFFER/SEEK listings across 5 categories (Warehouse, Transport,
  Equipment, Services, General Logistics) with location/size/price/photo; contact seller via DMs.
  Backed by the `Listing` table; wordlist/AI moderated; owner/admin delete.
- **Marketplace monetization (TradeIndia/IndiaMART-style — buyers free, sellers pay):**
  `User.plan` = FREE | PRO (+ `planExpiresAt`; `User.isPro()`). **Contact gating** —
  `POST /api/listings/{id}/contact` records a `MarketplaceLead` and returns the seller's username
  (client then opens the DM); FREE members are capped at **5 distinct supplier contacts / rolling
  30 days** (`FREE_MONTHLY_CONTACTS`), PRO is unlimited (re-contacting a listing you already reached
  is free). **Anti-disintermediation:** `MessageController` redacts phone/email in DMs for FREE
  senders (masked as "[contact hidden — upgrade to Pro]"); PRO can share freely. NOTE: redaction is
  global for free users, not marketplace-only — dial back if it hurts community DMs. **Pro perks:**
  ⭐ Pro Supplier badge (`Json` exposes `pro` on author/public/private), priority placement (PRO
  listings sort first in `/api/listings`), and a **lead inbox** (`GET /api/listings/leads` — count
  for free sellers, who+what for PRO). **Pricing page** `/pricing` (₹0 Member vs ₹1,999/mo Pro);
  Pro CTA sends an upgrade request via `/api/contact`. **Payments are manual for now** — admin grants
  via `POST /api/admin/plan {username, plan, months}` (Admin page "Marketplace subscriptions" card);
  wire Razorpay behind this seam later (buyer free / seller pays is the profit model).
- **Reputation:** `5*questions + 2*comments + 10*upvotes_received + 15*accepted`. Leaderboard.
- **Profiles:** click any username → their questions, comments ("Commented on" tab), upvotes,
  headline/bio/LinkedIn, verified + mentor badges. Self sees an "Edit profile" → `/settings`.
- **Direct messages:** 1:1 chat with read tracking, unread badge. Polling (4s thread / 20s unread).
  New DM → email to the recipient (`NotificationService.notifyNewMessage`); this also covers the
  mentorship "connect" flow (which happens over DMs).
- **Email notifications (all via `MailService`/SMTP):** OTP sign-in, weekly digest, new followed-topic
  / all-questions question, new marketplace listing (`notifyNewListing` → all non-banned members),
  and new direct message. Everything degrades gracefully to a no-op when SMTP is off.
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
  Dockerfile                    Maven multi-stage (for compose/k8s)
  src/main/resources/
    application.properties       server.port=${PORT:4000}, ddl-auto=none, sql.init.mode=always, quoted identifiers, UTC
    schema.sql                   IDEMPOTENT migration — see §7. Safe from any prior version.
  src/main/java/com/cscen/forum/
    ForumApplication.java
    config/    DataSourceConfig (parses postgres:// URLs), WebConfig (uploads + SPA fallback), StorageConfig (picks Local vs Azure UploadStorage), GlobalExceptionHandler ({"error":msg})
    model/     User, Question, Comment, Vote, Message, ModerationEvent, Event, EventRsvp, Listing — UUID string ids, static create() factories
    repo/      *Repository — native leaderboard query, JPQL search, mentor/flag finders, findTopicFollowers, findByVerifyStatus, ListingRepository.search
    security/  JwtService, CurrentUser (optionalUserId/requireUserId/requireUser/requireActiveUser/requireAdmin)
    service/   Json (DTO shaping incl. listing()), QuestionService (TAGS×11, toJson, tagLabel), ModerationService (wordlist||AI),
               AiModerationClient (Anthropic SDK — moderation + isSupplyChainRelevant verify),
               MailService (Resend HTTP API preferred + JavaMail SMTP fallback, no-op until configured), DigestService (@Scheduled + manual test),
               NotificationService (@Async new-question topic emails), SeedService (idempotent starter content),
               UploadStorage (interface) + LocalUploadStorage (default, ./uploads) + AzureBlobUploadStorage (env-gated object storage)
    web/       AuthController (google/profile/me — profile now takes topics/linkedin/headline/bio + runs verify),
               QuestionController (create fires notifications), UserController, LeaderboardController, MessageController,
               AdminController (flagged/ban/verify/pending/seed/digest), EventController, MentorshipController,
               MarketplaceController (/api/listings CRUD+filter), HealthController, ApiException

frontend/
  vite.config.ts, tsconfig.json, tailwind config (in package/index.css component classes)
  src/
    App.tsx                     Routes (+/settings +/marketplace); `/` = user ? Feed : Landing; aurora background
    api.ts, auth.tsx (updateUser), theme.ts, types.ts (+Listing), memberTypes.ts (6), tags.ts (11), marketplace.ts (5 cats/2 kinds), time.ts,
    poll.ts (startVisibilityInterval — polls only while tab visible; used by Navbar unread + MessageThread)
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

Tables: `User, Question, Comment, Vote, Message, ModerationEvent, Event, EventRsvp, Listing,
MarketplaceLead`. `User` also grew `plan` (guarded ADD COLUMN, default 'FREE') + `planExpiresAt`.
`MarketplaceLead` (id/listingId/sellerId/buyerId/createdAt) is a fresh CREATE TABLE with a unique
(listingId, buyerId) index.
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
