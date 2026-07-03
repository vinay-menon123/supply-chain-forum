# Supply Chain Forum

A Stack Overflow / Quora–style forum: users sign up, ask questions (text + optional image), comment on them, and share links.

## Tech Stack

| Layer     | Tech                                                        |
| --------- | ----------------------------------------------------------- |
| Frontend  | React 18, TypeScript, Vite, Tailwind CSS, React Router      |
| Backend   | Node.js 22, Express, TypeScript, Prisma ORM, Zod, Multer    |
| Database  | PostgreSQL 16                                               |
| Auth      | Google Sign-In (verified emails) + JWT Bearer sessions      |
| Deploy    | Docker (multi-stage builds), docker-compose, Kubernetes     |

## Google Sign-In Setup (required for login)

Accounts are created exclusively through Google Sign-In, so every email is
Google-verified. One-time setup:

1. Open [Google Cloud Console → APIs & Services → Credentials](https://console.cloud.google.com/apis/credentials)
   (create a project if you don't have one).
2. Configure the OAuth consent screen (External, add yourself as a test user).
3. **Create Credentials → OAuth client ID → Web application**, and add your
   origins under **Authorized JavaScript origins**:
   - `http://localhost:3000` (docker compose)
   - `http://localhost:8080` (Kubernetes)
   - `http://localhost:5173` (local dev)
4. Copy the Client ID (`xxxx.apps.googleusercontent.com`) and provide it to the backend:
   - **compose**: create a `.env` file next to `docker-compose.yml` with `GOOGLE_CLIENT_ID=...`
   - **Kubernetes**: paste it into `k8s/01-secrets.yaml` and re-apply
   - **local dev**: set it in `backend/.env`

The frontend fetches the client id from `/api/auth/config` at runtime, so no
frontend rebuild is needed when it changes. Until it's set, the login page
shows a setup notice.

## Project Structure

```
├── backend/            Express REST API
│   ├── prisma/         Prisma schema (User, Question, Comment)
│   ├── src/
│   │   ├── routes/     auth.ts, questions.ts
│   │   ├── middleware/ JWT auth middleware
│   │   └── upload.ts   Multer image upload config
│   └── Dockerfile
├── frontend/           React SPA (served by nginx in prod, proxies /api → backend)
│   ├── src/pages/      Feed, QuestionDetail, Ask, Login, Register
│   └── Dockerfile
├── docker-compose.yml  One-command local stack
└── k8s/                Kubernetes manifests for Docker Desktop
```

## Run with Docker Compose (quickest)

```sh
docker compose up -d --build
```

- App: http://localhost:3000
- API: http://localhost:4000/api/health

The backend runs `prisma db push` on boot, so the schema is created automatically.

## Run on the Docker Desktop Kubernetes cluster

Docker Desktop's Kubernetes shares the local image store, so build the images first:

```sh
docker compose build          # produces forum-backend:latest and forum-frontend:latest
kubectl apply -f k8s/
kubectl get pods -n forum -w  # wait until all pods are Ready
```

- App: http://localhost:8080 (the frontend Service is `type: LoadBalancer`, which Docker Desktop binds to localhost)

Tear down with `kubectl delete namespace forum`.

> Change `JWT_SECRET` / `POSTGRES_PASSWORD` in `k8s/01-secrets.yaml` (and docker-compose.yml) before any real deployment.

## Deploy to Railway (~$5/mo Hobby plan)

The root [Dockerfile](Dockerfile) builds an all-in-one image (Express serves the
frontend build directly), so Railway needs just **one service + Postgres + a volume**:

1. Push this repo to GitHub, then in [Railway](https://railway.com): **New Project → Deploy from GitHub repo**. Railway auto-detects the root Dockerfile.
2. Add a database: right-click the canvas → **Database → PostgreSQL**.
3. On the app service → **Variables**:
   - `DATABASE_URL` = `${{Postgres.DATABASE_URL}}` (reference, not a literal)
   - `JWT_SECRET` = long random string (`openssl rand -hex 32`)
   - `GOOGLE_CLIENT_ID` = your OAuth Web Client ID
4. Right-click the app service → **Attach Volume**, mount path `/app/uploads` (keeps uploaded images across deploys).
5. Service → **Settings → Networking → Generate Domain** → you get `https://<name>.up.railway.app`.
6. Add that URL to **Authorized JavaScript origins** on your Google OAuth client.

Every `git push` then redeploys automatically. (No GitHub? `npm i -g @railway/cli`, then `railway login`, `railway init`, `railway up` from the project root.)

## Local Development (hot reload)

```sh
# 1. Start just the database
docker compose up -d db

# 2. Backend (http://localhost:4000)
cd backend
copy .env.example .env
npm install
npx prisma db push
npm run dev

# 3. Frontend (http://localhost:5173, proxies /api to :4000)
cd frontend
npm install
npm run dev
```

## API Overview

| Method | Path                          | Auth | Description                          |
| ------ | ----------------------------- | ---- | ------------------------------------ |
| GET    | `/api/auth/config`            | –    | Runtime config → `{ googleClientId }` |
| POST   | `/api/auth/google`            | –    | Verify Google ID token → `{ token, user }` |
| GET    | `/api/auth/me`                | ✅   | Current user                         |
| GET    | `/api/questions?q=&page=`     | –    | List/search questions (paginated; includes voteCount/viewerHasVoted) |
| POST   | `/api/questions`              | ✅   | Ask question (`multipart/form-data`: title, body, image?) |
| GET    | `/api/questions/:id`          | –    | Question detail with comments        |
| POST   | `/api/questions/:id/vote`     | ✅   | Toggle upvote → `{ voteCount, viewerHasVoted }` |
| POST   | `/api/questions/:id/comments` | ✅   | Add a comment (`multipart/form-data`: body, image?) |
| DELETE | `/api/questions/:id`          | ✅   | Delete question (author or admin)    |
| DELETE | `/api/questions/:id/comments/:commentId` | ✅ | Delete comment (author or admin) |
| POST   | `/api/questions/:id/share`    | –    | Increment share counter              |
| GET    | `/api/users/:username`        | –    | Public profile: stats + questions    |
| GET    | `/api/messages`               | ✅   | Conversation list with unread counts |
| GET    | `/api/messages/unread`        | ✅   | Total unread message count           |
| GET    | `/api/messages/:username`     | ✅   | Thread with a user (marks as read)   |
| POST   | `/api/messages/:username`     | ✅   | Send a direct message                |
| GET    | `/api/admin/flagged`          | 🛡️   | Flagged/banned users (admin only)    |
| POST   | `/api/admin/users/:id/ban`    | 🛡️   | Ban or unban a user (admin only)     |
| GET    | `/api/health`                 | –    | Health check                         |

Authenticated requests send `Authorization: Bearer <token>`.

## Features

- **Authentication** — Google Sign-In only (emails are verified by Google), JWT sessions (7-day expiry)
- **Questions** — title + rich text body + optional image upload (JPEG/PNG/GIF/WebP, ≤ 5 MB), stored on a persistent volume
- **Upvotes** — one vote per user per question, click again to remove; feed sortable by Newest / Top
- **Comments** — discussion under each question, with optional image attachments
- **Share** — native share sheet where available, clipboard fallback, with a share counter
- **Search** — case-insensitive search across titles and bodies
- **Dark mode** — toggle in the navbar, follows system preference by default
- **User profiles** — click any username: avatar, stats (questions / comments / upvotes received), their posts, and a Message button
- **Direct messages** — 1:1 chat with unread badges (polling-based)
- **Moderation** — profanity is auto-removed before it's stored; authors are flagged, and accounts auto-suspend after 5 flags. Question/comment deletion by the author or an admin
- **Admin role** — emails listed in `ADMIN_EMAILS` become admins on sign-in and get a moderation dashboard (`/admin`) to review flags and ban/unban

## Environment variables (backend)

| Variable | Purpose |
| --- | --- |
| `DATABASE_URL` | PostgreSQL connection string |
| `JWT_SECRET` | Signing key for session tokens |
| `GOOGLE_CLIENT_ID` | OAuth 2.0 Web Client ID for Google Sign-In |
| `ADMIN_EMAILS` | Comma-separated emails granted the ADMIN role on sign-in |
| `MODERATION_BAN_THRESHOLD` | Flags before auto-suspension (default 5) |
| `PORT` | Listen port (default 4000) |
