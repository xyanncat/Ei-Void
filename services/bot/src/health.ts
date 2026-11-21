import { createServer, type Server, type ServerResponse } from 'node:http';

export interface HealthClient {
  isReady(): boolean;
  guilds: {
    cache: {
      readonly size: number;
      get(id: string): unknown;
    };
  };
}

export interface HealthPool {
  query(text: string): Promise<unknown>;
}

export interface HealthMailer {
  readonly provider: string | null;
}

export interface HealthScheduler {
  isRunning(): boolean;
}

export interface HealthDependencies {
  readonly client: HealthClient;
  readonly pool: HealthPool;
  readonly mailer: HealthMailer;
  readonly scheduler: HealthScheduler;
  readonly guildId: string;
}

export interface HealthPayload {
  ok: boolean;
  bot: boolean;
  guilds: number;
  inLockedGuild: boolean;
  database: boolean;
  mail: boolean;
  scheduler: boolean;
  guildLock: string;
}

function sendJson(response: ServerResponse, status: number, body: unknown): void {
  const payload = JSON.stringify(body);
  response.writeHead(status, {
    'content-type': 'application/json; charset=utf-8',
    'cache-control': 'no-store',
    'content-length': Buffer.byteLength(payload),
  });
  response.end(payload);
}

/**
 * Checks the worker's real dependencies. `SELECT 1` is the database probe; the
 * payload reports each subsystem separately so a failure is diagnosable
 * without exposing any secret.
 */
export async function checkHealth(dependencies: HealthDependencies): Promise<HealthPayload> {
  let database = false;
  try {
    await dependencies.pool.query('SELECT 1');
    database = true;
  } catch {
    database = false;
  }

  let bot = false;
  try {
    bot = dependencies.client.isReady();
  } catch {
    bot = false;
  }

  const inLockedGuild =
    dependencies.client.guilds.cache.get(dependencies.guildId) !== undefined;
  const scheduler = dependencies.scheduler.isRunning();
  const mail = dependencies.mailer.provider !== null;

  return {
    ok: database && bot && inLockedGuild && scheduler,
    bot,
    guilds: dependencies.client.guilds.cache.size,
    inLockedGuild,
    database,
    mail,
    scheduler,
    guildLock: dependencies.guildId,
  };
}

export interface HealthServerOptions extends HealthDependencies {
  readonly port: number;
  readonly log?: (code: string) => void;
}

/** Creates the HTTP server without listening; exported for tests. */
export function createHealthServer(dependencies: HealthDependencies): Server {
  return createServer((request, response) => {
    const url = request.url ?? '/';
    if (request.method === 'GET' && (url === '/health' || url === '/healthz')) {
      void checkHealth(dependencies)
        .then((payload) => {
          sendJson(response, payload.ok ? 200 : 503, payload);
        })
        .catch(() => {
          sendJson(response, 503, { ok: false });
        });
      return;
    }
    sendJson(response, 404, { error: 'not_found' });
  });
}

/** Starts the health listener. The caller closes it during shutdown. */
export function startHealthServer(options: HealthServerOptions): Server {
  const log = options.log ?? ((code: string) => console.warn(`[health] ${code}`));
  const server = createHealthServer(options);
  server.on('error', () => {
    log('health_server_error');
  });
  server.listen(options.port, () => {
    log('health_listening');
  });
  return server;
}
