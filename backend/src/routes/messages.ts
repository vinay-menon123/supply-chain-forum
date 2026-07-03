import { Router } from "express";
import { z } from "zod";
import { prisma } from "../lib/prisma";
import { AuthRequest, requireActiveUser } from "../middleware/auth";
import { rejectIfProfane } from "../moderation";

const router = Router();

router.use(requireActiveUser);

const partnerSelect = { id: true, username: true, name: true, avatarUrl: true };

// Total unread — polled by the navbar badge
router.get("/unread", async (req: AuthRequest, res, next) => {
  try {
    const count = await prisma.message.count({
      where: { toId: req.userId!, readAt: null },
    });
    res.json({ count });
  } catch (err) {
    next(err);
  }
});

// Conversation list: one entry per partner with last message + unread count
router.get("/", async (req: AuthRequest, res, next) => {
  try {
    const me = req.userId!;
    const messages = await prisma.message.findMany({
      where: { OR: [{ fromId: me }, { toId: me }] },
      orderBy: { createdAt: "desc" },
      take: 300,
      include: {
        from: { select: partnerSelect },
        to: { select: partnerSelect },
      },
    });

    const conversations = new Map<
      string,
      {
        partner: { id: string; username: string; name: string | null; avatarUrl: string | null };
        lastMessage: { body: string; createdAt: Date; fromMe: boolean };
        unread: number;
      }
    >();
    for (const msg of messages) {
      const partner = msg.fromId === me ? msg.to : msg.from;
      let convo = conversations.get(partner.id);
      if (!convo) {
        convo = {
          partner,
          lastMessage: { body: msg.body, createdAt: msg.createdAt, fromMe: msg.fromId === me },
          unread: 0,
        };
        conversations.set(partner.id, convo);
      }
      if (msg.toId === me && !msg.readAt) convo.unread++;
    }
    res.json({ conversations: [...conversations.values()] });
  } catch (err) {
    next(err);
  }
});

// Full thread with one user; opening it marks their messages as read
router.get("/:username", async (req: AuthRequest, res, next) => {
  try {
    const me = req.userId!;
    const partner = await prisma.user.findUnique({
      where: { username: req.params.username },
      select: partnerSelect,
    });
    if (!partner) return res.status(404).json({ error: "User not found" });

    const [messages] = await Promise.all([
      prisma.message.findMany({
        where: {
          OR: [
            { fromId: me, toId: partner.id },
            { fromId: partner.id, toId: me },
          ],
        },
        orderBy: { createdAt: "asc" },
        take: 200,
        select: { id: true, body: true, createdAt: true, fromId: true },
      }),
      prisma.message.updateMany({
        where: { fromId: partner.id, toId: me, readAt: null },
        data: { readAt: new Date() },
      }),
    ]);

    res.json({
      partner,
      messages: messages.map((m) => ({
        id: m.id,
        body: m.body,
        createdAt: m.createdAt,
        fromMe: m.fromId === me,
      })),
    });
  } catch (err) {
    next(err);
  }
});

const messageSchema = z.object({
  body: z.string().trim().min(1, "Message cannot be empty").max(2000, "Message is too long"),
});

router.post("/:username", async (req: AuthRequest, res, next) => {
  try {
    const parsed = messageSchema.safeParse(req.body);
    if (!parsed.success) {
      return res.status(400).json({ error: parsed.error.issues[0].message });
    }
    const partner = await prisma.user.findUnique({
      where: { username: req.params.username },
      select: { id: true },
    });
    if (!partner) return res.status(404).json({ error: "User not found" });
    if (partner.id === req.userId) {
      return res.status(400).json({ error: "You cannot message yourself" });
    }

    const violation = await rejectIfProfane(req.userId!, "message", parsed.data.body);
    if (violation) return res.status(400).json({ error: violation });

    const message = await prisma.message.create({
      data: { body: parsed.data.body, fromId: req.userId!, toId: partner.id },
      select: { id: true, body: true, createdAt: true },
    });
    res.status(201).json({ ...message, fromMe: true });
  } catch (err) {
    next(err);
  }
});

export default router;
