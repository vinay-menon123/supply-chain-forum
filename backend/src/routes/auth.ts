import { Router } from "express";
import { OAuth2Client } from "google-auth-library";
import { prisma } from "../lib/prisma";
import { AuthRequest, requireAuth, signToken } from "../middleware/auth";

const router = Router();

const googleClientId = process.env.GOOGLE_CLIENT_ID || "";
const googleClient = new OAuth2Client(googleClientId);

const userSelect = {
  id: true,
  email: true,
  username: true,
  name: true,
  avatarUrl: true,
  createdAt: true,
};

// The frontend fetches the client id at runtime, so one image works everywhere
router.get("/config", (_req, res) => {
  res.json({ googleClientId });
});

async function uniqueUsername(email: string): Promise<string> {
  let base = email.split("@")[0].toLowerCase().replace(/[^a-z0-9_]/g, "_").slice(0, 24);
  if (base.length < 3) base = `user_${base}`;
  let candidate = base;
  for (let suffix = 2; ; suffix++) {
    const exists = await prisma.user.findUnique({
      where: { username: candidate },
      select: { id: true },
    });
    if (!exists) return candidate;
    candidate = `${base}${suffix}`;
  }
}

router.post("/google", async (req, res, next) => {
  try {
    if (!googleClientId) {
      return res
        .status(503)
        .json({ error: "Google Sign-In is not configured (set GOOGLE_CLIENT_ID)" });
    }
    const credential = typeof req.body?.credential === "string" ? req.body.credential : "";
    if (!credential) {
      return res.status(400).json({ error: "Missing Google credential" });
    }

    let payload;
    try {
      const ticket = await googleClient.verifyIdToken({
        idToken: credential,
        audience: googleClientId,
      });
      payload = ticket.getPayload();
    } catch {
      return res.status(401).json({ error: "Invalid Google credential" });
    }
    if (!payload?.sub || !payload.email) {
      return res.status(401).json({ error: "Google account did not provide an email" });
    }
    if (payload.email_verified === false) {
      return res.status(401).json({ error: "Google email is not verified" });
    }

    const { sub: googleId, email, name, picture } = payload;
    const existing = await prisma.user.findFirst({
      where: { OR: [{ googleId }, { email }] },
      select: { id: true },
    });

    const user = existing
      ? await prisma.user.update({
          where: { id: existing.id },
          data: { googleId, name: name ?? undefined, avatarUrl: picture ?? undefined },
          select: userSelect,
        })
      : await prisma.user.create({
          data: {
            googleId,
            email,
            username: await uniqueUsername(email),
            name: name ?? null,
            avatarUrl: picture ?? null,
          },
          select: userSelect,
        });

    res.json({ token: signToken(user.id), user });
  } catch (err) {
    next(err);
  }
});

router.get("/me", requireAuth, async (req: AuthRequest, res, next) => {
  try {
    const user = await prisma.user.findUnique({
      where: { id: req.userId! },
      select: userSelect,
    });
    if (!user) return res.status(401).json({ error: "Account no longer exists" });
    res.json({ user });
  } catch (err) {
    next(err);
  }
});

export default router;
