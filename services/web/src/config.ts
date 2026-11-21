import 'dotenv/config';
import {
  ConfigError,
  clerkFrontendApiFromPublishableKey,
  loadConfig as loadBaseConfig,
  type Config as BaseConfig,
  type DeepPartial,
} from '@eivoid/config';

export { ConfigError };

export type VoidEnv = BaseConfig['env'];

/**
 * The web service's configuration is the shared configuration plus the values
 * that only the public website needs. Everything else — Clerk, Discord, mail,
 * GitHub, the database URL, the session secret and the version — comes from
 * `@eivoid/config` so the bot and the website cannot drift apart.
 */
export interface Config extends BaseConfig {
  /** Service token for trusted first-party callers. Never exposed to browsers. */
  readonly botApiToken: string | null;
  readonly contactEmail: string | null;
  /** Explicit APK URL; when absent the download page points at releases. */
  readonly downloadUrl: string | null;
  readonly releasesUrl: string;
  /** The website's own Clerk sign-in page. */
  readonly signInUrl: string;
  /** Convenience mirrors of the shared values used by templates. */
  readonly githubRepo: string;
  readonly databaseUrl: string | null;
  readonly discordApplicationId: string;
  readonly discordGuildId: string;
}

const DEV_APPLICATION_ID = '1573240846223151144';
const DEV_GUILD_ID = '848841415940898827';
const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

function absoluteHttpUrl(value: string, name: string): string {
  let parsed: URL;
  try {
    parsed = new URL(value);
  } catch {
    throw new ConfigError(`${name} must be a valid http(s) URL.`);
  }
  if (parsed.protocol !== 'https:' && parsed.protocol !== 'http:') {
    throw new ConfigError(`${name} must be a valid http(s) URL.`);
  }
  if (parsed.username || parsed.password) {
    throw new ConfigError(`${name} must not contain credentials.`);
  }
  return parsed.toString().replace(/\/+$/, '');
}

function optionalEmail(value: string): string | null {
  const trimmed = value.trim();
  if (!trimmed) return null;
  if (trimmed.length > 254 || !EMAIL_PATTERN.test(trimmed)) {
    throw new ConfigError('CONTACT_EMAIL must be an email address.');
  }
  return trimmed.toLowerCase();
}

function freeze(config: Config): Config {
  return Object.freeze({
    ...config,
    clerk: Object.freeze({ ...config.clerk }),
    discord: Object.freeze({ ...config.discord }),
    mail: Object.freeze({
      ...config.mail,
      gmail: Object.freeze({ ...config.mail.gmail }),
      smtp: Object.freeze({ ...config.mail.smtp }),
    }),
    github: Object.freeze({ ...config.github }),
  });
}

/**
 * Parses the shared configuration, then the website-only fields. Every error
 * names a variable and never echoes its value.
 */
export function loadConfig(env: NodeJS.ProcessEnv = process.env): Config {
  const base = loadBaseConfig(env);

  const botApiToken = env.BOT_API_TOKEN?.trim() || null;
  if (base.isProduction && botApiToken !== null && botApiToken.length < 32) {
    throw new ConfigError('BOT_API_TOKEN must be at least 32 characters in production.');
  }

  const downloadUrl = env.DOWNLOAD_URL?.trim() || null;
  if (downloadUrl !== null && !downloadUrl.startsWith('https://')) {
    throw new ConfigError('DOWNLOAD_URL must use https.');
  }

  const releasesUrl = absoluteHttpUrl(
    env.RELEASES_URL?.trim() || `https://github.com/${base.github.repo}/releases`,
    'RELEASES_URL',
  );
  if (base.isProduction && !releasesUrl.startsWith('https://')) {
    throw new ConfigError('RELEASES_URL must use https in production.');
  }

  const signInUrl = absoluteHttpUrl(
    env.FRONTEND_SIGN_IN_URL?.trim() || `${base.baseUrl}/sign-in`,
    'FRONTEND_SIGN_IN_URL',
  );

  // The shared package deliberately leaves the Discord ids empty so a
  // deployment must opt in. The website keeps the historical home-guild
  // defaults so the public invite and the membership check remain coherent.
  const discordApplicationId = base.discord.applicationId || DEV_APPLICATION_ID;
  const discordGuildId = base.discord.guildId || DEV_GUILD_ID;

  return freeze({
    ...base,
    discord: Object.freeze({
      ...base.discord,
      applicationId: discordApplicationId,
      guildId: discordGuildId,
    }),
    botApiToken,
    contactEmail: optionalEmail(env.CONTACT_EMAIL ?? ''),
    downloadUrl,
    releasesUrl,
    signInUrl,
    githubRepo: base.github.repo,
    databaseUrl: base.databaseUrl,
    discordApplicationId,
    discordGuildId,
  });
}

/** Builds a frozen website config for tests without production variables. */
export function makeTestConfig(overrides: DeepPartial<Config> = {}): Config {
  const base = loadConfig({
    NODE_ENV: 'test',
    PUBLIC_BASE_URL: 'http://localhost:3000',
    SESSION_SECRET: 'test-session-secret-that-is-long-enough',
  });

  const clerk = {
    ...base.clerk,
    ...overrides.clerk,
  };
  if (clerk.publishableKey && !clerk.frontendApi) {
    const host = clerkFrontendApiFromPublishableKey(clerk.publishableKey);
    if (host) {
      clerk.frontendApi = host;
      if (!clerk.issuer) clerk.issuer = `https://${host}`;
    }
  }

  return freeze({
    ...base,
    ...overrides,
    clerk,
    discord: {
      ...base.discord,
      ...overrides.discord,
    },
    mail: {
      ...base.mail,
      ...overrides.mail,
      gmail: {
        ...base.mail.gmail,
        ...overrides.mail?.gmail,
      },
      smtp: {
        ...base.mail.smtp,
        ...overrides.mail?.smtp,
      },
    },
    github: {
      ...base.github,
      ...overrides.github,
    },
  });
}
