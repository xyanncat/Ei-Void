import { randomBytes } from 'node:crypto';
import type { Pool } from 'pg';
import { generateDeviceKey, hashDeviceKey } from './device-keys.js';
import type {
  Account,
  AddUsageSnapshotInput,
  CreateLinkCodeInput,
  DeliveryChannel,
  DeviceKeyRecord,
  DiscordIdentity,
  DueReminder,
  IssuedDeviceKey,
  LinkCode,
  ReminderInput,
  ReminderRecord,
  ReminderSummary,
  SnoozeReminderOptions,
  UpsertAccountInput,
  UsageSnapshotRecord,
} from './types.js';

const DEFAULT_LINK_CODE_TTL_MS = 10 * 60_000;

type AccountRow = {
  clerk_user_id: string;
  email: string;
  display_name: string;
  timezone: string;
  notify_hour: number;
  notify_minute: number;
  email_enabled: boolean;
  plan: string;
  discord_user_id: string | null;
  discord_username: string;
  created_at: Date | string;
  updated_at: Date | string;
};

type ReminderRow = {
  id: string | number;
  clerk_user_id: string;
  local_id: string | number;
  title: string;
  merchant: string;
  bank: string;
  amount_minor: string | number;
  currency: string;
  due_at: string | number;
  notify_at: string | number;
  repeat_rule: string;
  category: string;
  notes: string;
  lead_days: number;
  is_paid: boolean;
  is_active: boolean;
  paid_at: string | number | null;
  discord_notified_due_at: string | number;
  email_notified_due_at: string | number;
  discord_claimed_at: Date | string | null;
  email_claimed_at: Date | string | null;
  created_at: Date | string;
  updated_at: Date | string;
};

type DueReminderRow = ReminderRow & {
  account_email: string;
  account_email_enabled: boolean;
  account_timezone: string;
  account_discord_user_id: string | null;
};

type LinkCodeRow = {
  code: string;
  discord_user_id: string;
  discord_username: string;
  created_at: Date | string;
  expires_at: Date | string;
  used_at: Date | string | null;
};

type DeviceKeyRow = {
  id: string | number;
  clerk_user_id: string;
  key_hash: string;
  created_at: Date | string;
  last_seen_at: Date | string | null;
  revoked_at: Date | string | null;
};

type UsageSnapshotRow = {
  id: string | number;
  clerk_user_id: string;
  plan: string;
  tokens_used: string | number;
  tokens_limit: string | number;
  period_end: string | null;
  created_at: Date | string;
};

function toNumber(value: unknown): number {
  const number = Number(value);
  return Number.isFinite(number) ? number : 0;
}

function toDate(value: Date | string): Date {
  return value instanceof Date ? value : new Date(value);
}

function toDateOrNull(value: Date | string | null): Date | null {
  return value == null ? null : toDate(value);
}

function clampInteger(value: number | undefined, min: number, max: number, fallback: number): number {
  if (value === undefined) return fallback;
  if (!Number.isFinite(value)) return fallback;
  return Math.min(max, Math.max(min, Math.trunc(value)));
}

function epochMs(value: number | Date): number {
  const milliseconds = value instanceof Date ? value.getTime() : Number(value);
  if (!Number.isFinite(milliseconds)) {
    throw new RangeError('now must be a valid epoch timestamp.');
  }
  return Math.trunc(milliseconds);
}

function limitValue(value: number, fallback: number): number {
  if (!Number.isFinite(value)) return fallback;
  return Math.min(500, Math.max(1, Math.trunc(value)));
}

function mapAccount(row: AccountRow): Account {
  return {
    clerkUserId: row.clerk_user_id,
    email: row.email,
    displayName: row.display_name,
    timezone: row.timezone,
    notifyHour: toNumber(row.notify_hour),
    notifyMinute: toNumber(row.notify_minute),
    emailEnabled: Boolean(row.email_enabled),
    plan: row.plan,
    discordUserId: row.discord_user_id,
    discordUsername: row.discord_username,
    createdAt: toDate(row.created_at),
    updatedAt: toDate(row.updated_at),
  };
}

function mapReminder(row: ReminderRow): ReminderRecord {
  return {
    id: toNumber(row.id),
    clerkUserId: row.clerk_user_id,
    localId: toNumber(row.local_id),
    title: row.title,
    merchant: row.merchant,
    bank: row.bank,
    amountMinor: toNumber(row.amount_minor),
    currency: row.currency,
    dueAt: toNumber(row.due_at),
    notifyAt: toNumber(row.notify_at),
    repeatRule: row.repeat_rule,
    category: row.category,
    notes: row.notes,
    leadDays: toNumber(row.lead_days),
    isPaid: Boolean(row.is_paid),
    isActive: Boolean(row.is_active),
    paidAt: row.paid_at == null ? null : toNumber(row.paid_at),
    discordNotifiedDueAt: toNumber(row.discord_notified_due_at),
    emailNotifiedDueAt: toNumber(row.email_notified_due_at),
    discordClaimedAt: toDateOrNull(row.discord_claimed_at),
    emailClaimedAt: toDateOrNull(row.email_claimed_at),
    createdAt: toDate(row.created_at),
    updatedAt: toDate(row.updated_at),
  };
}

function mapDueReminder(row: DueReminderRow): DueReminder {
  return {
    ...mapReminder(row),
    accountEmail: row.account_email,
    accountEmailEnabled: Boolean(row.account_email_enabled),
    accountTimezone: row.account_timezone,
    discordUserId: row.account_discord_user_id,
  };
}

function mapLinkCode(row: LinkCodeRow): LinkCode {
  return {
    code: row.code,
    discordUserId: row.discord_user_id,
    discordUsername: row.discord_username,
    createdAt: toDate(row.created_at),
    expiresAt: toDate(row.expires_at),
    usedAt: toDateOrNull(row.used_at),
  };
}

function mapDeviceKey(row: DeviceKeyRow): DeviceKeyRecord {
  return {
    id: toNumber(row.id),
    clerkUserId: row.clerk_user_id,
    keyHash: row.key_hash,
    createdAt: toDate(row.created_at),
    lastSeenAt: toDateOrNull(row.last_seen_at),
    revokedAt: toDateOrNull(row.revoked_at),
  };
}

function mapUsageSnapshot(row: UsageSnapshotRow): UsageSnapshotRecord {
  return {
    id: toNumber(row.id),
    clerkUserId: row.clerk_user_id,
    plan: row.plan,
    tokensUsed: toNumber(row.tokens_used),
    tokensLimit: toNumber(row.tokens_limit),
    periodEnd: row.period_end,
    createdAt: toDate(row.created_at),
  };
}

function claimColumn(channel: DeliveryChannel): 'discord_claimed_at' | 'email_claimed_at' {
  if (channel === 'discord') return 'discord_claimed_at';
  if (channel === 'email') return 'email_claimed_at';
  throw new TypeError('channel must be "discord" or "email".');
}

function notificationColumn(
  channel: DeliveryChannel,
): 'discord_notified_due_at' | 'email_notified_due_at' {
  if (channel === 'discord') return 'discord_notified_due_at';
  if (channel === 'email') return 'email_notified_due_at';
  throw new TypeError('channel must be "discord" or "email".');
}

export class Store {
  constructor(private readonly pool: Pool) {}

  async upsertAccount(input: UpsertAccountInput): Promise<Account> {
    const email = (input.email ?? '').trim().slice(0, 254);
    const displayName = (input.displayName ?? '').trim().slice(0, 120);
    const timezone = (input.timezone ?? '').trim().slice(0, 80) || 'UTC';
    const notifyHour = clampInteger(input.notifyHour, 0, 23, 9);
    const notifyMinute = clampInteger(input.notifyMinute, 0, 59, 0);
    const emailEnabled = input.emailEnabled ?? true;
    const plan = input.plan?.trim() || null;

    const result = await this.pool.query<AccountRow>(
      `INSERT INTO accounts
        (clerk_user_id, email, display_name, timezone, notify_hour, notify_minute,
         email_enabled, plan, updated_at)
       VALUES ($1, $2, $3, $4, $5, $6, $7, COALESCE($8, 'Free'), now())
       ON CONFLICT (clerk_user_id) DO UPDATE SET
         email = EXCLUDED.email,
         display_name = EXCLUDED.display_name,
         timezone = EXCLUDED.timezone,
         notify_hour = EXCLUDED.notify_hour,
         notify_minute = EXCLUDED.notify_minute,
         email_enabled = EXCLUDED.email_enabled,
         plan = COALESCE($8, accounts.plan),
         updated_at = now()
       RETURNING *`,
      [
        input.clerkUserId,
        email,
        displayName,
        timezone,
        notifyHour,
        notifyMinute,
        emailEnabled,
        plan,
      ],
    );
    const row = result.rows[0];
    if (!row) throw new Error('upsertAccount returned no row.');
    return mapAccount(row);
  }

  async getAccountByClerkId(clerkUserId: string): Promise<Account | null> {
    const result = await this.pool.query<AccountRow>(
      'SELECT * FROM accounts WHERE clerk_user_id = $1',
      [clerkUserId],
    );
    const row = result.rows[0];
    return row ? mapAccount(row) : null;
  }

  async getAccountByDiscordId(discordUserId: string): Promise<Account | null> {
    if (!discordUserId) return null;
    const result = await this.pool.query<AccountRow>(
      'SELECT * FROM accounts WHERE discord_user_id = $1',
      [discordUserId],
    );
    const row = result.rows[0];
    return row ? mapAccount(row) : null;
  }

  async linkDiscord(clerkUserId: string, discord: DiscordIdentity): Promise<Account> {
    const result = await this.pool.query<AccountRow>(
      `INSERT INTO accounts
        (clerk_user_id, discord_user_id, discord_username, updated_at)
       VALUES ($1, $2, $3, now())
       ON CONFLICT (clerk_user_id) DO UPDATE SET
         discord_user_id = EXCLUDED.discord_user_id,
         discord_username = EXCLUDED.discord_username,
         updated_at = now()
       RETURNING *`,
      [clerkUserId, discord.id, discord.username],
    );
    const row = result.rows[0];
    if (!row) throw new Error('linkDiscord returned no row.');
    return mapAccount(row);
  }

  async unlinkDiscord(clerkUserId: string): Promise<Account | null> {
    const result = await this.pool.query<AccountRow>(
      `UPDATE accounts
       SET discord_user_id = NULL, discord_username = '', updated_at = now()
       WHERE clerk_user_id = $1
       RETURNING *`,
      [clerkUserId],
    );
    const row = result.rows[0];
    return row ? mapAccount(row) : null;
  }

  async deleteAccount(clerkUserId: string): Promise<boolean> {
    const result = await this.pool.query(
      'DELETE FROM accounts WHERE clerk_user_id = $1',
      [clerkUserId],
    );
    return (result.rowCount ?? 0) > 0;
  }

  /**
   * Replaces a user's server-side reminder snapshot. The upsert preserves the
   * notified markers while `due_at` is unchanged, and reminders absent from
   * the new snapshot are deleted.
   */
  async replaceReminders(clerkUserId: string, reminders: readonly ReminderInput[]): Promise<number> {
    const client = await this.pool.connect();
    try {
      await client.query('BEGIN');

      for (const reminder of reminders) {
        await client.query(
          `INSERT INTO reminders
            (clerk_user_id, local_id, title, merchant, bank, amount_minor, currency,
             due_at, notify_at, repeat_rule, category, notes, lead_days, is_paid,
             is_active, paid_at, discord_notified_due_at, email_notified_due_at,
             discord_claimed_at, email_claimed_at, updated_at)
           VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13,
                   $14, $15, $16, 0, 0, NULL, NULL, now())
           ON CONFLICT (clerk_user_id, local_id) DO UPDATE SET
             title = EXCLUDED.title,
             merchant = EXCLUDED.merchant,
             bank = EXCLUDED.bank,
             amount_minor = EXCLUDED.amount_minor,
             currency = EXCLUDED.currency,
             due_at = EXCLUDED.due_at,
             notify_at = EXCLUDED.notify_at,
             repeat_rule = EXCLUDED.repeat_rule,
             category = EXCLUDED.category,
             notes = EXCLUDED.notes,
             lead_days = EXCLUDED.lead_days,
             is_paid = EXCLUDED.is_paid,
             is_active = EXCLUDED.is_active,
             paid_at = EXCLUDED.paid_at,
             discord_notified_due_at = CASE
               WHEN reminders.due_at = EXCLUDED.due_at
               THEN reminders.discord_notified_due_at ELSE 0
             END,
             email_notified_due_at = CASE
               WHEN reminders.due_at = EXCLUDED.due_at
               THEN reminders.email_notified_due_at ELSE 0
             END,
             discord_claimed_at = CASE
               WHEN reminders.due_at = EXCLUDED.due_at
               THEN reminders.discord_claimed_at ELSE NULL
             END,
             email_claimed_at = CASE
               WHEN reminders.due_at = EXCLUDED.due_at
               THEN reminders.email_claimed_at ELSE NULL
             END,
             updated_at = now()`,
          [
            clerkUserId,
            reminder.localId,
            reminder.title,
            reminder.merchant,
            reminder.bank,
            reminder.amountMinor,
            reminder.currency,
            reminder.dueAt,
            reminder.notifyAt,
            reminder.repeatRule,
            reminder.category,
            reminder.notes,
            reminder.leadDays,
            reminder.isPaid,
            reminder.isActive,
            reminder.paidAt,
          ],
        );
      }

      await client.query(
        `DELETE FROM reminders
         WHERE clerk_user_id = $1
           AND NOT (local_id = ANY($2::bigint[]))`,
        [clerkUserId, reminders.map((reminder) => reminder.localId)],
      );

      await client.query('COMMIT');
      return reminders.length;
    } catch (error) {
      await client.query('ROLLBACK').catch(() => undefined);
      throw error;
    } finally {
      client.release();
    }
  }

  /**
   * Reminders whose notify time has arrived and that still need at least one
   * delivery channel. Email is only considered while the account has email
   * enabled and an address, matching the scheduler's eligibility rules.
   */
  async dueReminders(now: number | Date = Date.now(), limit = 25): Promise<DueReminder[]> {
    const nowMs = epochMs(now);
    const cappedLimit = limitValue(limit, 25);
    const result = await this.pool.query<DueReminderRow>(
      `SELECT r.*,
              a.email AS account_email,
              a.email_enabled AS account_email_enabled,
              a.timezone AS account_timezone,
              a.discord_user_id AS account_discord_user_id
       FROM reminders r
       JOIN accounts a ON a.clerk_user_id = r.clerk_user_id
       WHERE r.is_active = TRUE
         AND r.is_paid = FALSE
         AND r.notify_at <= $1
         AND (
           r.discord_notified_due_at <> r.due_at
           OR (
             a.email_enabled = TRUE
             AND a.email <> ''
             AND r.email_notified_due_at <> r.due_at
           )
         )
       ORDER BY r.notify_at ASC, r.id ASC
       LIMIT $2`,
      [nowMs, cappedLimit],
    );
    return result.rows.map(mapDueReminder);
  }

  /**
   * Claims a delivery channel with a five-minute lease. Returns false when a
   * fresh claim already exists, so concurrent workers cannot double-send.
   */
  async claimReminderDelivery(
    id: number,
    channel: DeliveryChannel,
    now: number | Date = new Date(),
  ): Promise<boolean> {
    const column = claimColumn(channel);
    const claimedAt = new Date(epochMs(now));
    const result = await this.pool.query(
      `UPDATE reminders
       SET ${column} = $2::timestamptz, updated_at = now()
       WHERE id = $1
         AND (${column} IS NULL OR ${column} < $2::timestamptz - interval '5 minutes')`,
      [id, claimedAt],
    );
    return (result.rowCount ?? 0) > 0;
  }

  async markDiscordNotified(id: number): Promise<boolean> {
    return this.markNotified(id, 'discord');
  }

  async markEmailNotified(id: number): Promise<boolean> {
    return this.markNotified(id, 'email');
  }

  private async markNotified(id: number, channel: DeliveryChannel): Promise<boolean> {
    const marker = notificationColumn(channel);
    const claim = claimColumn(channel);
    const result = await this.pool.query(
      `UPDATE reminders
       SET ${marker} = due_at, ${claim} = NULL, updated_at = now()
       WHERE id = $1`,
      [id],
    );
    return (result.rowCount ?? 0) > 0;
  }

  async releaseReminderClaim(id: number, channel: DeliveryChannel): Promise<boolean> {
    const claim = claimColumn(channel);
    const result = await this.pool.query(
      `UPDATE reminders
       SET ${claim} = NULL, updated_at = now()
       WHERE id = $1`,
      [id],
    );
    return (result.rowCount ?? 0) > 0;
  }

  async listUpcomingByDiscordId(
    discordUserId: string,
    limit = 10,
    now: number | Date = Date.now(),
  ): Promise<ReminderRecord[]> {
    if (!discordUserId) return [];
    const nowMs = epochMs(now);
    const cappedLimit = limitValue(limit, 10);
    const result = await this.pool.query<ReminderRow>(
      `SELECT r.*
       FROM reminders r
       JOIN accounts a ON a.clerk_user_id = r.clerk_user_id
       WHERE a.discord_user_id = $1
         AND r.is_active = TRUE
         AND r.is_paid = FALSE
         AND r.due_at >= $2
       ORDER BY r.due_at ASC, r.id ASC
       LIMIT $3`,
      [discordUserId, nowMs, cappedLimit],
    );
    return result.rows.map(mapReminder);
  }

  /**
   * Moves a reminder forward for a linked Discord user. Pass a number of days
   * (the common "snooze 1 day" action) or explicit epoch timestamps.
   */
  async snoozeReminderByDiscordId(
    discordUserId: string,
    localId: number,
    options: number | SnoozeReminderOptions = 1,
  ): Promise<ReminderRecord | null> {
    const settings = typeof options === 'number' ? { days: options } : options;
    const days = clampInteger(settings.days, 1, 3650, 1);
    const dueAt = optionalEpoch(settings.dueAt);
    const notifyAt = optionalEpoch(settings.notifyAt);

    const result = await this.pool.query<ReminderRow>(
      `UPDATE reminders AS r
       SET due_at = COALESCE($3::bigint, r.due_at + $4::bigint * 86400000),
           notify_at = COALESCE(
             $5::bigint,
             r.notify_at + (
               COALESCE($3::bigint, r.due_at + $4::bigint * 86400000) - r.due_at
             )
           ),
           discord_notified_due_at = 0,
           email_notified_due_at = 0,
           discord_claimed_at = NULL,
           email_claimed_at = NULL,
           updated_at = now()
       FROM accounts AS a
       WHERE a.clerk_user_id = r.clerk_user_id
         AND a.discord_user_id = $1
         AND r.local_id = $2
         AND r.is_paid = FALSE
       RETURNING r.*`,
      [discordUserId, localId, dueAt, days, notifyAt],
    );
    const row = result.rows[0];
    return row ? mapReminder(row) : null;
  }

  async markPaidByDiscordId(
    discordUserId: string,
    localId: number,
    now: number | Date = Date.now(),
  ): Promise<ReminderRecord | null> {
    const paidAt = epochMs(now);
    const result = await this.pool.query<ReminderRow>(
      `UPDATE reminders AS r
       SET is_paid = TRUE,
           is_active = FALSE,
           paid_at = $3,
           discord_notified_due_at = 0,
           email_notified_due_at = 0,
           discord_claimed_at = NULL,
           email_claimed_at = NULL,
           updated_at = now()
       FROM accounts AS a
       WHERE a.clerk_user_id = r.clerk_user_id
         AND a.discord_user_id = $1
         AND r.local_id = $2
         AND r.is_paid = FALSE
       RETURNING r.*`,
      [discordUserId, localId, paidAt],
    );
    const row = result.rows[0];
    return row ? mapReminder(row) : null;
  }

  async createLinkCode(input: CreateLinkCodeInput): Promise<LinkCode> {
    const code = randomBytes(24).toString('hex');
    const requestedTtl = input.ttlMs === undefined ? DEFAULT_LINK_CODE_TTL_MS : Number(input.ttlMs);
    const ttlMs = Number.isFinite(requestedTtl)
      ? Math.trunc(requestedTtl)
      : DEFAULT_LINK_CODE_TTL_MS;
    const expiresAt = new Date(Date.now() + ttlMs);

    const result = await this.pool.query<LinkCodeRow>(
      `INSERT INTO link_codes (code, discord_user_id, discord_username, expires_at)
       VALUES ($1, $2, $3, $4)
       RETURNING *`,
      [code, input.discordUserId, input.discordUsername, expiresAt],
    );
    const row = result.rows[0];
    if (!row) throw new Error('createLinkCode returned no row.');
    return mapLinkCode(row);
  }

  async getLinkCode(code: string): Promise<LinkCode | null> {
    const result = await this.pool.query<LinkCodeRow>(
      'SELECT * FROM link_codes WHERE code = $1',
      [code],
    );
    const row = result.rows[0];
    return row ? mapLinkCode(row) : null;
  }

  /** Atomically consumes a fresh code. Expired or used codes return null. */
  async confirmLinkCode(code: string): Promise<LinkCode | null> {
    const result = await this.pool.query<LinkCodeRow>(
      `UPDATE link_codes
       SET used_at = now()
       WHERE code = $1 AND used_at IS NULL AND expires_at > now()
       RETURNING *`,
      [code],
    );
    const row = result.rows[0];
    return row ? mapLinkCode(row) : null;
  }

  async issueDeviceKey(clerkUserId: string, key: string = generateDeviceKey()): Promise<IssuedDeviceKey> {
    const keyHash = hashDeviceKey(key);
    const result = await this.pool.query<DeviceKeyRow>(
      `INSERT INTO device_keys (clerk_user_id, key_hash)
       VALUES ($1, $2)
       RETURNING *`,
      [clerkUserId, keyHash],
    );
    const row = result.rows[0];
    if (!row) throw new Error('issueDeviceKey returned no row.');
    return { ...mapDeviceKey(row), key };
  }

  /**
   * Looks up an active key and records last-seen time. Accepts either the raw
   * bearer key or its SHA-256 digest.
   */
  async findDeviceKey(keyOrHash: string): Promise<DeviceKeyRecord | null> {
    if (!keyOrHash) return null;
    const keyHash = normaliseDeviceKey(keyOrHash);
    const result = await this.pool.query<DeviceKeyRow>(
      `UPDATE device_keys
       SET last_seen_at = now()
       WHERE key_hash = $1 AND revoked_at IS NULL
       RETURNING *`,
      [keyHash],
    );
    const row = result.rows[0];
    return row ? mapDeviceKey(row) : null;
  }

  /** Revokes by database id, raw device key or key digest. */
  async revokeDeviceKey(idOrKey: number | string): Promise<boolean> {
    const result =
      typeof idOrKey === 'number'
        ? await this.pool.query(
            `UPDATE device_keys
             SET revoked_at = COALESCE(revoked_at, now())
             WHERE id = $1
             RETURNING id`,
            [idOrKey],
          )
        : await this.pool.query(
            `UPDATE device_keys
             SET revoked_at = COALESCE(revoked_at, now())
             WHERE key_hash = $1
             RETURNING id`,
            [normaliseDeviceKey(idOrKey)],
          );
    return (result.rowCount ?? 0) > 0;
  }

  /**
   * Revokes a device key only when it belongs to the given account, so the
   * dashboard cannot revoke another user's key by guessing an id.
   */
  async revokeDeviceKeyForUser(id: number, clerkUserId: string): Promise<boolean> {
    if (!Number.isSafeInteger(id) || id <= 0) return false;
    const result = await this.pool.query(
      `UPDATE device_keys
       SET revoked_at = COALESCE(revoked_at, now())
       WHERE id = $1 AND clerk_user_id = $2
       RETURNING id`,
      [id, clerkUserId],
    );
    return (result.rowCount ?? 0) > 0;
  }

  /** Device keys belonging to an account, newest first. Hashes are included. */
  async listDeviceKeys(clerkUserId: string): Promise<DeviceKeyRecord[]> {
    const result = await this.pool.query<DeviceKeyRow>(
      `SELECT *
       FROM device_keys
       WHERE clerk_user_id = $1
       ORDER BY created_at DESC, id DESC
       LIMIT 50`,
      [clerkUserId],
    );
    return result.rows.map(mapDeviceKey);
  }

  /** Aggregate reminder counts for the dashboard without loading every row. */
  async reminderSummary(clerkUserId: string): Promise<ReminderSummary> {
    const result = await this.pool.query<{
      total: string | number;
      active: string | number;
      paid: string | number;
      next_due_at: string | number | null;
    }>(
      `SELECT count(*) AS total,
              count(*) FILTER (WHERE is_active AND NOT is_paid) AS active,
              count(*) FILTER (WHERE is_paid) AS paid,
              min(due_at) FILTER (WHERE is_active AND NOT is_paid) AS next_due_at
       FROM reminders
       WHERE clerk_user_id = $1`,
      [clerkUserId],
    );
    const row = result.rows[0];
    return {
      total: toNumber(row?.total),
      active: toNumber(row?.active),
      paid: toNumber(row?.paid),
      nextDueAt: row?.next_due_at == null ? null : toNumber(row.next_due_at),
    };
  }

  /** Lightweight liveness probe for the public health endpoint. */
  async ping(): Promise<boolean> {
    try {
      await this.pool.query('SELECT 1');
      return true;
    } catch {
      return false;
    }
  }

  async addUsageSnapshot(input: AddUsageSnapshotInput): Promise<UsageSnapshotRecord> {
    const result = await this.pool.query<UsageSnapshotRow>(
      `INSERT INTO usage_snapshots
         (clerk_user_id, plan, tokens_used, tokens_limit, period_end)
       VALUES ($1, $2, $3, $4, $5)
       RETURNING *`,
      [
        input.clerkUserId,
        input.plan,
        input.tokensUsed,
        input.tokensLimit,
        input.periodEnd ?? null,
      ],
    );
    const row = result.rows[0];
    if (!row) throw new Error('addUsageSnapshot returned no row.');
    return mapUsageSnapshot(row);
  }

  async latestUsage(clerkUserId: string): Promise<UsageSnapshotRecord | null> {
    const result = await this.pool.query<UsageSnapshotRow>(
      `SELECT *
       FROM usage_snapshots
       WHERE clerk_user_id = $1
       ORDER BY created_at DESC, id DESC
       LIMIT 1`,
      [clerkUserId],
    );
    const row = result.rows[0];
    return row ? mapUsageSnapshot(row) : null;
  }

  /** Returns true when the address was new, false when already present. */
  async addWaitlistEmail(email: string): Promise<boolean> {
    const normalised = String(email).trim().toLowerCase();
    if (!normalised) return false;
    const result = await this.pool.query(
      'INSERT INTO waitlist (email) VALUES ($1) ON CONFLICT (email) DO NOTHING',
      [normalised],
    );
    return (result.rowCount ?? 0) > 0;
  }
}

function normaliseDeviceKey(value: string): string {
  return /^(?:eivoid|void)_/.test(value) ? hashDeviceKey(value) : value;
}

function optionalEpoch(value: number | undefined): number | null {
  if (value === undefined) return null;
  const milliseconds = Number(value);
  if (!Number.isFinite(milliseconds)) {
    throw new RangeError('dueAt and notifyAt must be epoch timestamps.');
  }
  return Math.trunc(milliseconds);
}
