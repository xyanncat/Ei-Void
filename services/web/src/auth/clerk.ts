import { createClerkClient, verifyToken } from '@clerk/backend';
import { getAuth } from '@clerk/express';
import type { NextFunction, Request, RequestHandler, Response } from 'express';
import type { Config } from '../config.js';

export interface AuthUser {
  readonly userId: string;
  readonly email: string;
}

/**
 * The authentication surface the app depends on. Production uses Clerk;
 * tests inject a fake so no network or secret is required.
 */
export interface Auth {
  getUser(req: Request): Promise<AuthUser | null>;
}

const BEARER = /^Bearer\s+(\S+)$/i;
const EMAIL_CACHE_TTL_MS = 5 * 60_000;

/** Returns the token from an `Authorization: Bearer <token>` header. */
export function bearerToken(header: string | undefined): string | null {
  if (!header) return null;
  const match = BEARER.exec(header.trim());
  return match?.[1] ?? null;
}

/**
 * Clerk authentication.
 *
 * - `Authorization: Bearer <Clerk session JWT>` is verified with
 *   `verifyToken` from `@clerk/backend`, which is what the Android app sends.
 * - Browser sessions come from `getAuth`, populated by `clerkMiddleware`.
 *
 * The secret key and the user's email are never logged or returned to a
 * browser except inside the authenticated response.
 */
export function createClerkAuth(config: Config): Auth {
  const secretKey = config.clerk.secretKey;
  if (!secretKey) {
    return { getUser: async () => null };
  }

  const client = createClerkClient({ secretKey });
  const emailCache = new Map<string, { email: string; expiresAt: number }>();

  async function emailFor(userId: string): Promise<string> {
    const cached = emailCache.get(userId);
    if (cached && Date.now() < cached.expiresAt) return cached.email;
    let email = '';
    try {
      const user = await client.users.getUser(userId);
      const primaryId = user.primaryEmailAddressId;
      const primary =
        user.emailAddresses.find((entry) => entry.id === primaryId) ??
        user.emailAddresses[0];
      email = String(primary?.emailAddress ?? '').trim().toLowerCase();
    } catch {
      // A Clerk outage must not turn a valid session into a 500. Callers that
      // need an address (test email, account sync) handle the empty string.
      email = '';
    }
    emailCache.set(userId, { email, expiresAt: Date.now() + EMAIL_CACHE_TTL_MS });
    if (emailCache.size > 1000) {
      const now = Date.now();
      for (const [key, value] of emailCache) {
        if (value.expiresAt <= now) emailCache.delete(key);
      }
    }
    return email;
  }

  return {
    async getUser(req: Request): Promise<AuthUser | null> {
      const token = bearerToken(req.get('authorization'));
      if (token) {
        try {
          const payload = await verifyToken(token, {
            secretKey,
            ...(config.clerk.issuer ? { issuer: config.clerk.issuer } : {}),
          });
          const userId = typeof payload.sub === 'string' ? payload.sub : '';
          if (!userId) return null;
          return { userId, email: await emailFor(userId) };
        } catch {
          return null;
        }
      }

      try {
        const state = getAuth(req);
        const userId = typeof state?.userId === 'string' ? state.userId : '';
        if (!userId) return null;
        return { userId, email: await emailFor(userId) };
      } catch {
        // `getAuth` throws when `clerkMiddleware` is not mounted (for example
        // when Clerk is not configured). Treat that as signed out.
        return null;
      }
    },
  };
}

export interface RequireUserOptions {
  /** Page requests redirect to sign-in; API requests get a JSON 401. */
  readonly mode?: 'page' | 'json';
  readonly signInPath?: string;
}

/**
 * Resolves the authenticated user and stores it on `res.locals.authUser`.
 * In page mode an anonymous visitor is sent to the Clerk sign-in page with a
 * `redirect_url`; in JSON mode the response is a typed 401.
 */
export function requireUser(auth: Auth, options: RequireUserOptions = {}): RequestHandler {
  const mode = options.mode ?? 'page';
  const signInPath = options.signInPath ?? '/sign-in';

  return (req: Request, res: Response, next: NextFunction) => {
    auth
      .getUser(req)
      .then((user) => {
        if (!user) {
          if (mode === 'json') {
            res.status(401).json({ error: 'invalid_clerk_token' });
            return;
          }
          const target = `${signInPath}?redirect_url=${encodeURIComponent(req.originalUrl)}`;
          res.redirect(302, target);
          return;
        }
        res.locals.authUser = user;
        next();
      })
      .catch(next);
  };
}
