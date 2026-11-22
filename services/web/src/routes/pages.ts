import {
  Router,
  type Request,
  type RequestHandler,
  type Response,
} from 'express';
import { marked } from 'marked';
import { z } from 'zod';
import { planLimit } from '@eivoid/contracts';
import type {
  Account,
  DeviceKeyRecord,
  ReminderSummary,
  UsageSnapshotRecord,
} from '@eivoid/db';
import { requireUser, type Auth, type AuthUser } from '../auth/clerk.js';
import type { Config } from '../config.js';
import {
  changelog,
  docs,
  faqs,
  features,
  plans,
  steps,
  tiers,
} from '../content.js';
import { clearFormSession, writeFormSession } from '../session.js';
import type { WebStore } from '../ports.js';

export interface PagesDeps {
  config: Config;
  store: WebStore;
  auth: Auth;
  waitlistLimiter: RequestHandler;
  linkLimiter: RequestHandler;
}

interface PageMeta {
  title: string;
  description: string;
  path: string;
  ogImage?: string;
  noindex?: boolean;
  bodyClass?: string;
  jsonLd?: string;
}

type AsyncHandler = (req: Request, res: Response) => Promise<void>;

function asyncHandler(handler: AsyncHandler): RequestHandler {
  return (req, res, next) => {
    handler(req, res).catch(next);
  };
}

const BRAND = 'Ei Void';
const CODE_PATTERN = /^[a-f0-9]{48}$/;
const waitlistSchema = z.string().trim().toLowerCase().email().max(254);

function meta(
  title: string,
  description: string,
  pathname: string,
  extra: Partial<PageMeta> = {},
): PageMeta {
  return { title: `${title} — ${BRAND}`, description, path: pathname, ...extra };
}

function authUser(res: Response): AuthUser {
  return res.locals.authUser as AuthUser;
}

function percentOf(used: number, limit: number): number {
  if (limit <= 0) return 0;
  return Math.max(0, Math.min(100, Math.round((used / limit) * 100)));
}

interface DashboardData {
  page: PageMeta;
  user: AuthUser;
  account: Account | null;
  usage: UsageSnapshotRecord | null;
  plan: string;
  limit: number;
  percent: number;
  synced: boolean;
  discord: { id: string; username: string } | null;
  reminders: ReminderSummary;
  devices: DeviceKeyRecord[];
  newDeviceKey: string | null;
  notice: string;
}

const EMPTY_SUMMARY: ReminderSummary = {
  total: 0,
  active: 0,
  paid: 0,
  nextDueAt: null,
};

async function loadDashboard(
  store: WebStore,
  user: AuthUser,
  options: { newDeviceKey?: string | null; linked?: boolean } = {},
): Promise<DashboardData> {
  const [account, usage, reminders, devices] = await Promise.all([
    store.getAccountByClerkId(user.userId),
    store.latestUsage(user.userId),
    store.reminderSummary(user.userId),
    store.listDeviceKeys(user.userId),
  ]);

  const plan = usage?.plan ?? account?.plan ?? 'Free';
  const limit = usage?.tokensLimit ?? planLimit(plan);
  const used = usage?.tokensUsed ?? 0;

  return {
    page: meta('Dashboard', 'Your Ei Void account, reminders, plan and devices.', '/dashboard', {
      noindex: true,
      bodyClass: 'dashboard-page',
    }),
    user,
    account,
    usage,
    plan,
    limit,
    percent: percentOf(used, limit),
    synced: usage !== null,
    discord:
      account?.discordUserId
        ? { id: account.discordUserId, username: account.discordUsername }
        : null,
    reminders: reminders ?? EMPTY_SUMMARY,
    devices,
    newDeviceKey: options.newDeviceKey ?? null,
    notice: options.linked ? 'Discord linked. Welcome back.' : '',
  };
}

function renderDashboard(
  res: Response,
  store: WebStore,
  user: AuthUser,
  options: { newDeviceKey?: string | null; linked?: boolean } = {},
): Promise<void> {
  return loadDashboard(store, user, options).then((data) => {
    res.set('Cache-Control', 'no-store');
    res.render('dashboard', data);
  });
}

function escapeXml(value: string): string {
  return value
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&apos;');
}

export function createPagesRouter({
  config,
  store,
  auth,
  waitlistLimiter,
  linkLimiter,
}: PagesDeps): Router {
  const router = Router();
  const pageUser = requireUser(auth, { mode: 'page' });

  router.get('/', (_req, res) => {
    const page: PageMeta = {
      title: 'Ei Void — reminders and a coding agent in one app',
      description:
        'Ei Void keeps bill reminders and an on-device coding agent in one Android app, with one Discord bot and one website.',
      path: '/',
      bodyClass: 'home-page',
      jsonLd: JSON.stringify({
        '@context': 'https://schema.org',
        '@type': 'SoftwareApplication',
        name: BRAND,
        applicationCategory: 'DeveloperApplication',
        operatingSystem: 'Android 13+',
        description:
          'A native Android reminders and coding-agent app with real git, guarded tools and Clerk sign-in.',
        offers: { '@type': 'Offer', price: '0', priceCurrency: 'INR' },
      }).replaceAll('<', '\\u003c'),
    };
    res.render('home', {
      page,
      features,
      tiers,
      plans,
      faqs,
      steps,
      docs: docs.slice(0, 3),
    });
  });

  router.get('/features', (_req, res) => {
    res.render('features', {
      page: meta(
        'Features',
        'Real git, guarded tools, tiered models and a phone-native workspace.',
        '/features',
      ),
      features,
      tiers,
      steps,
    });
  });

  router.get('/reminders', (_req, res) => {
    res.render('reminders', {
      page: meta(
        'Reminders',
        'Bill reminders that stay on your phone and reach Discord or email when one is due.',
        '/reminders',
        { bodyClass: 'reminders-page' },
      ),
      plans,
      steps,
    });
  });

  router.get('/agent', (_req, res) => {
    res.render('agent', {
      page: meta(
        'Coding agent',
        'Clone, edit, review and push a repository from your phone through guarded tools.',
        '/agent',
        { bodyClass: 'agent-page' },
      ),
      features,
      tiers,
      steps,
    });
  });

  router.get('/pricing', (_req, res) => {
    res.render('pricing', {
      page: meta(
        'Plans',
        'Ei Void plans are a developer-preview roadmap. No billing is live yet.',
        '/pricing',
      ),
      plans,
      tiers,
      faqs,
    });
  });

  router.get('/bot', (_req, res) => {
    res.render('bot', {
      page: meta(
        'Discord companion',
        'Link Discord to Ei Void, check reminders and usage, and keep the bot token server-side.',
        '/bot',
        { bodyClass: 'bot-page' },
      ),
      botDocs: docs.filter((entry) => ['discord-bot', 'api', 'self-hosting'].includes(entry.slug)),
    });
  });

  router.get('/download', (_req, res) => {
    res.render('download', {
      page: meta(
        'Download',
        'Build Ei Void from source, or download the signed developer preview when it is published.',
        '/download',
      ),
      version: config.appVersion,
      changelog,
    });
  });

  router.get(
    '/security',
    asyncHandler(async (_req, res) => {
      const securityDoc = docs.find((entry) => entry.slug === 'security');
      const html = securityDoc ? await marked.parse(securityDoc.body) : '';
      res.render('security', {
        page: meta(
          'Security',
          'The Ei Void threat model: keys on the phone, a bounded workspace, and a bot token that never reaches the browser.',
          '/security',
        ),
        html,
        securityDoc,
      });
    }),
  );

  router.get('/privacy', (_req, res) => {
    res.render('privacy', {
      page: meta(
        'Privacy',
        'What Ei Void stores across reminders, the coding agent and this website.',
        '/privacy',
      ),
    });
  });

  router.get('/terms', (_req, res) => {
    res.render('terms', {
      page: meta('Terms', 'The terms for the Ei Void developer preview.', '/terms'),
    });
  });

  router.get('/changelog', (_req, res) => {
    res.render('changelog', {
      page: meta('Changelog', 'What changed in Ei Void.', '/changelog'),
      changelog,
    });
  });

  router.get('/thanks', (_req, res) => {
    res.render('thanks', {
      page: meta('You are on the list', 'The Ei Void preview list confirmation.', '/thanks', {
        noindex: true,
      }),
    });
  });

  router.get('/discord-callback', (req, res) => {
    // The Android WebView intercepts this URL before it is fetched. The page
    // exists so the redirect URI is a real HTTPS destination and so a browser
    // that reaches it gets a clear next step.
    res.set('Cache-Control', 'no-store');
    res.render('discord-callback', {
      page: meta(
        'Discord linking',
        'Return to the Ei Void app to finish linking Discord.',
        '/discord-callback',
        { noindex: true },
      ),
      code: typeof req.query.code === 'string' ? req.query.code : '',
    });
  });

  router.get('/docs', (_req, res) => {
    res.render('docs/index', {
      page: meta(
        'Docs',
        'Build Ei Void, deploy the GitHub bridge, and run the Discord companion.',
        '/docs',
        { bodyClass: 'docs-page' },
      ),
      docs,
    });
  });

  router.get(
    '/docs/:slug',
    asyncHandler(async (req, res) => {
      const doc = docs.find((entry) => entry.slug === req.params.slug);
      if (!doc) {
        res.status(404).render('error', {
          page: meta('Not found', 'That document is not here.', '/docs', {
            noindex: true,
          }),
          status: 404,
          message: 'That document is not here. Try the documentation index.',
        });
        return;
      }
      const index = docs.indexOf(doc);
      const previous = index > 0 ? docs[index - 1] ?? null : null;
      const following = index < docs.length - 1 ? docs[index + 1] ?? null : null;
      res.render('docs/doc', {
        page: meta(doc.title, doc.description, `/docs/${doc.slug}`, {
          bodyClass: 'docs-page',
        }),
        doc,
        html: await marked.parse(doc.body),
        docs,
        previous,
        next: following,
      });
    }),
  );

  // ---- Clerk sign-in -------------------------------------------------------

  router.get('/sign-in', (req, res) => {
    const requested = typeof req.query.redirect_url === 'string' ? req.query.redirect_url : '';
    const redirectUrl =
      requested.startsWith('/') && !requested.startsWith('//') ? requested : '/dashboard';
    res.set('Cache-Control', 'no-store');
    res.render('sign-in', {
      page: meta('Sign in', 'Sign in to the Ei Void dashboard.', '/sign-in', {
        noindex: true,
        bodyClass: 'sign-in-page',
      }),
      redirectUrl,
      clerkEnabled: Boolean(config.clerk.publishableKey && config.clerk.frontendApi),
      clerkPublishableKey: config.clerk.publishableKey,
      clerkFrontendApi: config.clerk.frontendApi,
    });
  });

  // ---- dashboard -----------------------------------------------------------

  router.get(
    '/dashboard',
    pageUser,
    asyncHandler(async (req, res) => {
      await renderDashboard(res, store, authUser(res), {
        linked: req.query.linked === '1',
      });
    }),
  );

  router.post(
    '/dashboard/devices',
    linkLimiter,
    pageUser,
    asyncHandler(async (req, res) => {
      const user = authUser(res);
      const issued = await store.issueDeviceKey(user.userId);
      await renderDashboard(res, store, user, { newDeviceKey: issued.key });
    }),
  );

  router.post(
    '/dashboard/devices/:id/revoke',
    linkLimiter,
    pageUser,
    asyncHandler(async (req, res) => {
      const raw = req.params.id ?? '';
      if (/^\d{1,12}$/.test(raw)) {
        await store.revokeDeviceKeyForUser(Number(raw), authUser(res).userId);
      }
      res.redirect(303, '/dashboard');
    }),
  );

  router.post(
    '/dashboard/discord/unlink',
    linkLimiter,
    pageUser,
    asyncHandler(async (req, res) => {
      await store.unlinkDiscord(authUser(res).userId);
      res.redirect(303, '/dashboard');
    }),
  );

  router.post(
    '/dashboard/delete',
    linkLimiter,
    pageUser,
    asyncHandler(async (req, res) => {
      await store.deleteAccount(authUser(res).userId);
      clearFormSession(res, config);
      res.redirect(303, '/?deleted=1');
    }),
  );

  router.post('/logout', (req, res) => {
    clearFormSession(res, config);
    res.redirect(303, '/');
  });

  // ---- Discord link confirmation ------------------------------------------

  router.get(
    '/link',
    pageUser,
    asyncHandler(async (req, res) => {
      const code = typeof req.query.code === 'string' ? req.query.code : '';
      if (!CODE_PATTERN.test(code)) {
        res.status(400).render('error', {
          page: meta('Link Discord', 'That link is incomplete.', '/link', {
            noindex: true,
          }),
          status: 400,
          message: 'That link is incomplete. Run /link in Discord again.',
          linkHint: true,
        });
        return;
      }
      const found = await store.getLinkCode(code);
      if (!found) {
        res.status(404).render('error', {
          page: meta('Link Discord', 'That link was not found.', '/link', {
            noindex: true,
          }),
          status: 404,
          message: 'That link was not found. Run /link in Discord again.',
          linkHint: true,
        });
        return;
      }
      if (found.usedAt !== null || found.expiresAt.getTime() <= Date.now()) {
        res.status(410).render('error', {
          page: meta('Link expired', 'That Discord link expired.', '/link', {
            noindex: true,
          }),
          status: 410,
          message: 'That link is single-use and has expired. Run /link in Discord again.',
          linkHint: true,
        });
        return;
      }
      res.set('Cache-Control', 'no-store');
      res.render('link', {
        page: meta('Link Discord', 'Confirm this Discord account for Ei Void.', '/link', {
          noindex: true,
          bodyClass: 'link-page',
        }),
        code: found.code,
        discordUsername: found.discordUsername,
        expiresAt: found.expiresAt,
        accountEmail: authUser(res).email,
      });
    }),
  );

  router.post(
    '/link',
    linkLimiter,
    pageUser,
    asyncHandler(async (req, res) => {
      const code = typeof req.body.code === 'string' ? req.body.code : '';
      if (!CODE_PATTERN.test(code)) {
        res.status(400).render('error', {
          page: meta('Link Discord', 'That link is incomplete.', '/link', {
            noindex: true,
          }),
          status: 400,
          message: 'That link is incomplete. Run /link in Discord again.',
          linkHint: true,
        });
        return;
      }
      const confirmed = await store.confirmLinkCode(code);
      if (!confirmed) {
        res.status(410).render('error', {
          page: meta('Link expired', 'That Discord link expired.', '/link', {
            noindex: true,
          }),
          status: 410,
          message: 'That link is single-use and has expired. Run /link in Discord again.',
          linkHint: true,
        });
        return;
      }
      const user = authUser(res);
      await store.linkDiscord(user.userId, {
        id: confirmed.discordUserId,
        username: confirmed.discordUsername,
      });
      writeFormSession(res, config, { clerkUserId: user.userId });
      res.redirect(303, '/dashboard?linked=1');
    }),
  );

  // ---- waitlist ------------------------------------------------------------

  router.post(
    '/waitlist',
    waitlistLimiter,
    asyncHandler(async (req, res) => {
      const body: unknown = req.body;
      const email =
        typeof (body as Record<string, unknown> | undefined)?.email === 'string'
          ? ((body as Record<string, unknown>).email as string)
          : '';
      const parsed = waitlistSchema.safeParse(email);
      if (!parsed.success) {
        res.status(400).render('error', {
          page: meta('Check that email', 'Enter a valid email address.', '/', {
            noindex: true,
          }),
          status: 400,
          message: 'That email address does not look right. Go back and try again.',
        });
        return;
      }
      await store.addWaitlistEmail(parsed.data);
      res.redirect(303, '/thanks');
    }),
  );

  // ---- discovery -----------------------------------------------------------

  router.get('/sitemap.xml', (_req, res) => {
    const today = new Date().toISOString().slice(0, 10);
    const paths = [
      '/',
      '/reminders',
      '/agent',
      '/features',
      '/pricing',
      '/bot',
      '/download',
      '/security',
      '/docs',
      ...docs.map((entry) => `/docs/${entry.slug}`),
      '/changelog',
      '/privacy',
      '/terms',
    ];
    const urls = paths
      .map(
        (path) =>
          `<url><loc>${escapeXml(config.baseUrl + path)}</loc><lastmod>${today}</lastmod></url>`,
      )
      .join('');
    res
      .type('application/xml')
      .send(
        `<?xml version="1.0" encoding="UTF-8"?><urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">${urls}</urlset>`,
      );
  });

  router.get('/robots.txt', (_req, res) => {
    res
      .type('text/plain')
      .send(
        `User-agent: *\nAllow: /\nDisallow: /dashboard\nDisallow: /link\nDisallow: /sign-in\nDisallow: /thanks\nDisallow: /api/\nSitemap: ${config.baseUrl}/sitemap.xml\n`,
      );
  });

  return router;
}
