import {
  ConfigError as SharedConfigError,
  loadConfig,
  type Config as SharedConfig,
  type MailConfig,
} from '@eivoid/config';

/** Configuration the Discord worker needs. Nothing here is ever logged. */
export interface BotConfig {
  /** Bot token. Only ever read by this worker; never sent to the web service. */
  readonly token: string;
  /** Application id from the token, or an explicit DISCORD_APPLICATION_ID. */
  readonly clientId: string;
  /** The single guild this bot serves. */
  readonly guildId: string;
  /** Channel used when a user's DMs are closed. Empty when not configured. */
  readonly fallbackChannelId: string;
  readonly databaseUrl: string;
  readonly mail: MailConfig;
  readonly healthPort: number;
  /** Public website base for /link. Empty when the bot has no public URL. */
  readonly webBaseUrl: string;
}

export class ConfigError extends Error {
  override name = 'ConfigError';

  constructor(message: string) {
    super(message);
  }
}

const SNOWFLAKE = /^\d{16,22}$/;
const DEFAULT_HEALTH_PORT = 8081;
const BOT_SESSION_PLACEHOLDER = 'ei-void-bot-worker-session-secret-is-unused';
const BOT_PUBLIC_PLACEHOLDER = 'https://bot.invalid';

function rawWebBaseUrl(env: NodeJS.ProcessEnv): string {
  return env.PUBLIC_BASE_URL?.trim() || env.RENDER_EXTERNAL_URL?.trim() || '';
}

function normaliseHttpUrl(value: string, name: string): string {
  // Accept Render's bare-host shape as well as a full URL.
  const withScheme = /^[a-z][a-z0-9+.-]*:\/\//i.test(value) ? value : `https://${value}`;
  let url: URL;
  try {
    url = new URL(withScheme);
  } catch {
    throw new ConfigError(`${name} must be a valid URL.`);
  }
  if (url.protocol !== 'https:' && url.protocol !== 'http:') {
    throw new ConfigError(`${name} must be an http(s) URL.`);
  }
  if (url.username || url.password) {
    throw new ConfigError(`${name} must not contain credentials.`);
  }
  return url.toString().replace(/\/+$/, '');
}

/**
 * The shared loader also validates web-only settings: a production session
 * secret and an https PUBLIC_BASE_URL. The bot uses neither, so supply inert
 * values only when the deployment leaves them out. Everything the bot actually
 * reads (token, guild, database, mail) is still validated normally.
 */
function sharedEnvironment(env: NodeJS.ProcessEnv): NodeJS.ProcessEnv {
  const copy: NodeJS.ProcessEnv = { ...env };
  const webBase = rawWebBaseUrl(env);
  copy.PUBLIC_BASE_URL = webBase
    ? normaliseHttpUrl(webBase, 'PUBLIC_BASE_URL')
    : BOT_PUBLIC_PLACEHOLDER;
  if (!copy.SESSION_SECRET?.trim()) {
    copy.SESSION_SECRET = BOT_SESSION_PLACEHOLDER;
  }
  return copy;
}

function healthPort(env: NodeJS.ProcessEnv): number {
  const raw = env.BOT_HEALTH_PORT?.trim();
  if (!raw) return DEFAULT_HEALTH_PORT;
  if (!/^\d{1,5}$/.test(raw)) {
    throw new ConfigError('BOT_HEALTH_PORT must be an integer between 1 and 65535.');
  }
  const port = Number(raw);
  if (!Number.isInteger(port) || port < 1 || port > 65535) {
    throw new ConfigError('BOT_HEALTH_PORT must be an integer between 1 and 65535.');
  }
  return port;
}

/**
 * A bot token's first segment is the application/bot user id. Deriving it
 * means the worker can register commands even when DISCORD_APPLICATION_ID is
 * not set, and a copied id can never win over the token that authenticates.
 */
export function tokenApplicationId(token: string): string | null {
  const [first] = token.split('.');
  if (!first) return null;
  try {
    const decoded = Buffer.from(first, 'base64url').toString('utf8');
    return SNOWFLAKE.test(decoded) ? decoded : null;
  } catch {
    return null;
  }
}

/**
 * Parses every value the worker needs. Errors name the variable but never its
 * value, so a startup failure is safe to log.
 */
export function readConfig(env: NodeJS.ProcessEnv): BotConfig {
  const token = env.DISCORD_BOT_TOKEN?.trim() ?? '';
  if (!token) {
    throw new ConfigError('DISCORD_BOT_TOKEN is required.');
  }

  // Shared validation covers nested mail/Clerk/GitHub settings and the
  // database URL shape; the bot-only required fields are checked below.
  let shared: SharedConfig;
  try {
    shared = loadConfig(sharedEnvironment(env));
  } catch (error) {
    // One error type for callers, so the startup guard is predictable.
    if (error instanceof SharedConfigError) {
      throw new ConfigError(error.message);
    }
    throw error;
  }

  const guildId = shared.discord.guildId;
  if (!guildId) {
    throw new ConfigError('DISCORD_GUILD_ID is required.');
  }
  if (!SNOWFLAKE.test(guildId)) {
    throw new ConfigError('DISCORD_GUILD_ID must be a Discord guild snowflake.');
  }

  const databaseUrl = shared.databaseUrl;
  if (!databaseUrl) {
    throw new ConfigError('DATABASE_URL is required.');
  }

  const clientId = tokenApplicationId(token) || shared.discord.applicationId;
  if (!SNOWFLAKE.test(clientId)) {
    throw new ConfigError(
      'DISCORD_APPLICATION_ID is required when the bot token has no decodable application id.',
    );
  }

  const webBase = rawWebBaseUrl(env);

  return Object.freeze({
    token,
    clientId,
    guildId,
    fallbackChannelId: shared.discord.fallbackChannelId,
    databaseUrl,
    mail: shared.mail,
    healthPort: healthPort(env),
    webBaseUrl: webBase ? normaliseHttpUrl(webBase, 'PUBLIC_BASE_URL') : '',
  });
}
