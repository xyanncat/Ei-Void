import { Pool, type PoolConfig } from 'pg';

export interface CreatePoolOptions extends Omit<PoolConfig, 'connectionString'> {}

const LOCAL_HOSTS = new Set(['', 'localhost', '127.0.0.1', '::1', '[::1]']);

function parseDatabaseUrl(databaseUrl: string): URL {
  let parsed: URL;
  try {
    parsed = new URL(databaseUrl);
  } catch {
    throw new Error('DATABASE_URL must be a valid postgres:// or postgresql:// URL.');
  }
  if (parsed.protocol !== 'postgres:' && parsed.protocol !== 'postgresql:') {
    throw new Error('DATABASE_URL must be a valid postgres:// or postgresql:// URL.');
  }
  return parsed;
}

function derivedSsl(url: URL): PoolConfig['ssl'] {
  if (LOCAL_HOSTS.has(url.hostname) || url.searchParams.get('sslmode') === 'disable') {
    return undefined;
  }
  return { rejectUnauthorized: true };
}

/**
 * Creates a pg pool. Remote databases get TLS with certificate verification;
 * localhost and unix-socket connections stay unencrypted unless the caller
 * explicitly supplies `ssl`.
 */
export function createPool(
  databaseUrl: string,
  options: CreatePoolOptions = {},
): Pool {
  const url = parseDatabaseUrl(databaseUrl);
  const ssl = options.ssl !== undefined ? options.ssl : derivedSsl(url);

  return new Pool({
    connectionString: databaseUrl,
    max: 5,
    idleTimeoutMillis: 30_000,
    connectionTimeoutMillis: 10_000,
    application_name: 'ei-void',
    ...options,
    ssl,
  });
}
