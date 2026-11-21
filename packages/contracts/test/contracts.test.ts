import { describe, expect, it } from 'vitest';
import {
  CATEGORIES,
  CURRENCIES,
  ERROR_CODES,
  PLAN_LIMITS,
  REPEAT_RULES,
  accountResponseSchema,
  deviceKeyIssueRequestSchema,
  discordLinkCodeRequestSchema,
  discordLinkRequestSchema,
  errorCodeSchema,
  errorResponseSchema,
  normalizeReminder,
  planLimit,
  reminderInputSchema,
  reminderProfileSchema,
  reminderSyncRequestSchema,
  reminderSyncResponseSchema,
  usageSnapshotInputSchema,
  usageSnapshotRecordSchema,
  usageSnapshotSchema,
} from '../src/index.js';

const DUE_AT = 1_800_000_000_000;
const NOTIFY_AT = DUE_AT - 4 * 86_400_000;

describe('constants', () => {
  it('exposes the reminder enums in the Ei Snore order', () => {
    expect(REPEAT_RULES).toEqual(['NONE', 'WEEKLY', 'MONTHLY', 'QUARTERLY', 'YEARLY']);
    expect(CATEGORIES).toEqual([
      'BILL',
      'SUBSCRIPTION',
      'EMI',
      'RENT',
      'INSURANCE',
      'OTHER',
    ]);
    expect(CURRENCIES).toEqual(['INR', 'USD']);
  });

  it('maps plans to token limits and falls back to Free', () => {
    expect(Object.isFrozen(PLAN_LIMITS)).toBe(true);
    expect(planLimit('Free')).toBe(30_000_000);
    expect(planLimit('Pro')).toBe(80_000_000);
    expect(planLimit('Max')).toBe(200_000_000);
    expect(planLimit('Enterprise')).toBe(30_000_000);
    expect(planLimit(undefined)).toBe(30_000_000);
    expect(planLimit('')).toBe(30_000_000);
  });
});

describe('normalizeReminder', () => {
  it('matches the backend normalization and truncation rules', () => {
    const normalized = normalizeReminder({
      localId: '42.9',
      title: `  Rent ${'x'.repeat(200)}  `,
      merchant: '  Landlord ',
      bank: ' HDFC ',
      amountMinor: '12345.8',
      currency: 'inr',
      dueAt: String(DUE_AT),
      notifyAt: NOTIFY_AT,
      repeatRule: 'monthly',
      category: ' rent ',
      notes: ` ${'n'.repeat(1200)} `,
      leadDays: 99,
      isPaid: 0,
      isActive: 'yes',
      paidAt: '123.9',
    });

    expect(normalized).not.toBeNull();
    expect(normalized).toMatchObject({
      localId: 42,
      merchant: 'Landlord',
      bank: 'HDFC',
      amountMinor: 12345,
      currency: 'INR',
      dueAt: DUE_AT,
      notifyAt: NOTIFY_AT,
      repeatRule: 'MONTHLY',
      category: 'RENT',
      leadDays: 30,
      isPaid: false,
      isActive: true,
      paidAt: 123,
    });
    expect(normalized?.title).toHaveLength(180);
    expect(normalized?.title.startsWith('Rent ')).toBe(true);
    expect(normalized?.notes).toHaveLength(1000);
  });

  it('applies defaults for an otherwise valid reminder', () => {
    const normalized = normalizeReminder({
      localId: 1,
      dueAt: DUE_AT,
      notifyAt: NOTIFY_AT,
    });

    expect(normalized).toEqual({
      localId: 1,
      title: 'Payment reminder',
      merchant: '',
      bank: '',
      amountMinor: 0,
      currency: 'INR',
      dueAt: DUE_AT,
      notifyAt: NOTIFY_AT,
      repeatRule: 'NONE',
      category: 'OTHER',
      notes: '',
      leadDays: 4,
      isPaid: false,
      isActive: false,
      paidAt: null,
    });
  });

  it('rejects missing or invalid identifiers and enums', () => {
    expect(normalizeReminder(null)).toBeNull();
    expect(normalizeReminder('not an object')).toBeNull();
    expect(normalizeReminder({ localId: 0, dueAt: DUE_AT, notifyAt: NOTIFY_AT })).toBeNull();
    expect(normalizeReminder({ localId: 1, dueAt: 0, notifyAt: NOTIFY_AT })).toBeNull();
    expect(normalizeReminder({ localId: 1, dueAt: DUE_AT, notifyAt: -1 })).toBeNull();
    expect(
      normalizeReminder({
        localId: 1,
        dueAt: DUE_AT,
        notifyAt: NOTIFY_AT,
        repeatRule: 'DAILY',
      }),
    ).toBeNull();
    expect(
      normalizeReminder({
        localId: 1,
        dueAt: DUE_AT,
        notifyAt: NOTIFY_AT,
        category: 'FOOD',
      }),
    ).toBeNull();
    expect(
      normalizeReminder({
        localId: 1,
        dueAt: DUE_AT,
        notifyAt: NOTIFY_AT,
        currency: 'EUR',
      }),
    ).toBeNull();
  });

  it('clamps lead days and amounts instead of rejecting them', () => {
    const low = normalizeReminder({
      localId: 1,
      dueAt: DUE_AT,
      notifyAt: NOTIFY_AT,
      leadDays: -10,
      amountMinor: -500,
    });
    expect(low?.leadDays).toBe(0);
    expect(low?.amountMinor).toBe(0);

    const high = normalizeReminder({
      localId: 1,
      dueAt: DUE_AT,
      notifyAt: NOTIFY_AT,
      leadDays: 99,
    });
    expect(high?.leadDays).toBe(30);
  });
});

describe('reminder schemas', () => {
  it('parses the sync request and normalizes nested reminders', () => {
    const parsed = reminderSyncRequestSchema.parse({
      profile: {
        timezone: 'Asia/Kolkata',
        notifyHour: 8,
        notifyMinute: 15,
        name: ' Void Cat ',
        email: 'CAT@Example.COM',
        emailEnabled: true,
      },
      reminders: [{ localId: 7, dueAt: DUE_AT, notifyAt: NOTIFY_AT, title: ' Rent ' }],
    });

    expect(parsed.profile).toEqual({
      timezone: 'Asia/Kolkata',
      notifyHour: 8,
      notifyMinute: 15,
      name: 'Void Cat',
      email: 'cat@example.com',
      emailEnabled: true,
    });
    expect(parsed.reminders[0]?.localId).toBe(7);
    expect(parsed.reminders[0]?.title).toBe('Rent');
  });

  it('fills in profile defaults and clamps out-of-range times', () => {
    const parsed = reminderSyncRequestSchema.parse({
      profile: { notifyHour: 99, notifyMinute: -4 },
      reminders: [],
    });

    expect(parsed.profile).toEqual({
      timezone: 'UTC',
      notifyHour: 23,
      notifyMinute: 0,
      name: '',
      email: '',
      emailEnabled: false,
    });
  });

  it('rejects invalid reminders and oversized syncs', () => {
    expect(reminderInputSchema.safeParse({ localId: 0 }).success).toBe(false);
    expect(
      reminderSyncRequestSchema.safeParse({
        reminders: [{ localId: 1, dueAt: DUE_AT, notifyAt: NOTIFY_AT, repeatRule: 'DAILY' }],
      }).success,
    ).toBe(false);

    const tooMany = Array.from({ length: 501 }, (_, index) => ({
      localId: index + 1,
      dueAt: DUE_AT,
      notifyAt: NOTIFY_AT,
    }));
    expect(reminderSyncRequestSchema.safeParse({ reminders: tooMany }).success).toBe(false);
  });

  it('rejects timezones the backend would reject', () => {
    expect(reminderProfileSchema.safeParse({ timezone: 'America/New York' }).success).toBe(false);
    expect(reminderProfileSchema.safeParse({ timezone: 'America/New_York' }).success).toBe(true);
  });
});

describe('API response schemas', () => {
  it('validates the account response shape', () => {
    expect(
      accountResponseSchema.parse({
        email: 'cat@example.com',
        displayName: 'Void Cat',
        discord: { id: '123456789012345678', username: 'voidcat' },
      }),
    ).toEqual({
      email: 'cat@example.com',
      displayName: 'Void Cat',
      discord: { id: '123456789012345678', username: 'voidcat' },
    });

    expect(
      accountResponseSchema.parse({ email: '', displayName: '', discord: null }),
    ).toEqual({ email: '', displayName: '', discord: null });
    expect(
      accountResponseSchema.safeParse({
        email: '',
        displayName: '',
        discord: { id: 'not-a-snowflake', username: 'x' },
      }).success,
    ).toBe(false);

    expect(
      reminderSyncResponseSchema.parse({ ok: true, synced: 2, discordLinked: false }),
    ).toEqual({ ok: true, synced: 2, discordLinked: false });
  });

  it('validates usage snapshot input and output', () => {
    const input = usageSnapshotInputSchema.parse({
      plan: 'Pro',
      tokensUsed: 1_000,
      tokensLimit: 80_000_000,
      periodEnd: '2026-12-01',
    });
    expect(input.periodEnd).toBe('2026-12-01');
    expect(
      usageSnapshotInputSchema.parse({
        plan: 'Free',
        tokensUsed: 0,
        tokensLimit: 30_000_000,
      }).periodEnd,
    ).toBeUndefined();
    expect(
      usageSnapshotInputSchema.safeParse({
        plan: 'Free',
        tokensUsed: -1,
        tokensLimit: 30_000_000,
      }).success,
    ).toBe(false);

    expect(
      usageSnapshotSchema.parse({
        id: 1,
        plan: 'Pro',
        tokensUsed: 1_000,
        tokensLimit: 80_000_000,
        periodEnd: null,
        createdAt: '2026-11-21T10:00:00.000Z',
      }),
    ).toMatchObject({ id: 1, periodEnd: null });
    expect(
      usageSnapshotRecordSchema.parse({
        id: 1,
        clerkUserId: 'user_123',
        plan: 'Pro',
        tokensUsed: 1_000,
        tokensLimit: 80_000_000,
        periodEnd: null,
        createdAt: '2026-11-21T10:00:00.000Z',
      }).clerkUserId,
    ).toBe('user_123');
  });

  it('validates discord link and device key requests', () => {
    expect(
      discordLinkRequestSchema.parse({ accessToken: '  oauth-token  ' }).accessToken,
    ).toBe('oauth-token');
    expect(discordLinkRequestSchema.safeParse({}).success).toBe(false);

    expect(
      discordLinkCodeRequestSchema.parse({
        discordUserId: '123456789012345678',
        discordUsername: ' voidcat ',
      }),
    ).toEqual({ discordUserId: '123456789012345678', discordUsername: 'voidcat' });
    expect(
      discordLinkCodeRequestSchema.safeParse({
        discordUserId: 'nope',
        discordUsername: 'voidcat',
      }).success,
    ).toBe(false);
    expect(
      discordLinkCodeRequestSchema.safeParse({
        discordUserId: '123456789012345678',
        discordUsername: 'bad\u0007name',
      }).success,
    ).toBe(false);

    expect(deviceKeyIssueRequestSchema.parse({})).toEqual({});
    expect(deviceKeyIssueRequestSchema.parse({ label: ' Pixel ' })).toEqual({
      label: 'Pixel',
    });
  });

  it('exposes the stable error-code union', () => {
    expect(ERROR_CODES).toContain('invalid_reminder');
    expect(ERROR_CODES).toContain('discord_not_linked');
    expect(errorCodeSchema.parse('too_many_requests')).toBe('too_many_requests');
    expect(errorCodeSchema.safeParse('some_random_error').success).toBe(false);
    expect(
      errorResponseSchema.parse({ error: 'invalid_reminder', detail: 'Bad reminder.' }),
    ).toEqual({ error: 'invalid_reminder', detail: 'Bad reminder.' });
  });
});
