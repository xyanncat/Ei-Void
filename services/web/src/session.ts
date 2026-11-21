import { randomBytes, timingSafeEqual } from 'node:crypto';
import type { Request, Response } from 'express';
import type { Config } from './config.js';

export const FORM_SESSION_COOKIE = 'eivoid_form';
export const CSRF_COOKIE = 'eivoid_csrf';
const CSRF_MAX_AGE_MS = 60 * 60 * 1000;
const SESSION_MAX_AGE_MS = 30 * 24 * 60 * 60 * 1000;
const HEX_CSRF = /^[a-f0-9]{64}$/;

/**
 * A signed cookie written after a browser form succeeds. It is *not* an
 * authentication credential — Clerk owns sign-in — and is only used to carry
 * form integrity context between the confirm page and the dashboard.
 */
export interface FormSession {
  clerkUserId: string;
  linkedAt: number;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

export function readFormSession(req: Request): FormSession | null {
  const raw = (req.signedCookies as Record<string, unknown>)[FORM_SESSION_COOKIE];
  if (typeof raw !== 'string' || raw.length > 2048) return null;
  let parsed: unknown;
  try {
    parsed = JSON.parse(raw);
  } catch {
    return null;
  }
  if (!isRecord(parsed)) return null;
  const { clerkUserId, linkedAt } = parsed;
  if (typeof clerkUserId !== 'string' || !/^user_[A-Za-z0-9]{1,120}$/.test(clerkUserId)) {
    return null;
  }
  if (typeof linkedAt !== 'number' || !Number.isFinite(linkedAt)) return null;
  if (Date.now() - linkedAt > SESSION_MAX_AGE_MS || linkedAt > Date.now() + 5 * 60_000) {
    return null;
  }
  return { clerkUserId, linkedAt };
}

export function writeFormSession(
  res: Response,
  config: Config,
  session: Omit<FormSession, 'linkedAt'>,
): void {
  res.cookie(FORM_SESSION_COOKIE, JSON.stringify({ ...session, linkedAt: Date.now() }), {
    signed: true,
    httpOnly: true,
    sameSite: 'lax',
    secure: config.isProduction,
    maxAge: SESSION_MAX_AGE_MS,
    path: '/',
  });
}

export function clearFormSession(res: Response, config: Config): void {
  const options = { path: '/', secure: config.isProduction };
  res.clearCookie(FORM_SESSION_COOKIE, options);
  res.clearCookie(CSRF_COOKIE, options);
}

/** Creates a double-submit CSRF token if this browser does not have one. */
export function ensureCsrf(req: Request, res: Response, config: Config): string {
  const existing = (req.signedCookies as Record<string, unknown>)[CSRF_COOKIE];
  if (typeof existing === 'string' && HEX_CSRF.test(existing)) return existing;
  const token = randomBytes(32).toString('hex');
  res.cookie(CSRF_COOKIE, token, {
    signed: true,
    httpOnly: true,
    sameSite: 'lax',
    secure: config.isProduction,
    maxAge: CSRF_MAX_AGE_MS,
    path: '/',
  });
  return token;
}

export function verifyCsrf(req: Request): boolean {
  const cookie = (req.signedCookies as Record<string, unknown>)[CSRF_COOKIE];
  const body: unknown = req.body;
  const token = isRecord(body) ? body._csrf : undefined;
  if (typeof cookie !== 'string' || typeof token !== 'string') return false;
  const a = Buffer.from(cookie);
  const b = Buffer.from(token);
  return a.length === b.length && a.length > 0 && timingSafeEqual(a, b);
}
