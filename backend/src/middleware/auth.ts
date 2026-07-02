import { NextFunction, Request, Response } from "express";
import jwt from "jsonwebtoken";

const JWT_SECRET = process.env.JWT_SECRET || "dev-secret-change-me";

export interface AuthRequest extends Request {
  userId?: string;
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
