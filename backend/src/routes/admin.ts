import { Router } from "express";
import { prisma } from "../lib/prisma";
import { requireActiveUser, requireAdmin } from "../middleware/auth";

const router = Router();

router.use(requireActiveUser, requireAdmin);

// Users with moderation history, worst offenders first
router.get("/flagged", async (_req, res, next) => {
  try {
    const users = await prisma.user.findMany({
      where: { OR: [{ flagCount: { gt: 0 } }, { isBanned: true }] },
      orderBy: [{ isBanned: "desc" }, { flagCount: "desc" }],
      select: {
        id: true,
        username: true,
        name: true,
        avatarUrl: true,
        flagCount: true,
        isBanned: true,
        createdAt: true,
        moderationEvents: {
          orderBy: { createdAt: "desc" },
          take: 3,
          select: { kind: true, content: true, createdAt: true },
        },
      },
    });
    res.json({ users });
  } catch (err) {
    next(err);
  }
});

// Manually ban / unban. Unbanning resets the flag counter for a fresh start.
router.post("/users/:id/ban", async (req, res, next) => {
  try {
    const banned = Boolean(req.body?.banned);
    const user = await prisma.user.update({
      where: { id: req.params.id },
      data: banned ? { isBanned: true } : { isBanned: false, flagCount: 0 },
      select: { id: true, username: true, isBanned: true, flagCount: true },
    });
    res.json(user);
  } catch (err) {
    if (err && typeof err === "object" && "code" in err && err.code === "P2025") {
      return res.status(404).json({ error: "User not found" });
    }
    next(err);
  }
});

export default router;
