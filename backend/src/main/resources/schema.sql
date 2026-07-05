-- Idempotent schema, compatible with databases created by the original
-- Prisma backend. Fresh databases get the full schema; existing ones are
-- upgraded in place (new columns added, enum role converted to text).

CREATE TABLE IF NOT EXISTS "User" (
    id            TEXT PRIMARY KEY,
    email         TEXT NOT NULL UNIQUE,
    username      TEXT NOT NULL UNIQUE,
    "googleId"    TEXT UNIQUE,
    name          TEXT,
    "avatarUrl"   TEXT,
    role          TEXT NOT NULL DEFAULT 'USER',
    "flagCount"   INTEGER NOT NULL DEFAULT 0,
    "isBanned"    BOOLEAN NOT NULL DEFAULT false,
    "createdAt"   TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Databases created by older app versions may be missing these columns
-- entirely — add them before altering, so every starting point migrates
ALTER TABLE "User" ADD COLUMN IF NOT EXISTS "googleId" TEXT;
ALTER TABLE "User" ADD COLUMN IF NOT EXISTS name TEXT;
ALTER TABLE "User" ADD COLUMN IF NOT EXISTS "avatarUrl" TEXT;
ALTER TABLE "User" ADD COLUMN IF NOT EXISTS role TEXT NOT NULL DEFAULT 'USER';
ALTER TABLE "User" ADD COLUMN IF NOT EXISTS "flagCount" INTEGER NOT NULL DEFAULT 0;
ALTER TABLE "User" ADD COLUMN IF NOT EXISTS "isBanned" BOOLEAN NOT NULL DEFAULT false;

-- Prisma created role as a Postgres enum; convert to plain text
ALTER TABLE "User" ALTER COLUMN role TYPE TEXT USING role::text;
ALTER TABLE "User" ALTER COLUMN role SET DEFAULT 'USER';
DROP TYPE IF EXISTS "Role";

ALTER TABLE "User" ADD COLUMN IF NOT EXISTS "memberType" TEXT;
ALTER TABLE "User" ADD COLUMN IF NOT EXISTS phone TEXT;
ALTER TABLE "User" ADD COLUMN IF NOT EXISTS organization TEXT;
ALTER TABLE "User" ADD COLUMN IF NOT EXISTS "openToMentor" BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE "User" ADD COLUMN IF NOT EXISTS "seekingMentor" BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE "User" ADD COLUMN IF NOT EXISTS topics TEXT;
ALTER TABLE "User" ADD COLUMN IF NOT EXISTS "linkedinUrl" TEXT;
ALTER TABLE "User" ADD COLUMN IF NOT EXISTS headline TEXT;
ALTER TABLE "User" ADD COLUMN IF NOT EXISTS bio TEXT;
ALTER TABLE "User" ADD COLUMN IF NOT EXISTS "verifyStatus" TEXT NOT NULL DEFAULT 'PENDING';
ALTER TABLE "User" ADD COLUMN IF NOT EXISTS "firstName" TEXT;
ALTER TABLE "User" ADD COLUMN IF NOT EXISTS "lastName" TEXT;
ALTER TABLE "User" ADD COLUMN IF NOT EXISTS "position" TEXT;
ALTER TABLE "User" ADD COLUMN IF NOT EXISTS "passwordHash" TEXT;
ALTER TABLE "User" ADD COLUMN IF NOT EXISTS "notifyAllQuestions" BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE "User" ADD COLUMN IF NOT EXISTS plan TEXT NOT NULL DEFAULT 'FREE';
ALTER TABLE "User" ADD COLUMN IF NOT EXISTS "planExpiresAt" TIMESTAMP(3);

CREATE TABLE IF NOT EXISTS "Question" (
    id            TEXT PRIMARY KEY,
    title         TEXT NOT NULL,
    body          TEXT NOT NULL,
    "imageUrl"    TEXT,
    "shareCount"  INTEGER NOT NULL DEFAULT 0,
    "createdAt"   TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "authorId"    TEXT NOT NULL REFERENCES "User"(id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS "Question_createdAt_idx" ON "Question"("createdAt");

ALTER TABLE "Question" ADD COLUMN IF NOT EXISTS tag TEXT NOT NULL DEFAULT 'GENERAL';
ALTER TABLE "Question" ADD COLUMN IF NOT EXISTS "acceptedCommentId" TEXT;

CREATE TABLE IF NOT EXISTS "Comment" (
    id            TEXT PRIMARY KEY,
    body          TEXT NOT NULL,
    "imageUrl"    TEXT,
    "createdAt"   TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "authorId"    TEXT NOT NULL REFERENCES "User"(id) ON DELETE CASCADE,
    "questionId"  TEXT NOT NULL REFERENCES "Question"(id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS "Comment_questionId_idx" ON "Comment"("questionId");
ALTER TABLE "Comment" ADD COLUMN IF NOT EXISTS "imageUrl" TEXT;

CREATE TABLE IF NOT EXISTS "Vote" (
    id            TEXT PRIMARY KEY,
    "createdAt"   TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "userId"      TEXT NOT NULL REFERENCES "User"(id) ON DELETE CASCADE,
    "questionId"  TEXT NOT NULL REFERENCES "Question"(id) ON DELETE CASCADE
);
CREATE UNIQUE INDEX IF NOT EXISTS "Vote_userId_questionId_key" ON "Vote"("userId", "questionId");

CREATE TABLE IF NOT EXISTS "Message" (
    id            TEXT PRIMARY KEY,
    body          TEXT NOT NULL,
    "createdAt"   TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "readAt"      TIMESTAMP(3),
    "fromId"      TEXT NOT NULL REFERENCES "User"(id) ON DELETE CASCADE,
    "toId"        TEXT NOT NULL REFERENCES "User"(id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS "Message_fromId_toId_createdAt_idx" ON "Message"("fromId", "toId", "createdAt");
CREATE INDEX IF NOT EXISTS "Message_toId_readAt_idx" ON "Message"("toId", "readAt");

CREATE TABLE IF NOT EXISTS "ModerationEvent" (
    id            TEXT PRIMARY KEY,
    kind          TEXT NOT NULL,
    content       TEXT NOT NULL,
    "createdAt"   TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "userId"      TEXT NOT NULL REFERENCES "User"(id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS "ModerationEvent_userId_idx" ON "ModerationEvent"("userId");

CREATE TABLE IF NOT EXISTS "Event" (
    id            TEXT PRIMARY KEY,
    title         TEXT NOT NULL,
    description   TEXT NOT NULL,
    link          TEXT,
    "startsAt"    TIMESTAMP(3) NOT NULL,
    "createdBy"   TEXT NOT NULL REFERENCES "User"(id) ON DELETE CASCADE,
    "createdAt"   TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS "Event_startsAt_idx" ON "Event"("startsAt");

CREATE TABLE IF NOT EXISTS "EventRsvp" (
    id            TEXT PRIMARY KEY,
    "eventId"     TEXT NOT NULL REFERENCES "Event"(id) ON DELETE CASCADE,
    "userId"      TEXT NOT NULL REFERENCES "User"(id) ON DELETE CASCADE,
    "createdAt"   TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS "EventRsvp_eventId_userId_key" ON "EventRsvp"("eventId", "userId");

CREATE TABLE IF NOT EXISTS "Listing" (
    id            TEXT PRIMARY KEY,
    kind          TEXT NOT NULL,
    category      TEXT NOT NULL,
    title         TEXT NOT NULL,
    description   TEXT NOT NULL,
    location      TEXT,
    price         TEXT,
    size          TEXT,
    "imageUrl"    TEXT,
    "createdAt"   TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "authorId"    TEXT NOT NULL REFERENCES "User"(id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS "Listing_createdAt_idx" ON "Listing"("createdAt");

-- Marketplace monetization: leads (buyer→seller contacts). Free tier is capped;
-- PRO lifts the cap and reveals lead details to sellers.
CREATE TABLE IF NOT EXISTS "MarketplaceLead" (
    id            TEXT PRIMARY KEY,
    "listingId"   TEXT NOT NULL REFERENCES "Listing"(id) ON DELETE CASCADE,
    "sellerId"    TEXT NOT NULL REFERENCES "User"(id) ON DELETE CASCADE,
    "buyerId"     TEXT NOT NULL REFERENCES "User"(id) ON DELETE CASCADE,
    "createdAt"   TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS "MarketplaceLead_buyerId_createdAt_idx" ON "MarketplaceLead"("buyerId", "createdAt");
CREATE INDEX IF NOT EXISTS "MarketplaceLead_sellerId_idx" ON "MarketplaceLead"("sellerId");
CREATE UNIQUE INDEX IF NOT EXISTS "MarketplaceLead_listingId_buyerId_key" ON "MarketplaceLead"("listingId", "buyerId");

-- Answer Upvotes & Nested Commenting Upgrades
ALTER TABLE "Comment" ADD COLUMN IF NOT EXISTS "parentId" TEXT REFERENCES "Comment"(id) ON DELETE CASCADE;
ALTER TABLE "Vote" ALTER COLUMN "questionId" DROP NOT NULL;
ALTER TABLE "Vote" ADD COLUMN IF NOT EXISTS "commentId" TEXT REFERENCES "Comment"(id) ON DELETE CASCADE;
CREATE UNIQUE INDEX IF NOT EXISTS "Vote_userId_commentId_key" ON "Vote"("userId", "commentId");

