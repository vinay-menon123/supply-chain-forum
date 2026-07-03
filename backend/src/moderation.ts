import leo from "leo-profanity";
import { prisma } from "./lib/prisma";

const BAN_THRESHOLD = Number(process.env.MODERATION_BAN_THRESHOLD) || 5;

/**
 * Checks text against the profanity dictionary. If it violates, the content
 * is rejected (never stored), a moderation event is recorded, the user's
 * flag count is incremented, and the account is suspended once it reaches
 * the threshold. Returns the rejection message, or null when clean.
 */
export async function rejectIfProfane(
  userId: string,
  kind: "question" | "comment" | "message",
  ...texts: (string | undefined)[]
): Promise<string | null> {
  const combined = texts.filter(Boolean).join(" ");
  if (!combined || !leo.check(combined)) return null;

  const user = await prisma.user.update({
    where: { id: userId },
    data: {
      flagCount: { increment: 1 },
      moderationEvents: {
        create: { kind, content: combined.slice(0, 500) },
      },
    },
    select: { flagCount: true, isBanned: true },
  });

  if (user.flagCount >= BAN_THRESHOLD && !user.isBanned) {
    await prisma.user.update({ where: { id: userId }, data: { isBanned: true } });
    return "This content violates our community guidelines and was removed. Your account has been suspended due to repeated violations.";
  }
  if (user.isBanned) {
    return "Your account is suspended.";
  }
  return `This content violates our community guidelines and was removed. Warning ${user.flagCount}/${BAN_THRESHOLD} — repeated violations lead to suspension.`;
}
