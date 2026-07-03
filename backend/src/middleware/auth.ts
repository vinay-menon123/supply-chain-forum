import { NextFunction, Request, Response } from "express";
import jwt from "jsonwebtoken";
import { prisma } from "../lib/prisma";

const JWT_SECRET = process.env.JWT_SECRET || "dev-secret-change-me";

export interface AuthRequest extends Request {
  userId?: string;
  currentUser?: { id: string; username: string; role: string; isBanned: boolean };
}

export function signToken(userId: string): string {
  return jwt.sign({}, JWT_SECRET, { subject: userId, expiresIn: "7d" });
}

function userIdFromHeader(header: string | undefined): string | null {
  if (!header?.startsWith("Bearer ")) return null;
  try {
    const payload = jwt.verify(header.slice("Bearer ".length), JWT_SECRET);
    const sub = typeof payload === "string" ? payload : payload.sub;
    return sub || null;
  } catch {
    return null;
  }
}

export function requireAuth(req: AuthRequest, res: Response, next: NextFunction) {
  const userId = userIdFromHeader(req.headers.authorization);
  if (!userId) {
    return res.status(401).json({ error: "Authentication required" });
  }
  req.userId = userId;
  next();
}

// Attaches the user if a valid token is present, but never rejects —
// lets public endpoints personalize responses (e.g. viewerHasVoted)
export function optionalAuth(req: AuthRequest, _res: Response, next: NextFunction) {
  req.userId = userIdFromHeader(req.headers.authorization) ?? undefined;
  next();
}

// For content-creating routes: requires auth AND a non-suspended account
export async function requireActiveUser(req: AuthRequest, res: Response, next: NextFunction) {
  const userId = userIdFromHeader(req.headers.authorization);
  if (!userId) {
    return res.status(401).json({ error: "Authentication required" });
  }
  try {
    const user = await prisma.user.findUnique({
      where: { id: userId },
      select: { id: true, username: true, role: true, isBanned: true },
    });
    if (!user) return res.status(401).json({ error: "Account no longer exists" });
    if (user.isBanned) {
      return res.status(403).json({ error: "Your account is suspended due to repeated guideline violations" });
    }
    req.userId = user.id;
    req.currentUser = user;
    next();
  } catch (err) {
    next(err);
  }
}

// Chain after requireActiveUser
export function requireAdmin(req: AuthRequest, res: Response, next: NextFunction) {
  if (req.currentUser?.role !== "ADMIN") {
    return res.status(403).json({ error: "Admin access required" });
  }
  next();
}
