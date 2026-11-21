import { randomBytes } from 'node:crypto';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { clerkMiddleware } from '@clerk/express';
import { inviteUrl } from '@eivoid/discord-core';
import type { Mailer } from '@eivoid/mailer';
import compression from 'compression';
import cookieParser from 'cookie-parser';
import express, {
  type ErrorRequestHandler,
  type NextFunction,
  type Request,
  type Response,
} from 'express';
import expressLayouts from 'express-ejs-layouts';
import rateLimit from 'express-rate-limit';
import helmet from 'helmet';
import { createClerkAuth, type Auth } from './auth/clerk.js';
import type { Config } from './config.js';
import type { WebStore } from './ports.js';
import { createApiRouter } from './routes/api.js';
import { createAppReleaseRouter } from './routes/app.js';
import { createPagesRouter } from './routes/pages.js';
import { ensureCsrf, readFormSession, verifyCsrf } from './session.js';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');

export interface AppDeps {
  config: Config;
  store: WebStore;
  auth?: Auth;
  mailer: Mailer;
  /** Injectable fetch for release, Discord and Clerk HTTP calls in tests. */
  fetchImpl?: typeof fetch;
}

function errorPage(title: string, message: string) {
  return {
    title: `${title} — Ei Void`,
    description: message,
    path: '/error',
    noindex: true,
  };
}

export function createApp({
  config,
  store,
  auth = createClerkAuth(config),
  mailer,
  fetchImpl,
}: AppDeps): express.Express {
  const app = express();
  app.disable('x-powered-by');
  if (config.isProduction) app.set('trust proxy', 1);

  app.set('views', path.join(root, 'views'));
  app.set('view engine', 'ejs');
  app.use(expressLayouts);
  app.set('layout', 'layout');

  // The nonce is generated before Helmet so the CSP can quote it. Inline
  // scripts (theme choice, JSON-LD and the Clerk mount) all carry it.
  app.use((_req, res, next) => {
    res.locals.nonce = randomBytes(16).toString('base64');
    next();
  });

  const clerkConfigured = Boolean(config.clerk.secretKey && config.clerk.publishableKey);
  const clerkOrigin = config.clerk.frontendApi ? `https://${config.clerk.frontendApi}` : '';

  app.use(
    helmet({
      contentSecurityPolicy: {
        useDefaults: false,
        directives: {
          defaultSrc: ["'self'"],
          baseUri: ["'self'"],
          fontSrc: ["'self'"],
          formAction: ["'self'"],
          frameAncestors: ["'none'"],
          imgSrc: ["'self'", 'data:', ...(clerkConfigured ? ['https://img.clerk.com'] : [])],
          manifestSrc: ["'self'"],
          objectSrc: ["'none'"],
          scriptSrc: [
            "'self'",
            (_req, res) =>
              `'nonce-${((res as Response).locals.nonce as string | undefined) ?? ''}'`,
            ...(clerkConfigured && clerkOrigin ? [clerkOrigin] : []),
          ],
          scriptSrcAttr: ["'none'"],
          styleSrc: ["'self'", ...(clerkConfigured ? ["'unsafe-inline'"] : [])],
          styleSrcAttr: clerkConfigured ? ["'unsafe-inline'"] : ["'none'"],
          connectSrc: ["'self'", ...(clerkConfigured && clerkOrigin ? [clerkOrigin] : [])],
          frameSrc: [...(clerkConfigured && clerkOrigin ? [clerkOrigin] : [])],
          ...(config.isProduction ? { upgradeInsecureRequests: [] } : {}),
        },
      },
      crossOriginEmbedderPolicy: false,
      crossOriginResourcePolicy: { policy: 'same-origin' },
      referrerPolicy: { policy: 'strict-origin-when-cross-origin' },
      hsts: config.isProduction
        ? { maxAge: 15_552_000, includeSubDomains: true }
        : false,
    }),
  );
  app.use(compression());
  app.use(
    express.static(path.join(root, 'public'), {
      maxAge: config.isProduction ? '7d' : 0,
      etag: true,
      fallthrough: true,
    }),
  );
  app.use(cookieParser(config.sessionSecret));

  app.get('/healthz', (_req, res) => {
    res.json({ ok: true, version: config.appVersion });
  });

  const releaseRouter = createAppReleaseRouter({
    config,
    ...(fetchImpl ? { fetchImpl } : {}),
  });
  // Public release proxy. The API mirror lives under /api/v1/app below.
  app.use('/app', releaseRouter);

  const jsonErrorHandler: ErrorRequestHandler = (error, _req, res, next) => {
    if (res.headersSent) {
      next(error);
      return;
    }
    const status =
      typeof error === 'object' && error !== null && 'status' in error
        ? Number((error as { status?: unknown }).status)
        : 0;
    if (status === 413) {
      res.status(413).json({ error: 'bad_request', detail: 'The JSON body is too large.' });
      return;
    }
    if (error instanceof SyntaxError) {
      res.status(400).json({ error: 'bad_request', detail: 'The JSON body is invalid.' });
      return;
    }
    console.error(
      'API parser error:',
      error instanceof Error ? error.message : 'unknown error',
    );
    res.status(500).json({ error: 'internal_error' });
  };

  // The service/device API is authenticated by bearer tokens, not cookies, so
  // it is mounted before both Clerk's browser middleware and the CSRF guard.
  app.use('/api/v1/app', releaseRouter);
  app.use(
    '/api/v1',
    express.json({ limit: '64kb' }),
    jsonErrorHandler,
    createApiRouter({ config, store, auth, mailer, ...(fetchImpl ? { fetchImpl } : {}) }),
  );
  app.use('/api/v1', (_req, res) => {
    res.status(404).json({ error: 'not_found' });
  });

  // Browser sessions come from Clerk's middleware. It is only mounted when a
  // Clerk instance is configured; tests inject a fake auth implementation.
  if (clerkConfigured) {
    app.use(
      clerkMiddleware({
        secretKey: config.clerk.secretKey,
        publishableKey: config.clerk.publishableKey,
      }),
    );
  }

  app.use(express.urlencoded({ extended: false, limit: '32kb' }));

  app.use((req, res, next) => {
    const invite = inviteUrl({
      applicationId: config.discord.applicationId,
      permissions: config.discord.invitePermissions,
      ...(config.discord.guildId ? { guildId: config.discord.guildId } : {}),
    });
    res.locals.site = {
      name: 'Ei Void',
      tagline: 'Reminders and a coding agent, in one app.',
      baseUrl: config.baseUrl,
      repoUrl: `https://github.com/${config.github.repo}`,
      releasesUrl: config.releasesUrl,
      downloadUrl: config.downloadUrl,
      discordInviteUrl: invite,
      discordApplicationId: config.discord.applicationId,
      discordGuildId: config.discord.guildId,
      version: config.appVersion,
      contactEmail: config.contactEmail,
      signInUrl: config.signInUrl,
      clerkEnabled: clerkConfigured,
      clerkFrontendApi: config.clerk.frontendApi,
      clerkPublishableKey: config.clerk.publishableKey,
    };
    res.locals.formSession = readFormSession(req);
    res.locals.currentPath = req.path;
    res.locals.year = new Date().getFullYear();
    next();
  });

  const tooMany = (req: Request, res: Response) => {
    res.status(429).render('error', {
      page: errorPage('Slow down', 'Too many attempts. Wait a few minutes and try again.'),
      status: 429,
      message: 'Too many attempts. Wait a few minutes and try again.',
    });
  };

  const waitlistLimiter = rateLimit({
    windowMs: 10 * 60_000,
    limit: 20,
    standardHeaders: 'draft-7',
    legacyHeaders: false,
    skip: () => config.env === 'test',
    handler: tooMany,
  });
  const linkLimiter = rateLimit({
    windowMs: 10 * 60_000,
    limit: 30,
    standardHeaders: 'draft-7',
    legacyHeaders: false,
    skip: () => config.env === 'test',
    handler: tooMany,
  });

  app.use((req, res, next) => {
    res.locals.csrfToken = ensureCsrf(req, res, config);
    if (req.method === 'POST' && !verifyCsrf(req)) {
      res.status(403).render('error', {
        page: errorPage('Form expired', 'This form expired or the CSRF token did not match.'),
        status: 403,
        message: 'This form expired. Go back, reload the page, and try again.',
      });
      return;
    }
    next();
  });

  app.use(
    createPagesRouter({
      config,
      store,
      auth,
      waitlistLimiter,
      linkLimiter,
    }),
  );

  app.use((_req, res) => {
    res.status(404).render('error', {
      page: errorPage('Not found', 'That page is not here.'),
      status: 404,
      message: 'That page is not here. The cat may have pushed it into the void.',
    });
  });

  app.use((error: Error, _req: Request, res: Response, next: NextFunction) => {
    if (res.headersSent) {
      next(error);
      return;
    }
    console.error('Website error:', error instanceof Error ? error.message : 'unknown error');
    res.status(500).render('error', {
      page: errorPage('Something broke', 'A server error interrupted that page.'),
      status: 500,
      message: config.isProduction
        ? 'Something broke on our side. Try again in a minute.'
        : error.message,
    });
  });

  return app;
}
