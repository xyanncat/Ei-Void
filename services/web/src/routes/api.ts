import {
  Router,
  type ErrorRequestHandler,
  type Request,
  type RequestHandler,
  type Response,
} from 'express';
import rateLimit from 'express-rate-limit';
import { hashDeviceKey } from '@eivoid/db';
import {
  deviceKeyIssueRequestSchema,
  discordLinkRequestSchema,
  planLimit,
  reminderSyncRequestSchema,
  usageSnapshotInputSchema,
} from '@eivoid/contracts';
import {
  DiscordAuthError,
  verifyDiscordOAuthToken,
  type VerifiedDiscordUser,
} from '@eivoid/discord-core';
import type { Mailer } from '@eivoid/mailer';
import { bearerToken, requireUser, type Auth, type AuthUser } from '../auth/clerk.js';
import type { Config } from '../config.js';
import type { WebStore } from '../ports.js';

const CLERK_API = 'https://api.clerk.com/v1';
const DISCORD_API = 'https://discord.com/api/v10';
const GITHUB_PROVIDER = 'oauth_github';
const DEVICE_KEY_PREFIX = 'eivoid_';

export interface ApiDeps {
  config: Config;
  store: WebStore;
  auth: Auth;
  mailer: Mailer;
  /** Injectable fetch for Discord and Clerk HTTP calls in tests. */
  fetchImpl?: typeof fetch;
}

type AsyncHandler = (req: Request, res: Response) => Promise<void>;

function asyncHandler(handler: AsyncHandler): RequestHandler {
  return (req, res, next) => {
    handler(req, res).catch(next);
  };
}

function authUser(res: Response): AuthUser {
  return res.locals.authUser as AuthUser;
}

function fail(res: Response, status: number, error: string, detail?: string): void {
  res.status(status).json(detail ? { error, detail } : { error });
}

function serialiseUsage(
  usage: Awaited<ReturnType<WebStore['latestUsage']>>,
): Record<string, unknown> | null {
  if (!usage) return null;
  return {
    id: usage.id,
    plan: usage.plan,
    tokensUsed: usage.tokensUsed,
    tokensLimit: usage.tokensLimit,
    periodEnd: usage.periodEnd,
    createdAt: usage.createdAt.toISOString(),
  };
}

interface OauthAccessToken {
  provider?: string;
  provider_user_id?: string;
  token?: string;
  expires_at?: number | null;
  scopes?: string[];
}

function pickGithubToken(
  tokens: OauthAccessToken[],
  nowSeconds = Math.floor(Date.now() / 1000),
): OauthAccessToken | null {
  const usable = tokens.filter((entry) => {
    if (entry.provider !== GITHUB_PROVIDER && entry.provider !== 'github') return false;
    if (!entry.token) return false;
    return entry.expires_at == null || entry.expires_at > nowSeconds;
  });
  usable.sort((a, b) => (b.expires_at ?? 0) - (a.expires_at ?? 0));
  return usable[0] ?? null;
}

export function createApiRouter({
  config,
  store,
  auth,
  mailer,
  fetchImpl,
}: ApiDeps): Router {
  const router = Router();
  const doFetch = fetchImpl ?? fetch;
  const clerkUser = requireUser(auth, { mode: 'json' });

  const apiLimiter = rateLimit({
    windowMs: 60_000,
    limit: 180,
    standardHeaders: 'draft-7',
    legacyHeaders: false,
    skip: () => config.env === 'test',
    handler: (_req, res) => fail(res, 429, 'too_many_requests'),
  });
  const mutationLimiter = rateLimit({
    windowMs: 60_000,
    limit: 60,
    standardHeaders: 'draft-7',
    legacyHeaders: false,
    skip: () => config.env === 'test',
    handler: (_req, res) => fail(res, 429, 'too_many_requests'),
  });
  const testLimiter = rateLimit({
    windowMs: 60_000,
    limit: 10,
    standardHeaders: 'draft-7',
    legacyHeaders: false,
    skip: () => config.env === 'test',
    handler: (_req, res) => fail(res, 429, 'too_many_requests'),
  });

  router.use(apiLimiter);

  router.get(
    '/health',
    asyncHandler(async (_req, res) => {
      const database = await store.ping().catch(() => false);
      res.json({
        ok: true,
        database,
        clerk: Boolean(config.clerk.secretKey && config.clerk.publishableKey),
        mail: config.mail.provider !== null,
        discord: Boolean(config.discord.token),
        updates: Boolean(config.github.token),
      });
    }),
  );

  // ---- reminders / account -------------------------------------------------

  router.post(
    '/reminders/sync',
    mutationLimiter,
    clerkUser,
    asyncHandler(async (req, res) => {
      const parsed = reminderSyncRequestSchema.safeParse(req.body ?? {});
      if (!parsed.success) {
        fail(res, 400, 'bad_request');
        return;
      }
      const user = authUser(res);
      const { profile, reminders } = parsed.data;
      const existing = await store.getAccountByClerkId(user.userId);
      const email = (user.email || profile.email || existing?.email || '')
        .trim()
        .toLowerCase()
        .slice(0, 254);

      const account = await store.upsertAccount({
        clerkUserId: user.userId,
        email,
        displayName: profile.name,
        timezone: profile.timezone,
        notifyHour: profile.notifyHour,
        notifyMinute: profile.notifyMinute,
        emailEnabled: profile.emailEnabled,
        plan: existing?.plan,
      });
      const synced = await store.replaceReminders(user.userId, reminders);
      res.json({
        ok: true,
        synced,
        discordLinked: Boolean(account.discordUserId),
      });
    }),
  );

  router.get(
    '/account',
    clerkUser,
    asyncHandler(async (req, res) => {
      const user = authUser(res);
      const account = await store.getAccountByClerkId(user.userId);
      if (!account) {
        res.json({
          clerkUserId: user.userId,
          email: user.email,
          displayName: '',
          timezone: 'UTC',
          notifyHour: 9,
          notifyMinute: 0,
          emailEnabled: false,
          plan: 'Free',
          discord: null,
          usage: null,
          planLimit: planLimit('Free'),
        });
        return;
      }
      const usage = await store.latestUsage(user.userId);
      res.set('Cache-Control', 'no-store');
      res.json({
        clerkUserId: account.clerkUserId,
        email: account.email,
        displayName: account.displayName,
        timezone: account.timezone,
        notifyHour: account.notifyHour,
        notifyMinute: account.notifyMinute,
        emailEnabled: account.emailEnabled,
        plan: account.plan,
        discord: account.discordUserId
          ? { id: account.discordUserId, username: account.discordUsername }
          : null,
        usage: serialiseUsage(usage),
        planLimit: planLimit(account.plan),
        createdAt: account.createdAt.toISOString(),
        updatedAt: account.updatedAt.toISOString(),
      });
    }),
  );

  router.delete(
    '/account',
    mutationLimiter,
    clerkUser,
    asyncHandler(async (_req, res) => {
      const deleted = await store.deleteAccount(authUser(res).userId);
      res.json({ ok: true, deleted });
    }),
  );

  // ---- Discord -------------------------------------------------------------

  router.post(
    '/discord/link',
    mutationLimiter,
    clerkUser,
    asyncHandler(async (req, res) => {
      const parsed = discordLinkRequestSchema.safeParse(req.body ?? {});
      if (!parsed.success) {
        fail(res, 400, 'bad_request');
        return;
      }
      let discord: VerifiedDiscordUser;
      try {
        discord = await verifyDiscordOAuthToken(parsed.data.accessToken, {
          ...(config.discord.guildId ? { guildId: config.discord.guildId } : {}),
          ...(fetchImpl ? { fetchImpl } : {}),
        });
      } catch (error) {
        if (error instanceof DiscordAuthError) {
          if (error.code === 'guild_membership_required') {
            fail(res, 403, 'guild_membership_required');
            return;
          }
          if (error.code === 'invalid_discord_token') {
            fail(res, 401, 'invalid_discord_token');
            return;
          }
          if (error.code === 'discord_rate_limited') {
            fail(res, 429, 'too_many_requests');
            return;
          }
          fail(res, 503, 'discord_unreachable');
          return;
        }
        fail(res, 502, 'discord_unreachable');
        return;
      }
      await store.linkDiscord(authUser(res).userId, discord);
      res.json({ ok: true, discord: { id: discord.id, username: discord.username } });
    }),
  );

  router.delete(
    '/discord/link',
    mutationLimiter,
    clerkUser,
    asyncHandler(async (_req, res) => {
      const account = await store.unlinkDiscord(authUser(res).userId);
      res.json({ ok: true, unlinked: account !== null });
    }),
  );

  // ---- test deliveries -----------------------------------------------------

  router.post(
    '/test/email',
    testLimiter,
    clerkUser,
    asyncHandler(async (req, res) => {
      const user = authUser(res);
      const account = await store.getAccountByClerkId(user.userId);
      const address = String(account?.email ?? '').trim().toLowerCase();
      if (!address) {
        // The recipient is always the address this Clerk user synced. A body
        // recipient is never read; accepting one would turn the owner's mail
        // account into a relay for any signed-in caller.
        fail(res, 400, 'email_not_synced');
        return;
      }
      const result = await mailer.send({
        to: address,
        subject: 'Ei Void test email',
        text: 'This is a test notification from Ei Void.',
      });
      if (!result.ok) {
        fail(res, result.status, result.error);
        return;
      }
      res.json({ ok: true, provider: result.provider });
    }),
  );

  router.post(
    '/test/discord',
    testLimiter,
    clerkUser,
    asyncHandler(async (req, res) => {
      const user = authUser(res);
      const account = await store.getAccountByClerkId(user.userId);
      if (!account?.discordUserId) {
        fail(res, 409, 'discord_not_linked');
        return;
      }
      const token = config.discord.token;
      if (!token) {
        fail(
          res,
          501,
          'discord_test_unavailable',
          'Test DMs run from the app linked flow; no Discord bot token is configured on this service.',
        );
        return;
      }

      const headers = {
        Authorization: `Bot ${token}`,
        'Content-Type': 'application/json',
      };
      const created = await doFetch(`${DISCORD_API}/users/@me/channels`, {
        method: 'POST',
        headers,
        body: JSON.stringify({ recipient_id: account.discordUserId }),
        signal: AbortSignal.timeout(10_000),
      });
      if (created.status === 429) {
        fail(res, 429, 'too_many_requests');
        return;
      }
      if (!created.ok) {
        fail(res, 502, 'discord_send_failed');
        return;
      }
      const channel = (await created.json().catch(() => null)) as { id?: unknown } | null;
      const channelId = typeof channel?.id === 'string' ? channel.id : '';
      if (!channelId) {
        fail(res, 502, 'discord_send_failed');
        return;
      }
      const sent = await doFetch(`${DISCORD_API}/channels/${channelId}/messages`, {
        method: 'POST',
        headers,
        body: JSON.stringify({
          content: 'Ei Void test message. Your Discord link is working.',
          allowed_mentions: { parse: [] },
        }),
        signal: AbortSignal.timeout(10_000),
      });
      if (sent.status === 429) {
        fail(res, 429, 'too_many_requests');
        return;
      }
      if (!sent.ok) {
        fail(res, 502, 'discord_send_failed');
        return;
      }
      res.json({ ok: true, delivered: 'dm' });
    }),
  );

  // ---- GitHub token gateway ------------------------------------------------

  router.get(
    '/github/token',
    clerkUser,
    asyncHandler(async (req, res) => {
      const secretKey = config.clerk.secretKey;
      if (!secretKey) {
        fail(res, 503, 'clerk_not_configured');
        return;
      }
      const userId = authUser(res).userId;
      let response: globalThis.Response;
      try {
        response = (await doFetch(
          `${CLERK_API}/users/${encodeURIComponent(userId)}/oauth_access_tokens/${GITHUB_PROVIDER}`,
          {
            headers: {
              Authorization: `Bearer ${secretKey}`,
              Accept: 'application/json',
            },
            signal: AbortSignal.timeout(15_000),
          },
        )) as unknown as globalThis.Response;
      } catch {
        fail(res, 502, 'clerk_upstream_error');
        return;
      }

      if (response.status === 404) {
        fail(res, 404, 'github_not_connected');
        return;
      }
      if (response.status === 409) {
        res.set('Retry-After', '2');
        fail(res, 409, 'token_refresh_in_progress');
        return;
      }
      if (!response.ok) {
        // 401/403 here means the service's own Clerk secret is wrong. Never
        // surface Clerk's body, which can name the credential.
        fail(res, 502, 'clerk_upstream_error');
        return;
      }

      const tokens = (await response.json().catch(() => null)) as unknown;
      const list = Array.isArray(tokens) ? (tokens as OauthAccessToken[]) : [];
      const github = pickGithubToken(list);
      if (!github?.token) {
        fail(res, 404, 'github_not_connected');
        return;
      }
      res.set('Cache-Control', 'no-store');
      res.json({
        token: github.token,
        scopes: Array.isArray(github.scopes) ? github.scopes : [],
        expiresAt: github.expires_at ?? null,
      });
    }),
  );

  // ---- device keys and usage ----------------------------------------------

  router.post(
    '/devices',
    mutationLimiter,
    clerkUser,
    asyncHandler(async (req, res) => {
      const parsed = deviceKeyIssueRequestSchema.safeParse(req.body ?? {});
      if (!parsed.success) {
        fail(res, 400, 'bad_request');
        return;
      }
      const issued = await store.issueDeviceKey(authUser(res).userId);
      res.status(201).json({ id: issued.id, key: issued.key });
    }),
  );

  router.delete(
    '/devices/:id',
    mutationLimiter,
    clerkUser,
    asyncHandler(async (req, res) => {
      const raw = req.params.id ?? '';
      if (!/^\d{1,12}$/.test(raw)) {
        fail(res, 400, 'bad_request');
        return;
      }
      const revoked = await store.revokeDeviceKeyForUser(
        Number(raw),
        authUser(res).userId,
      );
      if (!revoked) {
        fail(res, 404, 'not_found');
        return;
      }
      res.json({ ok: true, revoked: true });
    }),
  );

  router.post(
    '/usage',
    mutationLimiter,
    asyncHandler(async (req, res) => {
      const token = bearerToken(req.get('authorization'));
      if (!token || !token.startsWith(DEVICE_KEY_PREFIX) || token.length > 200) {
        fail(res, 401, 'invalid_clerk_token');
        return;
      }
      let record;
      try {
        record = await store.findDeviceKey(hashDeviceKey(token));
      } catch {
        record = null;
      }
      if (!record) {
        fail(res, 401, 'invalid_clerk_token');
        return;
      }
      const parsed = usageSnapshotInputSchema.safeParse(req.body ?? {});
      if (!parsed.success) {
        fail(res, 400, 'bad_request');
        return;
      }
      await store.addUsageSnapshot({
        clerkUserId: record.clerkUserId,
        plan: parsed.data.plan,
        tokensUsed: parsed.data.tokensUsed,
        tokensLimit: parsed.data.tokensLimit,
        periodEnd: parsed.data.periodEnd ?? null,
      });
      res.status(202).json({ ok: true });
    }),
  );

  router.use((_req, res) => {
    fail(res, 404, 'not_found');
  });

  const apiErrorHandler: ErrorRequestHandler = (error, _req, res, next) => {
    if (res.headersSent) {
      next(error);
      return;
    }
    console.error('API error:', error instanceof Error ? error.message : 'unknown error');
    fail(res, 500, 'internal_error');
  };
  router.use(apiErrorHandler);

  return router;
}
