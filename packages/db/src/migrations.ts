import type { Pool } from 'pg';
import type { Migration, MigrationResult } from './types.js';

const MIGRATION_001_SQL = `
CREATE TABLE accounts (
  clerk_user_id TEXT PRIMARY KEY,
  email TEXT NOT NULL DEFAULT '',
  display_name TEXT NOT NULL DEFAULT '',
  timezone TEXT NOT NULL DEFAULT 'UTC',
  notify_hour SMALLINT NOT NULL DEFAULT 9,
  notify_minute SMALLINT NOT NULL DEFAULT 0,
  email_enabled BOOLEAN NOT NULL DEFAULT TRUE,
  plan TEXT NOT NULL DEFAULT 'Free',
  discord_user_id TEXT UNIQUE,
  discord_username TEXT NOT NULL DEFAULT '',
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE reminders (
  id BIGSERIAL PRIMARY KEY,
  clerk_user_id TEXT NOT NULL REFERENCES accounts(clerk_user_id) ON DELETE CASCADE,
  local_id BIGINT NOT NULL,
  title TEXT NOT NULL,
  merchant TEXT NOT NULL DEFAULT '',
  bank TEXT NOT NULL DEFAULT '',
  amount_minor BIGINT NOT NULL DEFAULT 0,
  currency TEXT NOT NULL DEFAULT 'INR',
  due_at BIGINT NOT NULL,
  notify_at BIGINT NOT NULL,
  repeat_rule TEXT NOT NULL DEFAULT 'NONE',
  category TEXT NOT NULL DEFAULT 'OTHER',
  notes TEXT NOT NULL DEFAULT '',
  lead_days SMALLINT NOT NULL DEFAULT 4,
  is_paid BOOLEAN NOT NULL DEFAULT FALSE,
  is_active BOOLEAN NOT NULL DEFAULT TRUE,
  paid_at BIGINT,
  discord_notified_due_at BIGINT NOT NULL DEFAULT 0,
  email_notified_due_at BIGINT NOT NULL DEFAULT 0,
  discord_claimed_at TIMESTAMPTZ,
  email_claimed_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT reminders_owner_local_id_unique UNIQUE (clerk_user_id, local_id)
);

CREATE INDEX reminders_due_idx
  ON reminders (notify_at)
  WHERE is_active = TRUE AND is_paid = FALSE;

CREATE INDEX reminders_owner_due_idx
  ON reminders (clerk_user_id, due_at);

CREATE TABLE link_codes (
  code TEXT PRIMARY KEY,
  discord_user_id TEXT NOT NULL,
  discord_username TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  expires_at TIMESTAMPTZ NOT NULL,
  used_at TIMESTAMPTZ
);

CREATE INDEX link_codes_expiry_idx ON link_codes (expires_at);

CREATE TABLE device_keys (
  id BIGSERIAL PRIMARY KEY,
  clerk_user_id TEXT NOT NULL REFERENCES accounts(clerk_user_id) ON DELETE CASCADE,
  key_hash TEXT NOT NULL UNIQUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  last_seen_at TIMESTAMPTZ,
  revoked_at TIMESTAMPTZ
);

CREATE INDEX device_keys_owner_idx ON device_keys (clerk_user_id);

CREATE TABLE usage_snapshots (
  id BIGSERIAL PRIMARY KEY,
  clerk_user_id TEXT NOT NULL REFERENCES accounts(clerk_user_id) ON DELETE CASCADE,
  plan TEXT NOT NULL,
  tokens_used BIGINT NOT NULL CHECK (tokens_used >= 0),
  tokens_limit BIGINT NOT NULL CHECK (tokens_limit >= 0),
  period_end TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX usage_snapshots_owner_created_idx
  ON usage_snapshots (clerk_user_id, created_at DESC, id DESC);

CREATE TABLE waitlist (
  id BIGSERIAL PRIMARY KEY,
  email TEXT NOT NULL UNIQUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
`;

export const migrations: readonly Migration[] = Object.freeze([
  Object.freeze({
    version: 1,
    name: '001_unified_schema',
    sql: MIGRATION_001_SQL,
  }),
]);

export const LATEST_SCHEMA_VERSION = migrations.reduce(
  (latest, migration) => Math.max(latest, migration.version),
  0,
);

/**
 * Applies every pending migration inside one transaction. A transaction-level
 * advisory lock serialises concurrent deploys, and schema_migrations records
 * which numbered migrations have run.
 */
export async function migrate(pool: Pool): Promise<MigrationResult> {
  const client = await pool.connect();
  try {
    await client.query('BEGIN');
    await client.query(
      "SELECT pg_advisory_xact_lock(hashtext('ei_void_schema_migrations')::bigint)",
    );
    await client.query(`
      CREATE TABLE IF NOT EXISTS schema_migrations (
        version INTEGER PRIMARY KEY,
        name TEXT NOT NULL,
        applied_at TIMESTAMPTZ NOT NULL DEFAULT now()
      )
    `);

    const existing = await client.query<{ version: number }>(
      'SELECT version FROM schema_migrations ORDER BY version',
    );
    const appliedVersions = new Set(existing.rows.map((row) => Number(row.version)));
    const applied: number[] = [];

    for (const migration of migrations) {
      if (appliedVersions.has(migration.version)) continue;
      await client.query(migration.sql);
      await client.query(
        'INSERT INTO schema_migrations (version, name) VALUES ($1, $2)',
        [migration.version, migration.name],
      );
      appliedVersions.add(migration.version);
      applied.push(migration.version);
    }

    await client.query('COMMIT');

    const currentVersion =
      applied.length > 0
        ? applied[applied.length - 1] ?? LATEST_SCHEMA_VERSION
        : Math.max(0, ...appliedVersions);

    return { applied, currentVersion };
  } catch (error) {
    await client.query('ROLLBACK').catch(() => undefined);
    throw error;
  } finally {
    client.release();
  }
}
