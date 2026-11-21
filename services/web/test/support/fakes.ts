import { randomBytes } from 'node:crypto';
import {
  generateDeviceKey,
  hashDeviceKey,
  type Account,
  type AddUsageSnapshotInput,
  type CreateLinkCodeInput,
  type DeviceKeyRecord,
  type DiscordIdentity,
  type IssuedDeviceKey,
  type LinkCode,
  type ReminderInput,
  type ReminderSummary,
  type UpsertAccountInput,
  type UsageSnapshotRecord,
} from '@eivoid/db';
import type { Mailer, MailMessage } from '@eivoid/mailer';
import type { Request } from 'express';
import { bearerToken, type Auth, type AuthUser } from '../../src/auth/clerk.js';
import type { WebStore } from '../../src/ports.js';

function cloneAccount(account: Account): Account {
  return { ...account };
}

function cloneDevice(device: DeviceKeyRecord): DeviceKeyRecord {
  return { ...device };
}

/**
 * In-memory WebStore with the same observable rules as @eivoid/db's Store:
 * link codes are single-use and expire, device keys are hashed, reminders are
 * replaced per account, and deletion cascades.
 */
export class FakeStore implements WebStore {
  private readonly accounts = new Map<string, Account>();
  private readonly reminders = new Map<string, ReminderInput[]>();
  private readonly usage = new Map<string, UsageSnapshotRecord[]>();
  private readonly devices: Array<DeviceKeyRecord & { keyHash: string }> = [];
  private readonly linkCodes = new Map<string, LinkCode>();
  private readonly waitlist = new Set<string>();
  private nextDeviceId = 1;
  private nextUsageId = 1;
  readonly hashes = new Map<string, string>();

  async getAccountByClerkId(clerkUserId: string): Promise<Account | null> {
    const account = this.accounts.get(clerkUserId);
    return account ? cloneAccount(account) : null;
  }

  async upsertAccount(input: UpsertAccountInput): Promise<Account> {
    const existing = this.accounts.get(input.clerkUserId);
    const now = new Date();
    const account: Account = {
      clerkUserId: input.clerkUserId,
      email: (input.email ?? '').trim().toLowerCase().slice(0, 254),
      displayName: (input.displayName ?? '').trim().slice(0, 120),
      timezone: (input.timezone ?? '').trim().slice(0, 80) || 'UTC',
      notifyHour: input.notifyHour ?? 9,
      notifyMinute: input.notifyMinute ?? 0,
      emailEnabled: input.emailEnabled ?? true,
      plan: input.plan?.trim() || existing?.plan || 'Free',
      discordUserId: existing?.discordUserId ?? null,
      discordUsername: existing?.discordUsername ?? '',
      createdAt: existing?.createdAt ?? now,
      updatedAt: now,
    };
    this.accounts.set(input.clerkUserId, account);
    return cloneAccount(account);
  }

  async replaceReminders(
    clerkUserId: string,
    reminders: readonly ReminderInput[],
  ): Promise<number> {
    this.reminders.set(
      clerkUserId,
      reminders.map((reminder) => ({ ...reminder })),
    );
    return reminders.length;
  }

  async linkDiscord(clerkUserId: string, discord: DiscordIdentity): Promise<Account> {
    const existing = this.accounts.get(clerkUserId);
    const now = new Date();
    const account: Account = existing
      ? { ...existing, discordUserId: discord.id, discordUsername: discord.username, updatedAt: now }
      : {
          clerkUserId,
          email: '',
          displayName: '',
          timezone: 'UTC',
          notifyHour: 9,
          notifyMinute: 0,
          emailEnabled: true,
          plan: 'Free',
          discordUserId: discord.id,
          discordUsername: discord.username,
          createdAt: now,
          updatedAt: now,
        };
    this.accounts.set(clerkUserId, account);
    return cloneAccount(account);
  }

  async unlinkDiscord(clerkUserId: string): Promise<Account | null> {
    const existing = this.accounts.get(clerkUserId);
    if (!existing) return null;
    const account: Account = {
      ...existing,
      discordUserId: null,
      discordUsername: '',
      updatedAt: new Date(),
    };
    this.accounts.set(clerkUserId, account);
    return cloneAccount(account);
  }

  async deleteAccount(clerkUserId: string): Promise<boolean> {
    const deleted = this.accounts.delete(clerkUserId);
    this.reminders.delete(clerkUserId);
    this.usage.delete(clerkUserId);
    for (let index = this.devices.length - 1; index >= 0; index -= 1) {
      if (this.devices[index]?.clerkUserId === clerkUserId) this.devices.splice(index, 1);
    }
    return deleted;
  }

  async issueDeviceKey(clerkUserId: string, key = generateDeviceKey()): Promise<IssuedDeviceKey> {
    const record: DeviceKeyRecord & { keyHash: string } = {
      id: this.nextDeviceId++,
      clerkUserId,
      keyHash: hashDeviceKey(key),
      createdAt: new Date(),
      lastSeenAt: null,
      revokedAt: null,
    };
    this.devices.push(record);
    this.hashes.set(key, record.keyHash);
    return { ...cloneDevice(record), key };
  }

  async findDeviceKey(keyHash: string): Promise<DeviceKeyRecord | null> {
    const record = this.devices.find(
      (entry) => entry.keyHash === keyHash && entry.revokedAt === null,
    );
    if (!record) return null;
    record.lastSeenAt = new Date();
    return cloneDevice(record);
  }

  async revokeDeviceKeyForUser(id: number, clerkUserId: string): Promise<boolean> {
    const record = this.devices.find(
      (entry) => entry.id === id && entry.clerkUserId === clerkUserId,
    );
    if (!record) return false;
    record.revokedAt = record.revokedAt ?? new Date();
    return true;
  }

  async listDeviceKeys(clerkUserId: string): Promise<DeviceKeyRecord[]> {
    return this.devices
      .filter((entry) => entry.clerkUserId === clerkUserId)
      .sort((a, b) => b.id - a.id)
      .map(cloneDevice);
  }

  async addUsageSnapshot(input: AddUsageSnapshotInput): Promise<UsageSnapshotRecord> {
    const record: UsageSnapshotRecord = {
      id: this.nextUsageId++,
      clerkUserId: input.clerkUserId,
      plan: input.plan,
      tokensUsed: input.tokensUsed,
      tokensLimit: input.tokensLimit,
      periodEnd: input.periodEnd ?? null,
      createdAt: new Date(),
    };
    const list = this.usage.get(input.clerkUserId) ?? [];
    list.push(record);
    this.usage.set(input.clerkUserId, list);
    return { ...record };
  }

  async latestUsage(clerkUserId: string): Promise<UsageSnapshotRecord | null> {
    const list = this.usage.get(clerkUserId) ?? [];
    const latest = list[list.length - 1];
    return latest ? { ...latest } : null;
  }

  async createLinkCode(input: CreateLinkCodeInput): Promise<LinkCode> {
    const code = randomBytes(24).toString('hex');
    const ttl = Number.isFinite(input.ttlMs) ? Number(input.ttlMs) : 10 * 60_000;
    const value: LinkCode = {
      code,
      discordUserId: input.discordUserId,
      discordUsername: input.discordUsername,
      createdAt: new Date(),
      expiresAt: new Date(Date.now() + ttl),
      usedAt: null,
    };
    this.linkCodes.set(code, value);
    return { ...value };
  }

  async getLinkCode(code: string): Promise<LinkCode | null> {
    const value = this.linkCodes.get(code);
    return value ? { ...value } : null;
  }

  async confirmLinkCode(code: string): Promise<LinkCode | null> {
    const value = this.linkCodes.get(code);
    if (!value || value.usedAt !== null || value.expiresAt.getTime() <= Date.now()) {
      return null;
    }
    value.usedAt = new Date();
    return { ...value };
  }

  async addWaitlistEmail(email: string): Promise<boolean> {
    const normalised = email.trim().toLowerCase();
    if (!normalised) return false;
    if (this.waitlist.has(normalised)) return false;
    this.waitlist.add(normalised);
    return true;
  }

  async reminderSummary(clerkUserId: string): Promise<ReminderSummary> {
    const list = this.reminders.get(clerkUserId) ?? [];
    const active = list.filter((entry) => entry.isActive && !entry.isPaid);
    return {
      total: list.length,
      active: active.length,
      paid: list.filter((entry) => entry.isPaid).length,
      nextDueAt:
        active.length > 0 ? Math.min(...active.map((entry) => entry.dueAt)) : null,
    };
  }

  async ping(): Promise<boolean> {
    return true;
  }

  /** Test helper: the stored reminder snapshot for one account. */
  remindersFor(clerkUserId: string): ReminderInput[] {
    return (this.reminders.get(clerkUserId) ?? []).map((entry) => ({ ...entry }));
  }

  waitlistSize(): number {
    return this.waitlist.size;
  }
}

export function createFakeAuth(
  emails: Record<string, string> = {},
): Auth & { calls: number } {
  let calls = 0;
  return {
    get calls() {
      return calls;
    },
    async getUser(req: Request): Promise<AuthUser | null> {
      calls += 1;
      const header = req.get('x-test-user')?.trim();
      const token = bearerToken(req.get('authorization'));
      const userId = header || (token?.startsWith('test:') ? token.slice(5) : '');
      if (!userId) return null;
      return { userId, email: emails[userId] ?? `${userId}@example.com` };
    },
  } as Auth & { calls: number };
}

export interface FakeMailer extends Mailer {
  readonly messages: MailMessage[];
}

export function createFakeMailer(): FakeMailer {
  const messages: MailMessage[] = [];
  return {
    provider: 'smtp',
    messages,
    async send(message: MailMessage) {
      messages.push(message);
      return { ok: true as const, provider: 'smtp' as const };
    },
  };
}
