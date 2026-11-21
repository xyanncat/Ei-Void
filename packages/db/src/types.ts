export interface Account {
  clerkUserId: string;
  email: string;
  displayName: string;
  timezone: string;
  notifyHour: number;
  notifyMinute: number;
  emailEnabled: boolean;
  plan: string;
  discordUserId: string | null;
  discordUsername: string;
  createdAt: Date;
  updatedAt: Date;
}

export interface UpsertAccountInput {
  clerkUserId: string;
  email?: string;
  displayName?: string;
  timezone?: string;
  notifyHour?: number;
  notifyMinute?: number;
  emailEnabled?: boolean;
  plan?: string;
}

/**
 * Structural equivalent of `ReminderInput` from @eivoid/contracts. Keeping a
 * local shape avoids a runtime dependency between the data and contract
 * packages; callers pass output from `normalizeReminder` directly.
 */
export interface ReminderInput {
  localId: number;
  title: string;
  merchant: string;
  bank: string;
  amountMinor: number;
  currency: string;
  dueAt: number;
  notifyAt: number;
  repeatRule: string;
  category: string;
  notes: string;
  leadDays: number;
  isPaid: boolean;
  isActive: boolean;
  paidAt: number | null;
}

export interface ReminderRecord {
  id: number;
  clerkUserId: string;
  localId: number;
  title: string;
  merchant: string;
  bank: string;
  amountMinor: number;
  currency: string;
  dueAt: number;
  notifyAt: number;
  repeatRule: string;
  category: string;
  notes: string;
  leadDays: number;
  isPaid: boolean;
  isActive: boolean;
  paidAt: number | null;
  discordNotifiedDueAt: number;
  emailNotifiedDueAt: number;
  discordClaimedAt: Date | null;
  emailClaimedAt: Date | null;
  createdAt: Date;
  updatedAt: Date;
}

export interface DueReminder extends ReminderRecord {
  accountEmail: string;
  accountEmailEnabled: boolean;
  accountTimezone: string;
  discordUserId: string | null;
}

export type DeliveryChannel = 'discord' | 'email';

export interface LinkCode {
  code: string;
  discordUserId: string;
  discordUsername: string;
  createdAt: Date;
  expiresAt: Date;
  usedAt: Date | null;
}

export interface CreateLinkCodeInput {
  discordUserId: string;
  discordUsername: string;
  ttlMs?: number;
}

export interface DeviceKeyRecord {
  id: number;
  clerkUserId: string;
  keyHash: string;
  createdAt: Date;
  lastSeenAt: Date | null;
  revokedAt: Date | null;
}

/** Aggregate reminder counts for the account dashboard. */
export interface ReminderSummary {
  total: number;
  active: number;
  paid: number;
  /** Earliest upcoming, unpaid due timestamp in epoch milliseconds, or null. */
  nextDueAt: number | null;
}

export interface IssuedDeviceKey extends DeviceKeyRecord {
  /** The raw key. It is returned once and never stored. */
  key: string;
}

export interface UsageSnapshotRecord {
  id: number;
  clerkUserId: string;
  plan: string;
  tokensUsed: number;
  tokensLimit: number;
  periodEnd: string | null;
  createdAt: Date;
}

export interface AddUsageSnapshotInput {
  clerkUserId: string;
  plan: string;
  tokensUsed: number;
  tokensLimit: number;
  periodEnd?: string | null;
}

export interface SnoozeReminderOptions {
  /** Number of days to shift both timestamps. Defaults to 1. */
  days?: number;
  /** Explicit new due timestamp in epoch milliseconds. */
  dueAt?: number;
  /** Explicit new notify timestamp in epoch milliseconds. */
  notifyAt?: number;
}

export interface DiscordIdentity {
  id: string;
  username: string;
}

export interface Migration {
  readonly version: number;
  readonly name: string;
  readonly sql: string;
}

export interface MigrationResult {
  readonly applied: readonly number[];
  readonly currentVersion: number;
}
