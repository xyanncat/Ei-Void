import request from 'supertest';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { DeepPartial } from '@eivoid/config';
import { createApp } from '../src/app.js';
import { makeTestConfig, type Config } from '../src/config.js';
import {
  createFakeAuth,
  createFakeMailer,
  FakeStore,
  type FakeMailer,
} from './support/fakes.js';

const CSRF = /name="_csrf" value="([a-f0-9]{64})"/;
const GUILD = '848841415940898827';
const DISCORD_ID = '123456789012345678';
const PK = `pk_test_${Buffer.from('clerk.ei-void.test$').toString('base64')}`;

interface HarnessOptions {
  config?: DeepPartial<Config>;
  users?: Record<string, string>;
  fetchImpl?: typeof fetch;
  mailer?: FakeMailer;
}

interface Harness {
  app: ReturnType<typeof createApp>;
  store: FakeStore;
  auth: ReturnType<typeof createFakeAuth>;
  mailer: FakeMailer;
  config: Config;
}

function harness(options: HarnessOptions = {}): Harness {
  const config = makeTestConfig(options.config ?? {});
  const store = new FakeStore();
  const auth = createFakeAuth(options.users);
  const mailer = options.mailer ?? createFakeMailer();
  const app = createApp({
    config,
    store,
    auth,
    mailer,
    ...(options.fetchImpl ? { fetchImpl: options.fetchImpl } : {}),
  });
  return { app, store, auth, mailer, config };
}

function token(userId: string): string {
  return `Bearer test:${userId}`;
}

function reminder(localId: number, title = 'Electricity bill') {
  return {
    localId,
    title,
    merchant: 'State Power',
    bank: 'Example Bank',
    amountMinor: 142000,
    currency: 'INR',
    dueAt: 1790000000000 + localId,
    notifyAt: 1789913600000 + localId,
    repeatRule: 'MONTHLY',
    category: 'BILL',
    notes: 'Pay before the late fee',
    leadDays: 4,
    isPaid: false,
    isActive: true,
  };
}

function syncBody(overrides: Record<string, unknown> = {}) {
  return {
    profile: {
      timezone: 'Asia/Kolkata',
      notifyHour: 9,
      notifyMinute: 0,
      name: 'Cat',
      email: 'profile@example.com',
      emailEnabled: true,
    },
    reminders: [reminder(1)],
    ...overrides,
  };
}

function csrfFrom(html: string): string {
  const match = CSRF.exec(html);
  if (!match?.[1]) throw new Error('No CSRF token in rendered page.');
  return match[1];
}

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

describe('public pages', () => {
  let h: Harness;
  beforeEach(() => {
    h = harness();
  });

  it('serves the health probes without secrets', async () => {
    const healthz = await request(h.app).get('/healthz');
    expect(healthz.status).toBe(200);
    expect(healthz.body).toEqual({ ok: true, version: '0.1.0' });

    const health = await request(h.app).get('/api/v1/health');
    expect(health.status).toBe(200);
    expect(health.body.ok).toBe(true);
    expect(Object.keys(health.body).sort()).toEqual([
      'clerk',
      'database',
      'discord',
      'mail',
      'ok',
      'updates',
    ]);
    for (const key of ['database', 'clerk', 'mail', 'discord', 'updates']) {
      expect(typeof health.body[key], key).toBe('boolean');
    }
  });

  it('renders the home page with the Ei Void brand and a nonce CSP', async () => {
    const response = await request(h.app).get('/');
    expect(response.status).toBe(200);
    expect(response.text).toContain('Ei Void');
    expect(response.text).toContain('Reminders');
    expect(response.text).toContain('application/ld+json');
    expect(response.text).toContain(`guild_id=${GUILD}`);
    expect(response.text).toContain('disable_guild_select=true');
    const csp = response.headers['content-security-policy'] ?? '';
    expect(csp).toContain("default-src 'self'");
    expect(csp).toContain("frame-ancestors 'none'");
    expect(csp).toMatch(/script-src 'self' 'nonce-[A-Za-z0-9+/=]+'/);
    expect(response.headers['x-powered-by']).toBeUndefined();
  });

  it('serves every marketing, docs and legal page', async () => {
    const pages: ReadonlyArray<readonly [string, string]> = [
      ['/', 'Reminders'],
      ['/reminders', 'Bills, met'],
      ['/agent', 'Code from'],
      ['/features', 'Small phone'],
      ['/pricing', 'A seat for'],
      ['/bot', 'A cat in'],
      ['/download', 'Take the void'],
      ['/security', 'The quiet'],
      ['/privacy', 'What stays'],
      ['/terms', 'The short'],
      ['/changelog', 'Orbit by'],
      ['/docs', 'Read the'],
      ['/sign-in', 'One door'],
    ];
    for (const [path, needle] of pages) {
      const response = await request(h.app).get(path);
      expect(response.status, path).toBe(200);
      expect(response.text.toLowerCase(), path).toContain(needle.toLowerCase());
      expect(response.text, path).toContain('</html>');
    }
  });

  it('serves styles, scripts and the manifest', async () => {
    for (const [path, type] of [
      ['/css/site.css', 'text/css'],
      ['/js/site.js', 'javascript'],
      ['/site.webmanifest', 'application/manifest+json'],
    ] as const) {
      const response = await request(h.app).get(path);
      expect(response.status, path).toBe(200);
      expect(response.headers['content-type'], path).toContain(type);
    }
  });

  it('renders documentation and its pager', async () => {
    const index = await request(h.app).get('/docs');
    expect(index.status).toBe(200);
    expect(index.text).toContain('Getting started');

    const doc = await request(h.app).get('/docs/getting-started');
    expect(doc.status).toBe(200);
    expect(doc.text).toContain('Build the app');
    expect(doc.text).toContain('Next');

    const api = await request(h.app).get('/docs/api');
    expect(api.status).toBe(200);
    expect(api.text).toContain('/api/v1');

    const missing = await request(h.app).get('/docs/not-a-doc');
    expect(missing.status).toBe(404);
  });

  it('advertises both pillars in the sitemap and keeps private routes out of robots', async () => {
    const sitemap = await request(h.app).get('/sitemap.xml');
    expect(sitemap.status).toBe(200);
    expect(sitemap.text).toContain('/reminders');
    expect(sitemap.text).toContain('/agent');
    expect(sitemap.text).toContain('/docs/getting-started');

    const robots = await request(h.app).get('/robots.txt');
    expect(robots.status).toBe(200);
    expect(robots.text).toContain('Disallow: /dashboard');
    expect(robots.text).toContain('Disallow: /api/');
  });

  it('renders a 404 for unknown pages', async () => {
    const response = await request(h.app).get('/not-here');
    expect(response.status).toBe(404);
    expect(response.text).toContain('Not in this orbit.');
  });

  it('rejects a form post without a CSRF token and accepts the waitlist once', async () => {
    const rejected = await request(h.app)
      .post('/waitlist')
      .type('form')
      .send({ email: 'cat@example.com' });
    expect(rejected.status).toBe(403);

    const agent = request.agent(h.app);
    const page = await agent.get('/');
    const tokenValue = csrfFrom(page.text);
    const created = await agent
      .post('/waitlist')
      .type('form')
      .send({ _csrf: tokenValue, email: 'Cat@Example.com' });
    expect(created.status).toBe(303);
    expect(created.headers.location).toBe('/thanks');
    expect(h.store.waitlistSize()).toBe(1);

    const invalid = await agent
      .post('/waitlist')
      .type('form')
      .send({ _csrf: tokenValue, email: 'not-an-email' });
    expect(invalid.status).toBe(400);
  });

  it('renders the Clerk sign-in page and documents a missing configuration', async () => {
    const response = await request(h.app).get('/sign-in');
    expect(response.status).toBe(200);
    expect(response.text).toContain('Sign-in is not configured on this server.');

    const configured = harness({
      config: { clerk: { secretKey: 'sk_test_example', publishableKey: PK } },
    });
    const signIn = await request(configured.app).get('/sign-in');
    expect(signIn.status).toBe(200);
    expect(signIn.text).toContain('clerk.ei-void.test');
    expect(signIn.text).toContain('data-clerk-publishable-key');
    const csp = signIn.headers['content-security-policy'] ?? '';
    expect(csp).toContain('https://clerk.ei-void.test');
  });
});

describe('authentication', () => {
  let h: Harness;
  beforeEach(() => {
    h = harness();
  });

  it('returns a JSON 401 for the API without a Clerk token', async () => {
    const response = await request(h.app).get('/api/v1/account');
    expect(response.status).toBe(401);
    expect(response.body).toEqual({ error: 'invalid_clerk_token' });
  });

  it('redirects anonymous dashboard and link pages to sign-in', async () => {
    const dashboard = await request(h.app).get('/dashboard');
    expect(dashboard.status).toBe(302);
    expect(dashboard.headers.location).toBe(
      `/sign-in?redirect_url=${encodeURIComponent('/dashboard')}`,
    );

    const link = await request(h.app).get('/link?code=deadbeef');
    expect(link.status).toBe(302);
    expect(link.headers.location).toContain('/sign-in?redirect_url=');
  });

  it('renders the dashboard for a signed-in user without leaking secrets', async () => {
    const response = await request(h.app)
      .get('/dashboard')
      .set('x-test-user', 'user_dash');
    expect(response.status).toBe(200);
    expect(response.headers['cache-control']).toBe('no-store');
    expect(response.text).toContain('user_dash@example.com');
    expect(response.text).toContain('No account row yet');
    expect(response.text).toContain('Delete website data');
    expect(response.text).not.toContain('sk_');
  });
});

describe('reminder sync and account API', () => {
  let h: Harness;
  beforeEach(() => {
    h = harness({ users: { user_a: 'a@example.com', user_b: 'b@example.com' } });
  });

  it('syncs a profile and reminders, then returns the account', async () => {
    const synced = await request(h.app)
      .post('/api/v1/reminders/sync')
      .set('Authorization', token('user_a'))
      .send(syncBody());
    expect(synced.status).toBe(200);
    expect(synced.body).toEqual({ ok: true, synced: 1, discordLinked: false });

    const account = await request(h.app)
      .get('/api/v1/account')
      .set('Authorization', token('user_a'));
    expect(account.status).toBe(200);
    expect(account.body).toMatchObject({
      clerkUserId: 'user_a',
      email: 'a@example.com',
      displayName: 'Cat',
      timezone: 'Asia/Kolkata',
      notifyHour: 9,
      emailEnabled: true,
      plan: 'Free',
      discord: null,
      usage: null,
      planLimit: 30_000_000,
    });
    expect(h.store.remindersFor('user_a')).toHaveLength(1);
  });

  it('keeps two Clerk users isolated', async () => {
    await request(h.app)
      .post('/api/v1/reminders/sync')
      .set('Authorization', token('user_a'))
      .send(syncBody({ reminders: [reminder(1), reminder(2)] }));
    await request(h.app)
      .post('/api/v1/reminders/sync')
      .set('Authorization', token('user_b'))
      .send(syncBody({ reminders: [reminder(7)] }));

    expect(h.store.remindersFor('user_a').map((entry) => entry.localId)).toEqual([1, 2]);
    expect(h.store.remindersFor('user_b').map((entry) => entry.localId)).toEqual([7]);

    const accountB = await request(h.app)
      .get('/api/v1/account')
      .set('Authorization', token('user_b'));
    expect(accountB.body.email).toBe('b@example.com');
  });

  it('rejects an invalid sync body', async () => {
    const response = await request(h.app)
      .post('/api/v1/reminders/sync')
      .set('Authorization', token('user_a'))
      .send({ reminders: [{ localId: -1 }] });
    expect(response.status).toBe(400);
    expect(response.body.error).toBe('bad_request');
  });

  it('deletes the account and cascaded rows', async () => {
    await request(h.app)
      .post('/api/v1/reminders/sync')
      .set('Authorization', token('user_a'))
      .send(syncBody());
    await request(h.app)
      .post('/api/v1/devices')
      .set('Authorization', token('user_a'))
      .send({});

    const removed = await request(h.app)
      .delete('/api/v1/account')
      .set('Authorization', token('user_a'));
    expect(removed.status).toBe(200);
    expect(removed.body).toEqual({ ok: true, deleted: true });
    expect(h.store.remindersFor('user_a')).toHaveLength(0);

    const after = await request(h.app)
      .get('/api/v1/account')
      .set('Authorization', token('user_a'));
    expect(after.body.email).toBe('a@example.com');
    expect(after.body.usage).toBeNull();
  });

  it('rejects malformed JSON and unknown API routes as JSON', async () => {
    const malformed = await request(h.app)
      .post('/api/v1/reminders/sync')
      .set('Authorization', token('user_a'))
      .set('Content-Type', 'application/json')
      .send('{"profile":');
    expect(malformed.status).toBe(400);
    expect(malformed.body.error).toBe('bad_request');

    const unknown = await request(h.app).get('/api/v1/nope');
    expect(unknown.status).toBe(404);
    expect(unknown.body).toEqual({ error: 'not_found' });
  });
});

describe('email, Discord and GitHub', () => {
  it('sends a test email only to the synced account address', async () => {
    const h = harness({
      users: { user_mail: 'auth@example.com' },
      mailer: createFakeMailer(),
    });
    await request(h.app)
      .post('/api/v1/reminders/sync')
      .set('Authorization', token('user_mail'))
      .send(syncBody({ profile: { ...syncBody().profile, email: 'profile@example.com' } }));

    const sent = await request(h.app)
      .post('/api/v1/test/email')
      .set('Authorization', token('user_mail'))
      .send({ to: 'attacker@example.com', recipient: 'attacker@example.com' });
    expect(sent.status).toBe(200);
    expect(sent.body).toEqual({ ok: true, provider: 'smtp' });
    expect(h.mailer.messages).toHaveLength(1);
    expect(h.mailer.messages[0]?.to).toBe('auth@example.com');
    expect(h.mailer.messages[0]?.to).not.toBe('attacker@example.com');
  });

  it('refuses a test email when no account address has synced', async () => {
    const h = harness();
    const response = await request(h.app)
      .post('/api/v1/test/email')
      .set('Authorization', token('user_none'))
      .send({ to: 'attacker@example.com' });
    expect(response.status).toBe(400);
    expect(response.body.error).toBe('email_not_synced');
    expect(h.mailer.messages).toHaveLength(0);
  });

  it('links Discord for a guild member and rejects a non-member', async () => {
    const memberFetch = vi.fn(async (input: unknown) => {
      const url = String(input);
      if (url.endsWith('/users/@me')) {
        return jsonResponse({ id: DISCORD_ID, username: 'voidcat' });
      }
      if (url.endsWith('/users/@me/guilds')) {
        return jsonResponse([{ id: GUILD }]);
      }
      return jsonResponse({}, 404);
    });
    const member = harness({ fetchImpl: memberFetch });
    const linked = await request(member.app)
      .post('/api/v1/discord/link')
      .set('Authorization', token('user_a'))
      .send({ accessToken: 'discord-access-token' });
    expect(linked.status).toBe(200);
    expect(linked.body).toMatchObject({
      ok: true,
      discord: { id: DISCORD_ID, username: 'voidcat' },
    });

    const outsiderFetch = vi.fn(async (input: unknown) => {
      const url = String(input);
      if (url.endsWith('/users/@me')) {
        return jsonResponse({ id: DISCORD_ID, username: 'voidcat' });
      }
      if (url.endsWith('/users/@me/guilds')) return jsonResponse([]);
      return jsonResponse({}, 404);
    });
    const outsider = harness({ fetchImpl: outsiderFetch });
    const rejected = await request(outsider.app)
      .post('/api/v1/discord/link')
      .set('Authorization', token('user_a'))
      .send({ accessToken: 'discord-access-token' });
    expect(rejected.status).toBe(403);
    expect(rejected.body).toEqual({ error: 'guild_membership_required' });
  });

  it('unlinks Discord', async () => {
    const h = harness();
    await h.store.linkDiscord('user_a', { id: DISCORD_ID, username: 'voidcat' });
    const response = await request(h.app)
      .delete('/api/v1/discord/link')
      .set('Authorization', token('user_a'));
    expect(response.status).toBe(200);
    expect(response.body).toEqual({ ok: true, unlinked: true });
    expect((await h.store.getAccountByClerkId('user_a'))?.discordUserId).toBeNull();
  });

  it('maps Clerk GitHub token responses to the public contract', async () => {
    const cases: Array<{
      status: number;
      body: unknown;
      expected: number;
      error: string;
      retry?: string;
    }> = [
      { status: 404, body: {}, expected: 404, error: 'github_not_connected' },
      {
        status: 409,
        body: {},
        expected: 409,
        error: 'token_refresh_in_progress',
        retry: '2',
      },
      {
        status: 200,
        body: [
          {
            provider: 'oauth_github',
            token: 'gho_example',
            scopes: ['repo', 'read:user'],
            expires_at: Math.floor(Date.now() / 1000) + 3600,
          },
        ],
        expected: 200,
        error: '',
      },
    ];

    for (const item of cases) {
      const fetchImpl = vi.fn(async () => jsonResponse(item.body, item.status));
      const h = harness({
        config: { clerk: { secretKey: 'sk_test_example', publishableKey: PK } },
        fetchImpl,
      });
      const response = await request(h.app)
        .get('/api/v1/github/token')
        .set('Authorization', token('user_a'));
      expect(response.status, String(item.status)).toBe(item.expected);
      if (item.expected === 200) {
        expect(response.body).toMatchObject({
          token: 'gho_example',
          scopes: ['repo', 'read:user'],
        });
      } else {
        expect(response.body.error).toBe(item.error);
        if (item.retry) expect(response.headers['retry-after']).toBe(item.retry);
      }
    }
  });

  it('refuses the GitHub token endpoint when Clerk is not configured', async () => {
    const h = harness({ config: { clerk: { secretKey: '' } } });
    const response = await request(h.app)
      .get('/api/v1/github/token')
      .set('Authorization', token('user_a'));
    expect(response.status).toBe(503);
    expect(response.body.error).toBe('clerk_not_configured');
  });

  it('returns an honest 501 for a test DM without a bot token', async () => {
    const h = harness();
    await h.store.linkDiscord('user_a', { id: DISCORD_ID, username: 'voidcat' });
    const response = await request(h.app)
      .post('/api/v1/test/discord')
      .set('Authorization', token('user_a'))
      .send({});
    expect(response.status).toBe(501);
    expect(response.body.error).toBe('discord_test_unavailable');
  });

  it('sends a test DM through the Discord REST API when the token exists', async () => {
    const fetchImpl = vi.fn(async (input: unknown) => {
      const url = String(input);
      if (url.endsWith('/users/@me/channels')) return jsonResponse({ id: '555' });
      if (url.endsWith('/channels/555/messages')) return jsonResponse({ id: '999' });
      return jsonResponse({}, 404);
    });
    const h = harness({
      config: { discord: { token: 'discord-bot-token' } },
      fetchImpl,
    });
    await h.store.linkDiscord('user_a', { id: DISCORD_ID, username: 'voidcat' });
    const response = await request(h.app)
      .post('/api/v1/test/discord')
      .set('Authorization', token('user_a'))
      .send({});
    expect(response.status).toBe(200);
    expect(response.body).toEqual({ ok: true, delivered: 'dm' });
    expect(fetchImpl).toHaveBeenCalledTimes(2);
  });
});

describe('device keys and usage', () => {
  let h: Harness;
  beforeEach(() => {
    h = harness({ users: { user_a: 'a@example.com', user_b: 'b@example.com' } });
  });

  it('issues a device key, accepts a snapshot and reports usage', async () => {
    await request(h.app)
      .post('/api/v1/reminders/sync')
      .set('Authorization', token('user_a'))
      .send(syncBody());

    const issued = await request(h.app)
      .post('/api/v1/devices')
      .set('Authorization', token('user_a'))
      .send({ label: 'phone' });
    expect(issued.status).toBe(201);
    expect(issued.body.id).toEqual(expect.any(Number));
    expect(issued.body.key).toMatch(/^eivoid_[A-Za-z0-9_-]+$/);

    const usage = await request(h.app)
      .post('/api/v1/usage')
      .set('Authorization', `Bearer ${issued.body.key as string}`)
      .send({
        plan: 'Pro',
        tokensUsed: 1_200_000,
        tokensLimit: 80_000_000,
        periodEnd: '2026-12-01',
      });
    expect(usage.status).toBe(202);
    expect(usage.body).toEqual({ ok: true });

    const account = await request(h.app)
      .get('/api/v1/account')
      .set('Authorization', token('user_a'));
    expect(account.body.usage).toMatchObject({
      plan: 'Pro',
      tokensUsed: 1_200_000,
      tokensLimit: 80_000_000,
      periodEnd: '2026-12-01',
    });
  });

  it('rejects an unknown key and an invalid snapshot', async () => {
    const unknown = await request(h.app)
      .post('/api/v1/usage')
      .set('Authorization', 'Bearer eivoid_unknown')
      .send({ plan: 'Free', tokensUsed: 1, tokensLimit: 1 });
    expect(unknown.status).toBe(401);

    const issued = await request(h.app)
      .post('/api/v1/devices')
      .set('Authorization', token('user_a'))
      .send({});
    const invalid = await request(h.app)
      .post('/api/v1/usage')
      .set('Authorization', `Bearer ${issued.body.key as string}`)
      .send({ plan: 'Free', tokensUsed: -1, tokensLimit: 10 });
    expect(invalid.status).toBe(400);
  });

  it('revokes only the caller-owned device key', async () => {
    const issued = await request(h.app)
      .post('/api/v1/devices')
      .set('Authorization', token('user_a'))
      .send({});

    const foreign = await request(h.app)
      .delete(`/api/v1/devices/${issued.body.id as number}`)
      .set('Authorization', token('user_b'));
    expect(foreign.status).toBe(404);

    const revoked = await request(h.app)
      .delete(`/api/v1/devices/${issued.body.id as number}`)
      .set('Authorization', token('user_a'));
    expect(revoked.status).toBe(200);
    expect(revoked.body).toEqual({ ok: true, revoked: true });

    const after = await request(h.app)
      .post('/api/v1/usage')
      .set('Authorization', `Bearer ${issued.body.key as string}`)
      .send({ plan: 'Free', tokensUsed: 1, tokensLimit: 10 });
    expect(after.status).toBe(401);
  });
});

describe('Discord link confirmation', () => {
  let h: Harness;
  beforeEach(() => {
    h = harness({ users: { user_link: 'link@example.com' } });
  });

  it('confirms a fresh code for the signed-in Clerk user and rejects reuse', async () => {
    const code = await h.store.createLinkCode({
      discordUserId: DISCORD_ID,
      discordUsername: 'voidcat',
      ttlMs: 60_000,
    });

    const agent = request.agent(h.app);
    const page = await agent.get(`/link?code=${code.code}`).set('x-test-user', 'user_link');
    expect(page.status).toBe(200);
    expect(page.text).toContain('voidcat');
    expect(page.text).toContain('link@example.com');

    const confirmed = await agent
      .post('/link')
      .set('x-test-user', 'user_link')
      .type('form')
      .send({ _csrf: csrfFrom(page.text), code: code.code });
    expect(confirmed.status).toBe(303);
    expect(confirmed.headers.location).toBe('/dashboard?linked=1');
    expect((await h.store.getAccountByClerkId('user_link'))?.discordUserId).toBe(DISCORD_ID);

    const reuse = await agent
      .post('/link')
      .set('x-test-user', 'user_link')
      .type('form')
      .send({ _csrf: csrfFrom(page.text), code: code.code });
    expect(reuse.status).toBe(410);
  });

  it('rejects an expired code and a malformed code', async () => {
    const stale = await h.store.createLinkCode({
      discordUserId: DISCORD_ID,
      discordUsername: 'voidcat',
      ttlMs: -1_000,
    });
    const expired = await request(h.app)
      .get(`/link?code=${stale.code}`)
      .set('x-test-user', 'user_link');
    expect(expired.status).toBe(410);

    const malformed = await request(h.app)
      .get('/link?code=deadbeef')
      .set('x-test-user', 'user_link');
    expect(malformed.status).toBe(400);
  });
});

describe('GitHub release proxy', () => {
  const releaseBody = {
    tag_name: 'v0.3.0',
    body: 'Notes',
    published_at: '2026-11-20T10:00:00Z',
    assets: [
      { id: 42, name: 'ei-void-0.3.0.apk', size: 1024, url: 'https://api.github.com/assets/42' },
    ],
  };

  it('serves and caches the latest release', async () => {
    const fetchImpl = vi.fn(async () => jsonResponse(releaseBody));
    const h = harness({ config: { github: { token: 'gh-token' } }, fetchImpl });

    const first = await request(h.app).get('/app/latest');
    expect(first.status).toBe(200);
    expect(first.body).toMatchObject({
      version: '0.3.0',
      apkUrl: '/app/download',
      assetName: 'ei-void-0.3.0.apk',
    });

    const second = await request(h.app).get('/api/v1/app/latest');
    expect(second.status).toBe(200);
    expect(second.body.version).toBe('0.3.0');
    expect(fetchImpl).toHaveBeenCalledTimes(1);
  });

  it('reports a missing update configuration honestly', async () => {
    const h = harness();
    const response = await request(h.app).get('/app/latest');
    expect(response.status).toBe(503);
    expect(response.body).toEqual({ error: 'update_not_configured' });
  });
});
