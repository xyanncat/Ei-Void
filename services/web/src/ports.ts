import type {
  Account,
  AddUsageSnapshotInput,
  CreateLinkCodeInput,
  DeviceKeyRecord,
  DiscordIdentity,
  IssuedDeviceKey,
  LinkCode,
  ReminderInput,
  ReminderSummary,
  UpsertAccountInput,
  UsageSnapshotRecord,
} from '@eivoid/db';

/**
 * The slice of `@eivoid/db`'s `Store` the web service uses. Declaring it
 * structurally lets tests inject a fake without a Postgres pool while the real
 * `Store` remains assignable.
 */
export interface WebStore {
  getAccountByClerkId(clerkUserId: string): Promise<Account | null>;
  upsertAccount(input: UpsertAccountInput): Promise<Account>;
  replaceReminders(clerkUserId: string, reminders: readonly ReminderInput[]): Promise<number>;
  linkDiscord(clerkUserId: string, discord: DiscordIdentity): Promise<Account>;
  unlinkDiscord(clerkUserId: string): Promise<Account | null>;
  deleteAccount(clerkUserId: string): Promise<boolean>;

  issueDeviceKey(clerkUserId: string, key?: string): Promise<IssuedDeviceKey>;
  findDeviceKey(keyHash: string): Promise<DeviceKeyRecord | null>;
  revokeDeviceKeyForUser(id: number, clerkUserId: string): Promise<boolean>;
  listDeviceKeys(clerkUserId: string): Promise<DeviceKeyRecord[]>;

  addUsageSnapshot(input: AddUsageSnapshotInput): Promise<UsageSnapshotRecord>;
  latestUsage(clerkUserId: string): Promise<UsageSnapshotRecord | null>;

  createLinkCode(input: CreateLinkCodeInput): Promise<LinkCode>;
  getLinkCode(code: string): Promise<LinkCode | null>;
  confirmLinkCode(code: string): Promise<LinkCode | null>;

  addWaitlistEmail(email: string): Promise<boolean>;
  reminderSummary(clerkUserId: string): Promise<ReminderSummary>;
  ping(): Promise<boolean>;
}
