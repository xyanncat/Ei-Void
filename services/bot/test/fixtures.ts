import { vi } from 'vitest';
import type { ChatInputCommandInteraction } from 'discord.js';
import type { Account, DueReminder, ReminderRecord } from '@eivoid/db';

export const CLIENT_ID = '123456789012345678';
export const GUILD_ID = '848841415940898827';
export const DISCORD_USER_ID = '223456789012345678';
export const OTHER_USER_ID = '323456789012345678';
export const FALLBACK_CHANNEL_ID = '423456789012345678';

export function tokenFor(clientId: string = CLIENT_ID): string {
  return `${Buffer.from(clientId).toString('base64url')}.signature.signature`;
}

export const baseEnv: NodeJS.ProcessEnv = {
  NODE_ENV: 'test',
  DISCORD_BOT_TOKEN: tokenFor(),
  DISCORD_GUILD_ID: GUILD_ID,
  DATABASE_URL: 'postgres://user:pass@localhost:5432/eivoid',
};

export interface FakeInteraction {
  readonly interaction: ChatInputCommandInteraction;
  readonly reply: ReturnType<typeof vi.fn>;
  readonly editReply: ReturnType<typeof vi.fn>;
  readonly getInteger: ReturnType<typeof vi.fn>;
}

/** A fully acknowledged interaction, so handlers edit their deferred reply. */
export function makeInteraction(
  options: Record<string, number | null> = {},
  user: { id: string; username: string } = { id: DISCORD_USER_ID, username: 'voidcat' },
): FakeInteraction {
  const reply = vi.fn(async () => undefined);
  const editReply = vi.fn(async () => undefined);
  const getInteger = vi.fn((name: string) => options[name] ?? null);

  const interaction = {
    user,
    guildId: GUILD_ID,
    commandName: 'reminders',
    deferred: true,
    replied: false,
    options: { getInteger },
    reply,
    editReply,
  } as unknown as ChatInputCommandInteraction;

  return { interaction, reply, editReply, getInteger };
}

const CREATED = new Date('2026-01-01T00:00:00.000Z');

export function makeAccount(overrides: Partial<Account> = {}): Account {
  return {
    clerkUserId: 'user_clerk_1',
    email: 'someone@example.com',
    displayName: 'Void Cat',
    timezone: 'UTC',
    notifyHour: 9,
    notifyMinute: 0,
    emailEnabled: true,
    plan: 'Free',
    discordUserId: DISCORD_USER_ID,
    discordUsername: 'voidcat',
    createdAt: CREATED,
    updatedAt: CREATED,
    ...overrides,
  };
}

export function makeReminder(overrides: Partial<ReminderRecord> = {}): ReminderRecord {
  return {
    id: 1,
    clerkUserId: 'user_clerk_1',
    localId: 12,
    title: 'Rent',
    merchant: 'Landlord',
    bank: 'HDFC',
    amountMinor: 250_000,
    currency: 'INR',
    dueAt: Date.UTC(2026, 11, 3, 9, 0),
    notifyAt: Date.UTC(2026, 11, 1, 9, 0),
    repeatRule: 'MONTHLY',
    category: 'RENT',
    notes: 'Pay before noon',
    leadDays: 4,
    isPaid: false,
    isActive: true,
    paidAt: null,
    discordNotifiedDueAt: 0,
    emailNotifiedDueAt: 0,
    discordClaimedAt: null,
    emailClaimedAt: null,
    createdAt: CREATED,
    updatedAt: CREATED,
    ...overrides,
  };
}

export function makeDueReminder(overrides: Partial<DueReminder> = {}): DueReminder {
  return {
    ...makeReminder(),
    accountEmail: 'someone@example.com',
    accountEmailEnabled: true,
    accountTimezone: 'UTC',
    discordUserId: DISCORD_USER_ID,
    ...overrides,
  };
}
