import { Router } from "express";
import { prisma } from "../lib/prisma";
import { AuthRequest, optionalAuth } from "../middleware/auth";

const router = Router();

const authorSelect = { select: { id: true, username: true, avatarUrl: true } };

router.get("/:username", optionalAuth, async (req: AuthRequest, res, next) => {
  try {
    const user = await prisma.user.findUnique({
      where: { username: req.params.username },
      select: {
        id: true,
        username: true,
        name: true,
        avatarUrl: true,
        role: true,
        createdAt: true,
        _count: { select: { questions: true, comments: true } },
      },
    });
    if (!user) return res.status(404).json({ error: "User not found" });

    const [upvotesReceived, questions] = await Promise.all([
      prisma.vote.count({ where: { question: { authorId: user.id } } }),
      prisma.question.findMany({
        where: { authorId: user.id },
        orderBy: { createdAt: "desc" },
        take: 20,
        include: {
          author: authorSelect,
          votes: { where: { userId: req.userId ?? "" }, select: { userId: true } },
          _count: { select: { comments: true, votes: true } },
        },
      }),
    ]);

    res.json({
      user: {
        id: user.id,
        username: user.username,
        name: user.name,
        avatarUrl: user.avatarUrl,
        role: user.role,
        createdAt: user.createdAt,
      },
      stats: {
        questions: user._count.questions,
        comments: user._count.comments,
        upvotesReceived,
      },
      questions: questions.map(({ votes, ...q }) => ({
        ...q,
        voteCount: q._count.votes,
        viewerHasVoted: votes.length > 0,
      })),
    });
  } catch (err) {
    next(err);
  }
});

export default router;
