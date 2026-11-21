export type AppEnv = 'development' | 'production' | 'test';

export interface ClerkConfig {
  readonly secretKey: string;
  readonly publishableKey: string;
  readonly issuer: string;
  readonly frontendApi: string;
}

export interface DiscordConfig {
  readonly token: string;
  readonly applicationId: string;
  readonly guildId: string;
  readonly fallbackChannelId: string;
  readonly invitePermissions: number;
}

export interface GmailConfig {
  readonly clientId: string;
  readonly clientSecret: string;
  readonly refreshToken: string;
}

export interface SmtpConfig {
  readonly host: string;
  readonly port: number;
  readonly user: string;
  readonly pass: string;
}

export type MailProvider = 'gmail' | 'brevo' | 'sendgrid' | 'smtp';

export interface MailConfig {
  readonly provider: MailProvider | null;
  readonly from: string;
  readonly fromName: string;
  readonly gmail: GmailConfig;
  readonly brevoApiKey: string;
  readonly sendgridApiKey: string;
  readonly smtp: SmtpConfig;
}

export interface GitHubConfig {
  readonly repo: string;
  readonly token: string;
}

export interface Config {
  readonly env: AppEnv;
  readonly isProduction: boolean;
  readonly port: number;
  readonly baseUrl: string;
  readonly sessionSecret: string;
  readonly clerk: ClerkConfig;
  readonly databaseUrl: string | null;
  readonly discord: DiscordConfig;
  readonly mail: MailConfig;
  readonly github: GitHubConfig;
  readonly appVersion: string;
}

export type DeepPartial<T> = {
  [Key in keyof T]?: T[Key] extends object ? DeepPartial<T[Key]> : T[Key];
};

export class ConfigError extends Error {
  override name = 'ConfigError';

  constructor(message: string) {
    super(message);
  }
}

const DEV_SESSION_SECRET = 'dev-only-session-secret-change-me-please';
const SNOWFLAKE = /^\d{16,22}$/;
const GITHUB_REPO = /^[A-Za-z0-9_.-]+\/[A-Za-z0-9_.-]+$/;
const DEFAULT_GITHUB_REPO = 'xyanncat/Ei-Void';
const DEFAULT_INVITE_PERMISSIONS = 3072;
const DEFAULT_APP_VERSION = '0.1.0';
const MAIL_PROVIDERS: readonly MailProvider[] = ['gmail', 'brevo', 'sendgrid', 'smtp'];

function optionalText(env: NodeJS.ProcessEnv, name: string): string {
  return env[name]?.trim() ?? '';
}

function parsePort(value: string, name: string, fallback: number): number {
  if (!value) return fallback;
  const port = Number(value);
  if (!Number.isInteger(port) || port < 1 || port > 65535) {
    throw new ConfigError(`${name} must be an integer between 1 and 65535.`);
  }
  return port;
}

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

function frontendApiHost(value: string, name: string): string {
  const trimmed = value.trim();
  if (!trimmed) return '';
  let parsed: URL;
  try {
    parsed = new URL(/^[a-z][a-z0-9+.-]*:\/\//i.test(trimmed) ? trimmed : `https://${trimmed}`);
  } catch {
    throw new ConfigError(`${name} must be a valid hostname or URL.`);
  }
  if (parsed.protocol !== 'https:' && parsed.protocol !== 'http:') {
    throw new ConfigError(`${name} must be a valid hostname or URL.`);
  }
  if (parsed.username || parsed.password) {
    throw new ConfigError(`${name} must not contain credentials.`);
  }
  return parsed.host;
}

function postgresUrl(value: string): string {
  let parsed: URL;
  try {
    parsed = new URL(value);
  } catch {
    throw new ConfigError('DATABASE_URL must be a valid postgres:// or postgresql:// URL.');
  }
  if (parsed.protocol !== 'postgres:' && parsed.protocol !== 'postgresql:') {
    throw new ConfigError('DATABASE_URL must be a valid postgres:// or postgresql:// URL.');
  }
  return value;
}

function discordSnowflake(value: string, name: string): string {
  if (value && !SNOWFLAKE.test(value)) {
    throw new ConfigError(`${name} must be a Discord snowflake.`);
  }
  return value;
}

function invitePermissions(value: string): number {
  if (!value) return DEFAULT_INVITE_PERMISSIONS;
  if (!/^\d{1,20}$/.test(value)) {
    throw new ConfigError('DISCORD_INVITE_PERMISSIONS must be a decimal permissions integer.');
  }
  const permissions = Number(value);
  if (!Number.isSafeInteger(permissions) || permissions < 0) {
    throw new ConfigError('DISCORD_INVITE_PERMISSIONS must be a decimal permissions integer.');
  }
  return permissions;
}

function mailFrom(value: string): string {
  if (!value) return '';
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value)) {
    throw new ConfigError('MAIL_FROM must be an email address.');
  }
  return value;
}

interface MailSettings {
  from: string;
  fromName: string;
  gmail: GmailConfig;
  brevoApiKey: string;
  sendgridApiKey: string;
  smtp: SmtpConfig;
}

interface MailRequirement {
  provider: MailProvider;
  variables: readonly string[];
  configured: boolean;
}

function mailRequirement(provider: MailProvider, mail: MailSettings): MailRequirement {
  if (provider === 'gmail') {
    const variables = [
      ...(mail.from ? [] : ['MAIL_FROM']),
      ...(mail.gmail.clientId ? [] : ['GMAIL_CLIENT_ID']),
      ...(mail.gmail.clientSecret ? [] : ['GMAIL_CLIENT_SECRET']),
      ...(mail.gmail.refreshToken ? [] : ['GMAIL_REFRESH_TOKEN']),
    ];
    return { provider, variables, configured: variables.length === 0 };
  }
  if (provider === 'brevo') {
    const variables = [
      ...(mail.from ? [] : ['MAIL_FROM']),
      ...(mail.brevoApiKey ? [] : ['BREVO_API_KEY']),
    ];
    return { provider, variables, configured: variables.length === 0 };
  }
  if (provider === 'sendgrid') {
    const variables = [
      ...(mail.from ? [] : ['MAIL_FROM']),
      ...(mail.sendgridApiKey ? [] : ['SENDGRID_API_KEY']),
    ];
    return { provider, variables, configured: variables.length === 0 };
  }
  const variables = [
    ...(mail.from ? [] : ['MAIL_FROM']),
    ...(mail.smtp.host ? [] : ['SMTP_HOST']),
    ...(mail.smtp.user ? [] : ['SMTP_USER']),
    ...(mail.smtp.pass ? [] : ['SMTP_PASS']),
  ];
  return { provider, variables, configured: variables.length === 0 };
}

function resolveMailProvider(
  requested: string,
  mail: MailSettings,
  isProduction: boolean,
): MailProvider | null {
  const normalised = requested.trim().toLowerCase();
  if (normalised && !MAIL_PROVIDERS.includes(normalised as MailProvider)) {
    throw new ConfigError(
      'MAIL_PROVIDER must be one of gmail, brevo, sendgrid or smtp.',
    );
  }

  if (normalised) {
    const provider = normalised as MailProvider;
    const requirement = mailRequirement(provider, mail);
    if (!requirement.configured && isProduction) {
      throw new ConfigError(
        `MAIL_PROVIDER=${provider} is missing: ${requirement.variables.join(', ')}.`,
      );
    }
    return requirement.configured ? provider : null;
  }

  for (const provider of ['gmail', 'brevo', 'sendgrid', 'smtp'] as const) {
    if (mailRequirement(provider, mail).configured) return provider;
  }
  return null;
}

/**
 * Clerk publishable keys are `pk_test_` or `pk_live_` followed by base64 of
 * the frontend API host and a trailing `$`.
 */
export function clerkFrontendApiFromPublishableKey(publishableKey: string): string {
  const match = /^pk_(?:test|live)_(.+)$/.exec(String(publishableKey || '').trim());
  if (!match) return '';
  try {
    const decoded = Buffer.from(match[1] ?? '', 'base64').toString('utf8');
    return decoded.endsWith('$') ? decoded.slice(0, -1) : decoded;
  } catch {
    return '';
  }
}

export function clerkIssuerFromPublishableKey(publishableKey: string): string {
  const host = clerkFrontendApiFromPublishableKey(publishableKey);
  return host ? `https://${host}` : '';
}

function freezeConfig(config: Config): Config {
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
 * Parses and validates configuration. Errors name the offending variable but
 * never include its value, so a failure is safe to log.
 */
export function loadConfig(env: NodeJS.ProcessEnv = process.env): Config {
  const envName: AppEnv =
    env.NODE_ENV === 'production'
      ? 'production'
      : env.NODE_ENV === 'test'
        ? 'test'
        : 'development';
  const isProduction = envName === 'production';

  const port = parsePort(optionalText(env, 'PORT'), 'PORT', 3000);
  const configuredBaseUrl =
    optionalText(env, 'PUBLIC_BASE_URL') ||
    optionalText(env, 'RENDER_EXTERNAL_URL') ||
    `http://localhost:${port}`;
  const baseUrl = absoluteHttpUrl(configuredBaseUrl, 'PUBLIC_BASE_URL');
  if (isProduction && !baseUrl.startsWith('https://')) {
    throw new ConfigError('PUBLIC_BASE_URL must use https in production.');
  }

  const configuredSecret = optionalText(env, 'SESSION_SECRET');
  if (isProduction && configuredSecret.length < 32) {
    throw new ConfigError('SESSION_SECRET must be at least 32 characters in production.');
  }
  const sessionSecret = configuredSecret || DEV_SESSION_SECRET;

  const secretKey = optionalText(env, 'CLERK_SECRET_KEY');
  if (secretKey && !secretKey.startsWith('sk_')) {
    throw new ConfigError('CLERK_SECRET_KEY must be a Clerk secret key.');
  }
  const publishableKey = optionalText(env, 'CLERK_PUBLISHABLE_KEY');
  if (publishableKey && !/^pk_(test|live)_.+$/.test(publishableKey)) {
    throw new ConfigError('CLERK_PUBLISHABLE_KEY must be a Clerk publishable key.');
  }

  const frontendFromKey = clerkFrontendApiFromPublishableKey(publishableKey);
  if (publishableKey && !frontendFromKey) {
    throw new ConfigError('CLERK_PUBLISHABLE_KEY does not encode a frontend API host.');
  }
  const configuredFrontendApi = optionalText(env, 'CLERK_FRONTEND_API');
  const frontendApi = configuredFrontendApi
    ? frontendApiHost(configuredFrontendApi, 'CLERK_FRONTEND_API')
    : frontendFromKey;

  const configuredIssuer = optionalText(env, 'CLERK_ISSUER');
  const issuerCandidate =
    configuredIssuer ||
    clerkIssuerFromPublishableKey(publishableKey) ||
    (frontendApi ? `https://${frontendApi}` : '');
  const issuer = issuerCandidate ? absoluteHttpUrl(issuerCandidate, 'CLERK_ISSUER') : '';
  if (isProduction && issuer && !issuer.startsWith('https://')) {
    throw new ConfigError('CLERK_ISSUER must use https in production.');
  }
  if (secretKey && !issuer) {
    throw new ConfigError(
      'CLERK_ISSUER or CLERK_PUBLISHABLE_KEY is required when CLERK_SECRET_KEY is set.',
    );
  }
  const resolvedFrontendApi = frontendApi || (issuer ? new URL(issuer).host : '');

  const databaseUrl = optionalText(env, 'DATABASE_URL');
  const validatedDatabaseUrl = databaseUrl ? postgresUrl(databaseUrl) : null;

  const applicationId = discordSnowflake(
    optionalText(env, 'DISCORD_APPLICATION_ID') || optionalText(env, 'DISCORD_CLIENT_ID'),
    'DISCORD_APPLICATION_ID',
  );
  const guildId = discordSnowflake(optionalText(env, 'DISCORD_GUILD_ID'), 'DISCORD_GUILD_ID');
  const fallbackChannelId = discordSnowflake(
    optionalText(env, 'DISCORD_FALLBACK_CHANNEL_ID'),
    'DISCORD_FALLBACK_CHANNEL_ID',
  );

  const mail: MailSettings = {
    from: mailFrom(optionalText(env, 'MAIL_FROM')),
    fromName: optionalText(env, 'MAIL_FROM_NAME') || 'Ei Void',
    gmail: {
      clientId: optionalText(env, 'GMAIL_CLIENT_ID'),
      clientSecret: optionalText(env, 'GMAIL_CLIENT_SECRET'),
      refreshToken: optionalText(env, 'GMAIL_REFRESH_TOKEN'),
    },
    brevoApiKey: optionalText(env, 'BREVO_API_KEY'),
    sendgridApiKey: optionalText(env, 'SENDGRID_API_KEY'),
    smtp: {
      host: optionalText(env, 'SMTP_HOST'),
      port: parsePort(optionalText(env, 'SMTP_PORT'), 'SMTP_PORT', 465),
      user: optionalText(env, 'SMTP_USER'),
      pass: optionalText(env, 'SMTP_PASS'),
    },
  };
  const mailProvider = resolveMailProvider(
    optionalText(env, 'MAIL_PROVIDER'),
    mail,
    isProduction,
  );

  const githubRepo = optionalText(env, 'GITHUB_REPO') || DEFAULT_GITHUB_REPO;
  if (!GITHUB_REPO.test(githubRepo)) {
    throw new ConfigError('GITHUB_REPO must be an owner/repository slug.');
  }

  return freezeConfig({
    env: envName,
    isProduction,
    port,
    baseUrl,
    sessionSecret,
    clerk: {
      secretKey,
      publishableKey,
      issuer,
      frontendApi: resolvedFrontendApi,
    },
    databaseUrl: validatedDatabaseUrl,
    discord: {
      token: optionalText(env, 'DISCORD_BOT_TOKEN'),
      applicationId,
      guildId,
      fallbackChannelId,
      invitePermissions: invitePermissions(optionalText(env, 'DISCORD_INVITE_PERMISSIONS')),
    },
    mail: {
      provider: mailProvider,
      from: mail.from,
      fromName: mail.fromName,
      gmail: mail.gmail,
      brevoApiKey: mail.brevoApiKey,
      sendgridApiKey: mail.sendgridApiKey,
      smtp: mail.smtp,
    },
    github: {
      repo: githubRepo,
      token: optionalText(env, 'GITHUB_TOKEN'),
    },
    appVersion: optionalText(env, 'APP_VERSION') || DEFAULT_APP_VERSION,
  });
}

/** Builds a frozen configuration for tests without requiring production env. */
export function makeTestConfig(overrides: DeepPartial<Config> = {}): Config {
  const base = loadConfig({
    NODE_ENV: 'test',
    PUBLIC_BASE_URL: 'http://localhost:3000',
    SESSION_SECRET: 'test-session-secret-that-is-long-enough',
  });

  return freezeConfig({
    ...base,
    ...overrides,
    clerk: {
      ...base.clerk,
      ...overrides.clerk,
    },
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
