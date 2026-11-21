import { describe, expect, it, vi } from 'vitest';
import {
  checkHealth,
  createHealthServer,
  type HealthClient,
  type HealthDependencies,
  type HealthPayload,
} from '../src/health.js';
import { GUILD_ID } from './fixtures.js';

interface HealthOptions {
  ready?: boolean;
  guildPresent?: boolean;
  databaseOk?: boolean;
  schedulerRunning?: boolean;
  provider?: string | null;
  guilds?: number;
}

function makeDependencies(options: HealthOptions = {}) {
  const {
    ready = true,
    guildPresent = true,
    databaseOk = true,
    schedulerRunning = true,
    provider = 'gmail',
    guilds = 1,
  } = options;

  const query = vi.fn(async () => {
    if (!databaseOk) throw new Error('database down');
    return { rows: [{ '?column?': 1 }] };
  });

  const client: HealthClient = {
    isReady: () => ready,
    guilds: {
      cache: {
        size: guilds,
        get: (id: string) => (id === GUILD_ID && guildPresent ? {} : undefined),
      },
    },
  };

  const dependencies: HealthDependencies = {
    client,
    pool: { query },
    mailer: { provider },
    scheduler: { isRunning: () => schedulerRunning },
    guildId: GUILD_ID,
  };

  return { dependencies, query };
}

describe('checkHealth', () => {
  it('reports every subsystem when healthy', async () => {
    const { dependencies, query } = makeDependencies();

    const payload = await checkHealth(dependencies);

    expect(query).toHaveBeenCalledWith('SELECT 1');
    expect(payload).toEqual({
      ok: true,
      bot: true,
      guilds: 1,
      inLockedGuild: true,
      database: true,
      mail: true,
      scheduler: true,
      guildLock: GUILD_ID,
    } satisfies HealthPayload);
  });

  it('reports a database failure without throwing', async () => {
    const { dependencies } = makeDependencies({ databaseOk: false });

    const payload = await checkHealth(dependencies);

    expect(payload.database).toBe(false);
    expect(payload.ok).toBe(false);
  });

  it('reports when the bot is not in the locked guild', async () => {
    const { dependencies } = makeDependencies({ guildPresent: false, guilds: 3 });

    const payload = await checkHealth(dependencies);

    expect(payload.inLockedGuild).toBe(false);
    expect(payload.guilds).toBe(3);
    expect(payload.ok).toBe(false);
  });

  it('reports a bot that is not ready and a stopped scheduler', async () => {
    const { dependencies } = makeDependencies({ ready: false, schedulerRunning: false });

    const payload = await checkHealth(dependencies);

    expect(payload.bot).toBe(false);
    expect(payload.scheduler).toBe(false);
    expect(payload.ok).toBe(false);
  });

  it('reports mail as optional while the worker is still healthy', async () => {
    const { dependencies } = makeDependencies({ provider: null });

    const payload = await checkHealth(dependencies);

    expect(payload.mail).toBe(false);
    expect(payload.ok).toBe(true);
  });
});

describe('createHealthServer', () => {
  it('serves GET /health as JSON', async () => {
    const { dependencies } = makeDependencies();
    const server = createHealthServer(dependencies);

    await new Promise<void>((resolve, reject) => {
      server.once('error', reject);
      server.listen(0, '127.0.0.1', () => resolve());
    });

    try {
      const address = server.address();
      if (!address || typeof address === 'string') {
        throw new Error('expected a TCP address');
      }
      const response = await fetch(`http://127.0.0.1:${address.port}/health`);
      expect(response.status).toBe(200);
      const body = (await response.json()) as HealthPayload;
      expect(body.ok).toBe(true);
      expect(body.guildLock).toBe(GUILD_ID);
      expect(body.database).toBe(true);
    } finally {
      await new Promise<void>((resolve) => server.close(() => resolve()));
    }
  });
});
