import type { Pool } from 'pg';
import { afterAll, beforeAll, describe, expect, it } from 'vitest';
import {
  LATEST_SCHEMA_VERSION,
  Store,
  createPool,
  generateDeviceKey,
  hashDeviceKey,
  migrate,
  migrations,
} from '../src/index.js';
import type { ReminderInput } from '../src/index.js';

const DAY = 86_400_000;

describe('createPool', () => {
  it('enables verified TLS for remote database URLs', async () => {
    const pool = createPool('postgres://user:secret@db.example.com:5432/ei_void');
    try {
      expect(pool.options.ssl).toMatchObject({ rejectUnauthorized: true });
    } finally {
      await pool.end();
    }
  });

  it('leaves localhost connections unencrypted', async () => {
    const pool = createPool('postgres://user:secret@localhost:5432/ei_void');
    try {
      expect(pool.options.ssl).toBeUndefined();
    } finally {
      await pool.end();
    }
  });

  it('honours sslmode=disable and explicit ssl overrides', async () => {
    const plain = createPool(
      'postgresql://user:secret@db.example.com:5432/ei_void?sslmode=disable',
    );
    try {
      expect(plain.options.ssl).toBeUndefined();
    } finally {
      await plain.end();
    }

    const forced = createPool('postgresql://user:secret@localhost:5432/ei_void', {
      ssl: { rejectUnauthorized: false },
    });
    try {
      expect(forced.options.ssl).toMatchObject({ rejectUnauthorized: false });
    } finally {
      await forced.end();
    }
  });

  it('rejects non-postgres URLs without echoing credentials', () => {
    try {
      createPool('mysql://user:topsecret@db.example.com/ei_void');
      throw new Error('expected createPool to throw');
    } catch (error) {
      expect(String(error)).toMatch(/postgres/);
      expect(String(error)).not.toContain('topsecret');
    }
  });
});

describe('migrations', () => {
  it('embeds one unified schema migration and no legacy tables', () => {
    const migration = migrations[0];
    expect(migration?.version).toBe(1);
    expect(migration?.name).toBe('001_unified_schema');
    expect(LATEST_SCHEMA_VERSION).toBe(1);

    const sql = migration?.sql ?? '';
    for (const table of [
      'accounts',
      'reminders',
      'link_codes',
      'device_keys',
      'usage_snapshots',
      'waitlist',
    ]) {
      expect(sql).toContain(`CREATE TABLE ${table}`);
    }
    for (const legacy of ['app_users', 'app_reminders', 'discord_links', 'CREATE TABLE users']) {
      expect(sql).not.toContain(legacy);
    }
    for (const column of [
      'discord_notified_due_at',
      'email_notified_due_at',
      'discord_claimed_at',
      'email_claimed_at',
      'tokens_used',
      'tokens_limit',
      'period_end',
    ]) {
      expect(sql).toContain(column);
    }
  });

  it('generates and hashes device keys', () => {
    const key = generateDeviceKey();
    expect(key).toMatch(/^void_[A-Za-z0-9_-]+$/);
    expect(key.length).toBeGreaterThan(20);
    expect(hashDeviceKey(key)).toMatch(/^[a-f0-9]{64}$/);
    expect(hashDeviceKey(key)).toBe(hashDeviceKey(key));
    expect(hashDeviceKey(key)).not.toBe(hashDeviceKey(generateDeviceKey()));
  });
});

const TEST_DATABASE_URL = process.env.TEST_DATABASE_URL;
const describeWithDatabase = TEST_DATABASE_URL ? describe : describe.skip;

describeWithDatabase('Store (PostgreSQL integration)', () => {
  let pool: Pool;
  let store: Store;

  const suffix = `${Date.now().toString(36)}${Math.random().toString(36).slice(2, 8)}`;
  const clerkUserId = `user_test_${suffix}`;
  const otherClerkUserId = `user_test_other_${suffix}`;
  const discordUserId = `9${Date.now().toString().padStart(15, '0').slice(-15)}`;
  const otherDiscordUserId = `8${(Date.now() + 1).toString().padStart(15, '0').slice(-15)}`;
  const waitlistEmail = `waitlist_${suffix}@example.com`;

  const now = Date.now();

  function reminder(overrides: Partial<ReminderInput> = {}): ReminderInput {
    return {
      localId: 1,
      title: 'Rent',
      merchant: 'Landlord',
      bank: 'HDFC',
      amountMinor: 100_000,
      currency: 'INR',
      dueAt: now + DAY,
      notifyAt: now - 1_000,
      repeatRule: 'MONTHLY',
      category: 'RENT',
      notes: '',
      leadDays: 4,
      isPaid: false,
      isActive: true,
      paidAt: null,
      ...overrides,
    };
  }

  beforeAll(async () => {
    pool = createPool(TEST_DATABASE_URL as string, { max: 3 });
    await migrate(pool);
    store = new Store(pool);
    await store.upsertAccount({
      clerkUserId,
      email: 'seed@example.com',
      displayName: 'Seed',
      timezone: 'UTC',
      notifyHour: 9,
      notifyMinute: 0,
      emailEnabled: true,
      plan: 'Free',
    });
  });

  afterAll(async () => {
    if (!pool) return;
    await pool.query('DELETE FROM accounts WHERE clerk_user_id = ANY($1::text[])', [
      [clerkUserId, otherClerkUserId],
    ]);
    await pool.query('DELETE FROM link_codes WHERE discord_user_id = ANY($1::text[])', [
      [discordUserId, otherDiscordUserId],
    ]);
    await pool.query('DELETE FROM waitlist WHERE email = $1', [waitlistEmail]);
    await pool.end();
  });

  it('runs the account, reminder, delivery and snooze lifecycle', async () => {
    expect(await store.getAccountByClerkId(clerkUserId)).not.toBeNull();
    expect(await store.getAccountByDiscordId(discordUserId)).toBeNull();

    const account = await store.upsertAccount({
      clerkUserId,
      email: 'cat@example.com',
      displayName: 'Void Cat',
      timezone: 'Asia/Kolkata',
      notifyHour: 8,
      notifyMinute: 15,
      emailEnabled: true,
      plan: 'Pro',
    });
    expect(account).toMatchObject({
      clerkUserId,
      email: 'cat@example.com',
      displayName: 'Void Cat',
      timezone: 'Asia/Kolkata',
      notifyHour: 8,
      notifyMinute: 15,
      plan: 'Pro',
      discordUserId: null,
    });

    const linked = await store.linkDiscord(clerkUserId, {
      id: discordUserId,
      username: 'voidcat',
    });
    expect(linked.discordUserId).toBe(discordUserId);
    expect(linked.discordUsername).toBe('voidcat');
    expect((await store.getAccountByDiscordId(discordUserId))?.clerkUserId).toBe(clerkUserId);

    const unlinked = await store.unlinkDiscord(clerkUserId);
    expect(unlinked?.discordUserId).toBeNull();
    expect(await store.getAccountByDiscordId(discordUserId)).toBeNull();
    await store.linkDiscord(clerkUserId, { id: discordUserId, username: 'voidcat' });

    const second = reminder({ localId: 2, title: 'Insurance', notifyAt: now + 30 * DAY });
    expect(await store.replaceReminders(clerkUserId, [reminder(), second])).toBe(2);

    const due = await store.dueReminders(now, 25);
    const dueOne = due.find((item) => item.localId === 1);
    expect(dueOne).toMatchObject({
      title: 'Rent',
      clerkUserId,
      accountEmail: 'cat@example.com',
      accountEmailEnabled: true,
      accountTimezone: 'Asia/Kolkata',
      discordUserId,
    });
    expect(due.some((item) => item.localId === 2)).toBe(false);

    const reminderId = dueOne?.id as number;
    const claimedAt = new Date(now);
    expect(await store.claimReminderDelivery(reminderId, 'discord', claimedAt)).toBe(true);
    expect(
      await store.claimReminderDelivery(reminderId, 'discord', new Date(now + 1_000)),
    ).toBe(false);
    expect(await store.releaseReminderClaim(reminderId, 'discord')).toBe(true);
    expect(
      await store.claimReminderDelivery(reminderId, 'discord', new Date(now + 1_000)),
    ).toBe(true);
    await store.releaseReminderClaim(reminderId, 'discord');

    await pool.query('UPDATE reminders SET discord_claimed_at = $2 WHERE id = $1', [
      reminderId,
      new Date(now - 10 * 60_000),
    ]);
    expect(await store.claimReminderDelivery(reminderId, 'discord', new Date(now))).toBe(true);

    expect(await store.markDiscordNotified(reminderId)).toBe(true);
    let marker = await pool.query<{ discord_notified_due_at: string }>(
      'SELECT discord_notified_due_at FROM reminders WHERE id = $1',
      [reminderId],
    );
    expect(Number(marker.rows[0]?.discord_notified_due_at)).toBe(now + DAY);

    expect(await store.markEmailNotified(reminderId)).toBe(true);
    const afterBoth = await store.dueReminders(now, 25);
    expect(afterBoth.some((item) => item.id === reminderId)).toBe(false);

    expect(
      await store.replaceReminders(clerkUserId, [
        reminder({ title: 'Rent updated' }),
        reminder({ localId: 3, title: 'New reminder' }),
      ]),
    ).toBe(2);
    marker = await pool.query<{ discord_notified_due_at: string }>(
      'SELECT discord_notified_due_at FROM reminders WHERE id = $1',
      [reminderId],
    );
    expect(Number(marker.rows[0]?.discord_notified_due_at)).toBe(now + DAY);
    const deleted = await pool.query(
      'SELECT 1 FROM reminders WHERE clerk_user_id = $1 AND local_id = 2',
      [clerkUserId],
    );
    expect(deleted.rowCount).toBe(0);

    const changedDue = now + 2 * DAY;
    expect(
      await store.replaceReminders(clerkUserId, [
        reminder({ title: 'Rent updated', dueAt: changedDue }),
      ]),
    ).toBe(1);
    marker = await pool.query<{ discord_notified_due_at: string }>(
      'SELECT discord_notified_due_at FROM reminders WHERE id = $1',
      [reminderId],
    );
    expect(Number(marker.rows[0]?.discord_notified_due_at)).toBe(0);
    const removed = await pool.query(
      'SELECT 1 FROM reminders WHERE clerk_user_id = $1 AND local_id = 3',
      [clerkUserId],
    );
    expect(removed.rowCount).toBe(0);

    const upcoming = await store.listUpcomingByDiscordId(discordUserId, 10, now);
    expect(upcoming).toHaveLength(1);
    expect(upcoming[0]?.localId).toBe(1);
    expect(upcoming[0]?.dueAt).toBe(changedDue);

    const snoozed = await store.snoozeReminderByDiscordId(discordUserId, 1, { days: 2 });
    expect(snoozed?.dueAt).toBe(changedDue + 2 * DAY);
    expect(snoozed?.notifyAt).toBe(now - 1_000 + 2 * DAY);
    expect(snoozed?.discordNotifiedDueAt).toBe(0);
    expect(snoozed?.emailNotifiedDueAt).toBe(0);

    const paid = await store.markPaidByDiscordId(discordUserId, 1, now);
    expect(paid?.isPaid).toBe(true);
    expect(paid?.isActive).toBe(false);
    expect(paid?.paidAt).toBe(now);
    expect(await store.markPaidByDiscordId(discordUserId, 1, now)).toBeNull();
    expect(await store.snoozeReminderByDiscordId(discordUserId, 1, 1)).toBeNull();
    expect(await store.listUpcomingByDiscordId(discordUserId, 10, now)).toHaveLength(0);
  });

  it('creates single-use expiring link codes', async () => {
    const code = await store.createLinkCode({
      discordUserId,
      discordUsername: 'voidcat',
      ttlMs: 60_000,
    });
    expect(code.code).toMatch(/^[a-f0-9]{48}$/);
    expect(code.usedAt).toBeNull();
    expect(await store.getLinkCode(code.code)).toMatchObject({ code: code.code });

    const confirmed = await store.confirmLinkCode(code.code);
    expect(confirmed?.usedAt).toBeInstanceOf(Date);
    expect(await store.confirmLinkCode(code.code)).toBeNull();

    const expired = await store.createLinkCode({
      discordUserId,
      discordUsername: 'voidcat',
      ttlMs: -1,
    });
    expect(await store.confirmLinkCode(expired.code)).toBeNull();
    expect(await store.getLinkCode('missing-code')).toBeNull();
  });

  it('issues, tracks and revokes device keys', async () => {
    const issued = await store.issueDeviceKey(clerkUserId);
    expect(issued.key).toMatch(/^void_/);
    expect(issued.keyHash).toBe(hashDeviceKey(issued.key));
    expect(issued.revokedAt).toBeNull();

    const found = await store.findDeviceKey(issued.keyHash);
    expect(found).toMatchObject({ id: issued.id, clerkUserId });
    expect(found?.lastSeenAt).toBeInstanceOf(Date);

    expect(await store.revokeDeviceKey(issued.id)).toBe(true);
    expect(await store.findDeviceKey(issued.keyHash)).toBeNull();

    const second = await store.issueDeviceKey(clerkUserId);
    expect(await store.revokeDeviceKey(second.keyHash)).toBe(true);
    expect(await store.revokeDeviceKey(999_999_999)).toBe(false);
  });

  it('stores usage snapshots and waitlist emails', async () => {
    const first = await store.addUsageSnapshot({
      clerkUserId,
      plan: 'Pro',
      tokensUsed: 1_000,
      tokensLimit: 80_000_000,
      periodEnd: '2026-12-01',
    });
    expect(first).toMatchObject({
      clerkUserId,
      plan: 'Pro',
      tokensUsed: 1_000,
      tokensLimit: 80_000_000,
      periodEnd: '2026-12-01',
    });
    expect(first.createdAt).toBeInstanceOf(Date);

    const second = await store.addUsageSnapshot({
      clerkUserId,
      plan: 'Pro',
      tokensUsed: 2_000,
      tokensLimit: 80_000_000,
    });
    expect((await store.latestUsage(clerkUserId))?.id).toBe(second.id);
    expect((await store.latestUsage(clerkUserId))?.periodEnd).toBeNull();
    expect(await store.latestUsage('user_missing')).toBeNull();

    expect(await store.addWaitlistEmail(`  ${waitlistEmail.toUpperCase()} `)).toBe(true);
    expect(await store.addWaitlistEmail(waitlistEmail)).toBe(false);
    expect(await store.addWaitlistEmail('')).toBe(false);
  });

  it('deletes an account and cascades its rows', async () => {
    const cascadeUser = otherClerkUserId;
    await store.upsertAccount({ clerkUserId: cascadeUser, email: 'cascade@example.com' });
    await store.replaceReminders(cascadeUser, [reminder()]);
    const key = await store.issueDeviceKey(cascadeUser);
    await store.addUsageSnapshot({
      clerkUserId: cascadeUser,
      plan: 'Free',
      tokensUsed: 1,
      tokensLimit: 30_000_000,
    });

    expect(await store.deleteAccount(cascadeUser)).toBe(true);
    expect(await store.getAccountByClerkId(cascadeUser)).toBeNull();
    const reminders = await pool.query('SELECT 1 FROM reminders WHERE clerk_user_id = $1', [
      cascadeUser,
    ]);
    const deviceKeys = await pool.query('SELECT 1 FROM device_keys WHERE clerk_user_id = $1', [
      cascadeUser,
    ]);
    const usage = await pool.query('SELECT 1 FROM usage_snapshots WHERE clerk_user_id = $1', [
      cascadeUser,
    ]);
    expect(reminders.rowCount).toBe(0);
    expect(deviceKeys.rowCount).toBe(0);
    expect(usage.rowCount).toBe(0);
    expect(await store.findDeviceKey(key.keyHash)).toBeNull();
    expect(await store.deleteAccount(cascadeUser)).toBe(false);
  });
});
