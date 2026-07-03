import { Router } from "express";
import { z } from "zod";
import { prisma } from "../lib/prisma";
import { AuthRequest, optionalAuth, requireActiveUser } from "../middleware/auth";
import { rejectIfProfane } from "../moderation";
import { discardUpload, imageUpload } from "../upload";

const router = Router();

const authorSelect = { select: { id: true, username: true, avatarUrl: true } };

// Selecting only the viewer's vote lets one query answer "did I vote?"
function viewerVotes(userId: string | undefined) {
  return { where: { userId: userId ?? "" }, select: { userId: true } };
}

function withVoteFields<T extends { votes: { userId: string }[]; _count: { votes: number } }>(
  question: T
) {
  const { votes, ...rest } = question;
  return { ...rest, voteCount: question._count.votes, viewerHasVoted: votes.length > 0 };
}

const questionSchema = z.object({
  title: z.string().trim().min(8, "Title must be at least 8 characters").max(200, "Title is too long"),
  body: z.string().trim().min(1, "Question body is required").max(20000, "Body is too long"),
});

router.get("/", optionalAuth, async (req: AuthRequest, res, next) => {
  try {
    const q = typeof req.query.q === "string" ? req.query.q.trim() : "";
    const page = Math.max(1, Number(req.query.page) || 1);
    const pageSize = 20;
    const orderBy =
      req.query.sort === "top"
        ? [{ votes: { _count: "desc" as const } }, { createdAt: "desc" as const }]
        : [{ createdAt: "desc" as const }];
    const where = q
      ? {
          OR: [
            { title: { contains: q, mode: "insensitive" as const } },
            { body: { contains: q, mode: "insensitive" as const } },
          ],
        }
      : {};

    const [questions, total] = await Promise.all([
      prisma.question.findMany({
        where,
        orderBy,
        skip: (page - 1) * pageSize,
        take: pageSize,
        include: {
          author: authorSelect,
          votes: viewerVotes(req.userId),
          _count: { select: { comments: true, votes: true } },
        },
      }),
      prisma.question.count({ where }),
    ]);
    res.json({ questions: questions.map(withVoteFields), total, page, pageSize });
  } catch (err) {
    next(err);
  }
});

router.post("/", requireActiveUser, imageUpload.single("image"), async (req: AuthRequest, res, next) => {
  try {
    const parsed = questionSchema.safeParse(req.body);
    if (!parsed.success) {
      discardUpload(req.file);
      return res.status(400).json({ error: parsed.error.issues[0].message });
    }

    const violation = await rejectIfProfane(
      req.userId!,
      "question",
      parsed.data.title,
      parsed.data.body
    );
    if (violation) {
      discardUpload(req.file);
      return res.status(400).json({ error: violation });
    }

    const question = await prisma.question.create({
      data: {
        ...parsed.data,
        imageUrl: req.file ? `/uploads/${req.file.filename}` : null,
        authorId: req.userId!,
      },
      include: {
        author: authorSelect,
        votes: viewerVotes(req.userId),
        _count: { select: { comments: true, votes: true } },
      },
    });
    res.status(201).json(withVoteFields(question));
  } catch (err) {
    next(err);
  }
});

router.get("/:id", optionalAuth, async (req: AuthRequest, res, next) => {
  try {
    const question = await prisma.question.findUnique({
      where: { id: req.params.id },
      include: {
        author: authorSelect,
        comments: { orderBy: { createdAt: "asc" }, include: { author: authorSelect } },
        votes: viewerVotes(req.userId),
        _count: { select: { comments: true, votes: true } },
      },
    });
    if (!question) return res.status(404).json({ error: "Question not found" });
    res.json(withVoteFields(question));
  } catch (err) {
    next(err);
  }
});

// Author or admin can delete a question (comments/votes cascade)
router.delete("/:id", requireActiveUser, async (req: AuthRequest, res, next) => {
  try {
    const question = await prisma.question.findUnique({
      where: { id: req.params.id },
      select: { authorId: true },
    });
    if (!question) return res.status(404).json({ error: "Question not found" });
    if (question.authorId !== req.userId && req.currentUser?.role !== "ADMIN") {
      return res.status(403).json({ error: "You can only delete your own questions" });
    }
    await prisma.question.delete({ where: { id: req.params.id } });
    res.status(204).end();
  } catch (err) {
    next(err);
  }
});

router.post("/:id/vote", requireActiveUser, async (req: AuthRequest, res, next) => {
  try {
    const questionId = req.params.id;
    const question = await prisma.question.findUnique({
      where: { id: questionId },
      select: { id: true },
    });
    if (!question) return res.status(404).json({ error: "Question not found" });

    const existing = await prisma.vote.findUnique({
      where: { userId_questionId: { userId: req.userId!, questionId } },
    });
    if (existing) {
      await prisma.vote.delete({ where: { id: existing.id } });
    } else {
      await prisma.vote.create({ data: { userId: req.userId!, questionId } });
    }
    const voteCount = await prisma.vote.count({ where: { questionId } });
    res.json({ voteCount, viewerHasVoted: !existing });
  } catch (err) {
    next(err);
  }
});

const commentSchema = z.object({
  body: z.string().trim().min(1, "Comment cannot be empty").max(5000, "Comment is too long"),
});

router.post(
  "/:id/comments",
  requireActiveUser,
  imageUpload.single("image"),
  async (req: AuthRequest, res, next) => {
    try {
      const parsed = commentSchema.safeParse(req.body);
      if (!parsed.success) {
        discardUpload(req.file);
        return res.status(400).json({ error: parsed.error.issues[0].message });
      }
      const question = await prisma.question.findUnique({
        where: { id: req.params.id },
        select: { id: true },
      });
      if (!question) {
        discardUpload(req.file);
        return res.status(404).json({ error: "Question not found" });
      }

      const violation = await rejectIfProfane(req.userId!, "comment", parsed.data.body);
      if (violation) {
        discardUpload(req.file);
        return res.status(400).json({ error: violation });
      }

      const comment = await prisma.comment.create({
        data: {
          body: parsed.data.body,
          imageUrl: req.file ? `/uploads/${req.file.filename}` : null,
          questionId: question.id,
          authorId: req.userId!,
        },
        include: { author: authorSelect },
      });
      res.status(201).json(comment);
    } catch (err) {
      next(err);
    }
  }
);

// Author or admin can delete a comment
router.delete("/:id/comments/:commentId", requireActiveUser, async (req: AuthRequest, res, next) => {
  try {
    const comment = await prisma.comment.findUnique({
      where: { id: req.params.commentId },
      select: { authorId: true, questionId: true },
    });
    if (!comment || comment.questionId !== req.params.id) {
      return res.status(404).json({ error: "Comment not found" });
    }
    if (comment.authorId !== req.userId && req.currentUser?.role !== "ADMIN") {
      return res.status(403).json({ error: "You can only delete your own comments" });
    }
    await prisma.comment.delete({ where: { id: req.params.commentId } });
    res.status(204).end();
  } catch (err) {
    next(err);
  }
});

router.post("/:id/share", async (req, res, next) => {
  try {
    const question = await prisma.question.update({
      where: { id: req.params.id },
      data: { shareCount: { increment: 1 } },
      select: { id: true, shareCount: true },
    });
    res.json(question);
  } catch (err) {
    if (err && typeof err === "object" && "code" in err && err.code === "P2025") {
      return res.status(404).json({ error: "Question not found" });
    }
    next(err);
  }
});

export default router;
