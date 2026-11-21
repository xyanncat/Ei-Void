import { describe, expect, it, vi } from 'vitest';
import { ConfigError, readConfig, tokenApplicationId } from '../src/config.js';
import {
  baseEnv,
  CLIENT_ID,
  FALLBACK_CHANNEL_ID,
  OTHER_USER_ID,
  tokenFor,
} from './fixtures.js';

describe('readConfig', () => {
  it('derives the application id from the token and exposes worker settings', () => {
    const config = readConfig(baseEnv);

    expect(config.clientId).toBe(CLIENT_ID);
    expect(config.token).toBe(baseEnv.DISCORD_BOT_TOKEN);
    expect(config.guildId).toBe(baseEnv.DISCORD_GUILD_ID);
    expect(config.databaseUrl).toBe(baseEnv.DATABASE_URL);
    expect(config.fallbackChannelId).toBe('');
    expect(config.healthPort).toBe(8081);
    expect(config.webBaseUrl).toBe('');
    expect(config.mail.provider).toBeNull();
  });

  it('requires a bot token', () => {
    expect(() => readConfig({ ...baseEnv, DISCORD_BOT_TOKEN: '' })).toThrow(ConfigError);
  });

  it('requires the home guild', () => {
    expect(() => readConfig({ ...baseEnv, DISCORD_GUILD_ID: '' })).toThrow(ConfigError);
    expect(() => readConfig({ ...baseEnv, DISCORD_GUILD_ID: 'not-a-snowflake' })).toThrow(
      ConfigError,
    );
  });

  it('requires a database URL', () => {
    const env: NodeJS.ProcessEnv = { ...baseEnv };
    delete env.DATABASE_URL;
    expect(() => readConfig(env)).toThrow(ConfigError);
  });

  it('accepts an explicit application id when the token is not decodable', () => {
    const config = readConfig({
      ...baseEnv,
      DISCORD_BOT_TOKEN: 'opaque-token',
      DISCORD_APPLICATION_ID: CLIENT_ID,
    });
    expect(config.clientId).toBe(CLIENT_ID);
  });

  it('lets the token own the application id over a mismatched paste', () => {
    const config = readConfig({ ...baseEnv, DISCORD_APPLICATION_ID: OTHER_USER_ID });
    expect(config.clientId).toBe(CLIENT_ID);
  });

  it('exposes the optional fallback channel', () => {
    const config = readConfig({
      ...baseEnv,
      DISCORD_FALLBACK_CHANNEL_ID: FALLBACK_CHANNEL_ID,
    });
    expect(config.fallbackChannelId).toBe(FALLBACK_CHANNEL_ID);
  });

  it('rejects a token with no application id and no explicit id', () => {
    expect(() => readConfig({ ...baseEnv, DISCORD_BOT_TOKEN: 'opaque-token' })).toThrow(
      ConfigError,
    );
  });

  it('reads and validates BOT_HEALTH_PORT', () => {
    expect(readConfig({ ...baseEnv, BOT_HEALTH_PORT: '9090' }).healthPort).toBe(9090);
    expect(() => readConfig({ ...baseEnv, BOT_HEALTH_PORT: '0' })).toThrow(ConfigError);
    expect(() => readConfig({ ...baseEnv, BOT_HEALTH_PORT: 'not-a-port' })).toThrow(
      ConfigError,
    );
    expect(() => readConfig({ ...baseEnv, BOT_HEALTH_PORT: '70000' })).toThrow(ConfigError);
  });

  it('builds webBaseUrl from PUBLIC_BASE_URL and strips a trailing slash', () => {
    expect(
      readConfig({ ...baseEnv, PUBLIC_BASE_URL: 'https://void.example.com/' }).webBaseUrl,
    ).toBe('https://void.example.com');
    expect(
      readConfig({ ...baseEnv, PUBLIC_BASE_URL: 'http://localhost:3000' }).webBaseUrl,
    ).toBe('http://localhost:3000');
  });

  it('falls back to RENDER_EXTERNAL_URL and accepts a bare host', () => {
    expect(
      readConfig({ ...baseEnv, RENDER_EXTERNAL_URL: 'https://void.onrender.com' }).webBaseUrl,
    ).toBe('https://void.onrender.com');
    expect(readConfig({ ...baseEnv, PUBLIC_BASE_URL: 'void.example.com' }).webBaseUrl).toBe(
      'https://void.example.com',
    );
  });

  it('rejects a web base with credentials or an unsupported protocol', () => {
    expect(() =>
      readConfig({ ...baseEnv, PUBLIC_BASE_URL: 'ftp://void.example.com' }),
    ).toThrow(ConfigError);
    expect(() =>
      readConfig({ ...baseEnv, PUBLIC_BASE_URL: 'https://user:pass@void.example.com' }),
    ).toThrow(ConfigError);
  });

  it('starts in production without web-only variables', () => {
    const config = readConfig({ ...baseEnv, NODE_ENV: 'production' });
    expect(config.webBaseUrl).toBe('');
    expect(config.healthPort).toBe(8081);
  });

  it('never echoes the token in an error or a log', () => {
    const token = tokenFor(CLIENT_ID);
    const spies = [
      vi.spyOn(console, 'log').mockImplementation(() => undefined),
      vi.spyOn(console, 'warn').mockImplementation(() => undefined),
      vi.spyOn(console, 'error').mockImplementation(() => undefined),
    ];

    try {
      readConfig({ ...baseEnv, DISCORD_BOT_TOKEN: token });
      expect(() => readConfig({ ...baseEnv, DISCORD_GUILD_ID: 'bad' })).toThrow(ConfigError);

      for (const spy of spies) {
        for (const call of spy.mock.calls) {
          expect(JSON.stringify(call)).not.toContain(token);
        }
      }
    } finally {
      for (const spy of spies) spy.mockRestore();
    }
  });
});

describe('tokenApplicationId', () => {
  it('decodes a snowflake first segment', () => {
    expect(tokenApplicationId(tokenFor(CLIENT_ID))).toBe(CLIENT_ID);
  });

  it('returns null for an opaque token', () => {
    expect(tokenApplicationId('not-a-token')).toBeNull();
  });
});
