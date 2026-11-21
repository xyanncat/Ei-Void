import { describe, expect, it } from 'vitest';
import { ConfigError, loadConfig, makeTestConfig } from '../src/config.js';

const SECRET = 'a'.repeat(64);
const PK = `pk_test_${Buffer.from('clerk.ei-void.test$').toString('base64')}`;

function production(env: NodeJS.ProcessEnv = {}) {
  return loadConfig({
    NODE_ENV: 'production',
    SESSION_SECRET: SECRET,
    PUBLIC_BASE_URL: 'https://void.example.com',
    ...env,
  });
}

describe('website config', () => {
  it('accepts a complete production environment', () => {
    const config = production({ BOT_API_TOKEN: 'b'.repeat(48) });
    expect(config.isProduction).toBe(true);
    expect(config.baseUrl).toBe('https://void.example.com');
    expect(config.botApiToken).toBe('b'.repeat(48));
    expect(config.releasesUrl).toBe('https://github.com/xyanncat/Ei-Void/releases');
    expect(config.signInUrl).toBe('https://void.example.com/sign-in');
  });

  it('keeps the shared Clerk, Discord, mail and GitHub sections', () => {
    const config = production({
      CLERK_SECRET_KEY: 'sk_test_example',
      CLERK_PUBLISHABLE_KEY: PK,
      DISCORD_BOT_TOKEN: 'discord-token',
      DISCORD_APPLICATION_ID: '1573240846223151144',
      DISCORD_GUILD_ID: '848841415940898827',
      GITHUB_REPO: 'xyanncat/Ei-Void',
      GITHUB_TOKEN: 'gh-token',
      MAIL_PROVIDER: 'brevo',
      MAIL_FROM: 'hello@example.com',
      BREVO_API_KEY: 'brevo-key',
    });
    expect(config.clerk.secretKey).toBe('sk_test_example');
    expect(config.clerk.frontendApi).toBe('clerk.ei-void.test');
    expect(config.discord.token).toBe('discord-token');
    expect(config.mail.provider).toBe('brevo');
    expect(config.github.repo).toBe('xyanncat/Ei-Void');
    expect(config.github.token).toBe('gh-token');
  });

  it('requires a long session secret in production', () => {
    expect(() => production({ SESSION_SECRET: 'short' })).toThrow(/SESSION_SECRET/);
    expect(() =>
      loadConfig({ NODE_ENV: 'production', PUBLIC_BASE_URL: 'https://void.example.com' }),
    ).toThrow(/SESSION_SECRET/);
  });

  it('requires https and a long bot token in production', () => {
    expect(() => production({ PUBLIC_BASE_URL: 'http://void.example.com' })).toThrow(
      /PUBLIC_BASE_URL/,
    );
    expect(() => production({ BOT_API_TOKEN: 'short' })).toThrow(/BOT_API_TOKEN/);
  });

  it('accepts the website-only fields', () => {
    const config = loadConfig({
      NODE_ENV: 'test',
      BOT_API_TOKEN: 'test-bot-api-token-that-is-long-enough',
      CONTACT_EMAIL: 'Cat@Example.com',
      DOWNLOAD_URL: 'https://example.com/ei-void.apk',
      RELEASES_URL: 'https://example.com/releases',
      FRONTEND_SIGN_IN_URL: 'https://accounts.example.com/sign-in',
    });
    expect(config.botApiToken).toBe('test-bot-api-token-that-is-long-enough');
    expect(config.contactEmail).toBe('cat@example.com');
    expect(config.downloadUrl).toBe('https://example.com/ei-void.apk');
    expect(config.releasesUrl).toBe('https://example.com/releases');
    expect(config.signInUrl).toBe('https://accounts.example.com/sign-in');
  });

  it('rejects malformed website-only fields', () => {
    expect(() => loadConfig({ CONTACT_EMAIL: 'not-an-email' })).toThrow(ConfigError);
    expect(() => loadConfig({ DOWNLOAD_URL: 'http://example.com/app.apk' })).toThrow(
      /DOWNLOAD_URL/,
    );
    expect(() => loadConfig({ RELEASES_URL: 'ftp://example.com/releases' })).toThrow(
      /RELEASES_URL/,
    );
    expect(() => loadConfig({ FRONTEND_SIGN_IN_URL: 'javascript:alert(1)' })).toThrow(
      /FRONTEND_SIGN_IN_URL/,
    );
  });

  it('defaults to the local base URL, public repo and home guild', () => {
    const config = loadConfig({});
    expect(config.env).toBe('development');
    expect(config.baseUrl).toBe('http://localhost:3000');
    expect(config.databaseUrl).toBeNull();
    expect(config.downloadUrl).toBeNull();
    expect(config.contactEmail).toBeNull();
    expect(config.githubRepo).toBe('xyanncat/Ei-Void');
    expect(config.discordApplicationId).toBe('1573240846223151144');
    expect(config.discordGuildId).toBe('848841415940898827');
  });

  it('uses PORT in the local base URL', () => {
    const config = loadConfig({ PORT: '4100' });
    expect(config.port).toBe(4100);
    expect(config.baseUrl).toBe('http://localhost:4100');
    expect(config.signInUrl).toBe('http://localhost:4100/sign-in');
  });

  it('builds a test config with overrides', () => {
    const config = makeTestConfig({
      botApiToken: 'test-token',
      mail: { provider: 'smtp', from: 'hello@example.com' },
    });
    expect(config.env).toBe('test');
    expect(config.botApiToken).toBe('test-token');
    expect(config.mail.provider).toBe('smtp');
    expect(config.mail.from).toBe('hello@example.com');
  });
});
