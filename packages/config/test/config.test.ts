import { describe, expect, it } from 'vitest';
import {
  ConfigError,
  clerkFrontendApiFromPublishableKey,
  clerkIssuerFromPublishableKey,
  loadConfig,
  makeTestConfig,
} from '../src/index.js';

const PRODUCTION_BASE = 'https://ei-void.example.com';
const PRODUCTION_SECRET = 'a-production-session-secret-with-32-chars';

function publishableKey(host: string, mode: 'test' | 'live' = 'test'): string {
  return `pk_${mode}_${Buffer.from(`${host}$`, 'utf8').toString('base64')}`;
}

function productionEnv(overrides: NodeJS.ProcessEnv = {}): NodeJS.ProcessEnv {
  return {
    NODE_ENV: 'production',
    PUBLIC_BASE_URL: PRODUCTION_BASE,
    SESSION_SECRET: PRODUCTION_SECRET,
    ...overrides,
  };
}

describe('clerkIssuerFromPublishableKey', () => {
  it('decodes the frontend API host from Clerk keys', () => {
    const key = publishableKey('example.clerk.accounts.dev');
    expect(clerkFrontendApiFromPublishableKey(key)).toBe('example.clerk.accounts.dev');
    expect(clerkIssuerFromPublishableKey(key)).toBe('https://example.clerk.accounts.dev');
  });

  it('returns an empty string for malformed keys', () => {
    expect(clerkIssuerFromPublishableKey('')).toBe('');
    expect(clerkIssuerFromPublishableKey('not-a-clerk-key')).toBe('');
    expect(clerkFrontendApiFromPublishableKey('pk_test_')).toBe('');
  });
});

describe('loadConfig', () => {
  it('loads development defaults', () => {
    const config = loadConfig({ NODE_ENV: 'development' });
    expect(config.env).toBe('development');
    expect(config.isProduction).toBe(false);
    expect(config.port).toBe(3000);
    expect(config.baseUrl).toBe('http://localhost:3000');
    expect(config.sessionSecret.length).toBeGreaterThanOrEqual(32);
    expect(config.databaseUrl).toBeNull();
    expect(config.discord.invitePermissions).toBe(3072);
    expect(config.mail.provider).toBeNull();
    expect(config.github.repo).toBe('xyanncat/Ei-Void');
    expect(config.appVersion).toBe('0.1.0');
  });

  it('requires a strong session secret in production without echoing it', () => {
    expect(() =>
      loadConfig(productionEnv({ SESSION_SECRET: undefined })),
    ).toThrow(ConfigError);
    expect(() =>
      loadConfig(productionEnv({ SESSION_SECRET: 'short-secret' })),
    ).toThrow(/SESSION_SECRET/);

    try {
      loadConfig(productionEnv({ SESSION_SECRET: 'super-secret-but-too-short' }));
      throw new Error('expected loadConfig to throw');
    } catch (error) {
      expect(error).toBeInstanceOf(ConfigError);
      expect(String(error)).not.toContain('super-secret-but-too-short');
    }
  });

  it('requires https for the production base URL', () => {
    expect(() =>
      loadConfig(productionEnv({ PUBLIC_BASE_URL: 'http://ei-void.example.com' })),
    ).toThrow(/https/);
    expect(() =>
      loadConfig(productionEnv({ PUBLIC_BASE_URL: 'not a url' })),
    ).toThrow(/PUBLIC_BASE_URL/);
  });

  it('validates the port and database URL without echoing credentials', () => {
    expect(() => loadConfig({ PORT: 'not-a-port' })).toThrow(/PORT/);
    expect(() => loadConfig({ PORT: '70000' })).toThrow(/PORT/);

    const config = loadConfig({
      DATABASE_URL: 'postgresql://user:pass@db.example.com:5432/ei_void',
    });
    expect(config.databaseUrl).toBe('postgresql://user:pass@db.example.com:5432/ei_void');

    try {
      loadConfig({ DATABASE_URL: 'mysql://user:topsecret@db.example.com/ei_void' });
      throw new Error('expected loadConfig to throw');
    } catch (error) {
      expect(error).toBeInstanceOf(ConfigError);
      expect(String(error)).not.toContain('topsecret');
    }
  });

  it('derives Clerk issuer and frontend API from the publishable key', () => {
    const key = publishableKey('example.clerk.accounts.dev');
    const config = loadConfig({
      CLERK_PUBLISHABLE_KEY: key,
      CLERK_SECRET_KEY: 'sk_test_example',
    });

    expect(config.clerk.publishableKey).toBe(key);
    expect(config.clerk.frontendApi).toBe('example.clerk.accounts.dev');
    expect(config.clerk.issuer).toBe('https://example.clerk.accounts.dev');
  });

  it('lets an explicit Clerk issuer and frontend API win', () => {
    const config = loadConfig({
      CLERK_PUBLISHABLE_KEY: publishableKey('ignored.clerk.accounts.dev'),
      CLERK_ISSUER: 'https://clerk.example.com/',
      CLERK_FRONTEND_API: 'https://frontend.example.com/',
    });

    expect(config.clerk.issuer).toBe('https://clerk.example.com');
    expect(config.clerk.frontendApi).toBe('frontend.example.com');
  });

  it('rejects a secret key without an issuer', () => {
    expect(() => loadConfig({ CLERK_SECRET_KEY: 'sk_test_example' })).toThrow(/CLERK_ISSUER/);
    expect(() => loadConfig({ CLERK_SECRET_KEY: 'not-a-secret' })).toThrow(
      /CLERK_SECRET_KEY/,
    );
  });

  it('validates Discord ids and invite permissions', () => {
    expect(() => loadConfig({ DISCORD_GUILD_ID: 'abc' })).toThrow(/DISCORD_GUILD_ID/);
    expect(() => loadConfig({ DISCORD_APPLICATION_ID: '123' })).toThrow(
      /DISCORD_APPLICATION_ID/,
    );
    expect(() =>
      loadConfig({ DISCORD_FALLBACK_CHANNEL_ID: 'nope' }),
    ).toThrow(/DISCORD_FALLBACK_CHANNEL_ID/);
    expect(() => loadConfig({ DISCORD_INVITE_PERMISSIONS: 'admin' })).toThrow(
      /DISCORD_INVITE_PERMISSIONS/,
    );

    const config = loadConfig({
      DISCORD_APPLICATION_ID: '123456789012345678',
      DISCORD_GUILD_ID: '876543210987654321',
      DISCORD_FALLBACK_CHANNEL_ID: '111222333444555666',
      DISCORD_INVITE_PERMISSIONS: '18432',
    });
    expect(config.discord.applicationId).toBe('123456789012345678');
    expect(config.discord.guildId).toBe('876543210987654321');
    expect(config.discord.fallbackChannelId).toBe('111222333444555666');
    expect(config.discord.invitePermissions).toBe(18432);
  });

  it('auto-detects the mail provider', () => {
    const config = loadConfig({
      MAIL_FROM: 'void@example.com',
      GMAIL_CLIENT_ID: 'client',
      GMAIL_CLIENT_SECRET: 'secret',
      GMAIL_REFRESH_TOKEN: 'refresh',
    });
    expect(config.mail.provider).toBe('gmail');

    const brevo = loadConfig({ MAIL_FROM: 'void@example.com', BREVO_API_KEY: 'key' });
    expect(brevo.mail.provider).toBe('brevo');
  });

  it('rejects an incomplete explicit mail provider in production', () => {
    expect(() =>
      loadConfig(productionEnv({ MAIL_PROVIDER: 'gmail', MAIL_FROM: 'void@example.com' })),
    ).toThrow(/GMAIL_CLIENT_ID/);

    try {
      loadConfig(
        productionEnv({
          MAIL_PROVIDER: 'sendgrid',
          MAIL_FROM: 'void@example.com',
          BREVO_API_KEY: 'never-print-this-key',
        }),
      );
      throw new Error('expected loadConfig to throw');
    } catch (error) {
      expect(error).toBeInstanceOf(ConfigError);
      expect(String(error)).not.toContain('never-print-this-key');
    }
  });

  it('validates smtp port, mail sender and GitHub repo', () => {
    expect(() => loadConfig({ SMTP_PORT: '0' })).toThrow(/SMTP_PORT/);
    expect(() => loadConfig({ MAIL_FROM: 'not-an-email' })).toThrow(/MAIL_FROM/);
    expect(() => loadConfig({ GITHUB_REPO: 'not-a-repo' })).toThrow(/GITHUB_REPO/);
  });

  it('freezes the configuration graph', () => {
    const config = loadConfig({ NODE_ENV: 'test' });
    expect(Object.isFrozen(config)).toBe(true);
    expect(Object.isFrozen(config.clerk)).toBe(true);
    expect(Object.isFrozen(config.discord)).toBe(true);
    expect(Object.isFrozen(config.mail)).toBe(true);
    expect(Object.isFrozen(config.mail.gmail)).toBe(true);
    expect(Object.isFrozen(config.mail.smtp)).toBe(true);
    expect(Object.isFrozen(config.github)).toBe(true);
  });
});

describe('makeTestConfig', () => {
  it('builds a test config and merges nested overrides', () => {
    const config = makeTestConfig({
      appVersion: '9.9.9',
      clerk: { frontendApi: 'test.clerk.accounts.dev' },
      discord: { guildId: '123456789012345678' },
      mail: {
        provider: 'brevo',
        brevoApiKey: 'brevo-test-key',
        smtp: { host: 'smtp.test' },
      },
      github: { repo: 'owner/repo' },
    });

    expect(config.env).toBe('test');
    expect(config.isProduction).toBe(false);
    expect(config.appVersion).toBe('9.9.9');
    expect(config.clerk.frontendApi).toBe('test.clerk.accounts.dev');
    expect(config.discord.guildId).toBe('123456789012345678');
    expect(config.mail.provider).toBe('brevo');
    expect(config.mail.brevoApiKey).toBe('brevo-test-key');
    expect(config.mail.smtp.host).toBe('smtp.test');
    expect(config.mail.smtp.port).toBe(465);
    expect(config.github.repo).toBe('owner/repo');
    expect(config.sessionSecret.length).toBeGreaterThanOrEqual(32);
    expect(Object.isFrozen(config.mail.smtp)).toBe(true);
  });
});
