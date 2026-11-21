import { z } from 'zod';
import {
  CATEGORIES,
  CURRENCIES,
  REPEAT_RULES,
  type Category,
  type Currency,
  type RepeatRule,
} from './constants.js';
import { normalizeReminder, type ReminderInput } from './reminders.js';

export const MAX_REMINDERS_PER_SYNC = 500;
export const MAX_REMINDER_TEXT_LENGTH = 1000;

const DISCORD_SNOWFLAKE = /^\d{16,22}$/;
const TIMEZONE_PATTERN = /^[A-Za-z0-9_+\-/]+$/;
const NUMBER_MAX_SAFE = Number.MAX_SAFE_INTEGER;

function toInteger(value: unknown, fallback: number): number {
  try {
    const number = Number(value);
    return Number.isFinite(number) ? Math.trunc(number) : fallback;
  } catch {
    return fallback;
  }
}

function toText(value: unknown, fallback = '', maxLength = 500): string {
  return String(value ?? fallback).trim().slice(0, maxLength);
}

function clampedInteger(min: number, max: number, fallback: number) {
  return z.preprocess((value) => {
    const number = toInteger(value, fallback);
    return Math.min(max, Math.max(min, number));
  }, z.number().int());
}

/**
 * Raw reminder payload. Every field is normalized by `normalizeReminder`, so
 * the schema accepts the loosely typed values the Android client sends and
 * returns the same canonical shape the backend stores.
 */
const rawReminderSchema = z.object({
  localId: z.unknown().optional(),
  title: z.unknown().optional(),
  merchant: z.unknown().optional(),
  bank: z.unknown().optional(),
  amountMinor: z.unknown().optional(),
  currency: z.unknown().optional(),
  dueAt: z.unknown().optional(),
  notifyAt: z.unknown().optional(),
  repeatRule: z.unknown().optional(),
  category: z.unknown().optional(),
  notes: z.unknown().optional(),
  leadDays: z.unknown().optional(),
  isPaid: z.unknown().optional(),
  isActive: z.unknown().optional(),
  paidAt: z.unknown().optional(),
});

export const reminderInputSchema = rawReminderSchema.transform((input, ctx) => {
  const normalized = normalizeReminder(input);
  if (normalized === null) {
    ctx.addIssue({
      code: z.ZodIssueCode.custom,
      message: 'invalid_reminder',
    });
    return z.NEVER;
  }
  return normalized;
});

export const reminderRepeatRuleSchema = z.enum(REPEAT_RULES);
export const reminderCategorySchema = z.enum(CATEGORIES);
export const reminderCurrencySchema = z.enum(CURRENCIES);

const timezoneSchema = z.preprocess(
  (value) => toText(value, 'UTC', 80) || 'UTC',
  z.string().regex(TIMEZONE_PATTERN, 'invalid_timezone'),
);

const emailSchema = z.preprocess(
  (value) => toText(value, '', 254).toLowerCase(),
  z.string().max(254),
);

/** The profile fields sent alongside a reminder sync. */
export const reminderProfileSchema = z.object({
  timezone: timezoneSchema,
  notifyHour: clampedInteger(0, 23, 9),
  notifyMinute: clampedInteger(0, 59, 0),
  name: z.preprocess((value) => toText(value, '', 120), z.string().max(120)),
  email: emailSchema,
  emailEnabled: z.preprocess((value) => Boolean(value), z.boolean()),
});

export type ReminderProfile = z.infer<typeof reminderProfileSchema>;

const DEFAULT_PROFILE: ReminderProfile = Object.freeze(reminderProfileSchema.parse({}));

/** POST /reminders/sync body, in the same shape the Android app sends. */
export const reminderSyncRequestSchema = z
  .object({
    profile: reminderProfileSchema.optional(),
    reminders: z
      .array(reminderInputSchema)
      .max(MAX_REMINDERS_PER_SYNC)
      .default([]),
  })
  .transform((value) => ({
    profile: value.profile ? { ...value.profile } : { ...DEFAULT_PROFILE },
    reminders: value.reminders,
  }));

export type ReminderSyncRequest = z.infer<typeof reminderSyncRequestSchema>;
export type ReminderSyncRequestInput = z.input<typeof reminderSyncRequestSchema>;

export const accountDiscordSchema = z.object({
  id: z.string().regex(DISCORD_SNOWFLAKE, 'invalid_discord_id'),
  username: z.string().trim().min(1).max(64),
});

export type AccountDiscord = z.infer<typeof accountDiscordSchema>;

/** GET /account response. */
export const accountResponseSchema = z.object({
  email: z.string().max(254),
  displayName: z.string().max(120),
  discord: accountDiscordSchema.nullable(),
});

export type AccountResponse = z.infer<typeof accountResponseSchema>;

const tokenCountSchema = z.number().int().nonnegative().max(NUMBER_MAX_SAFE);

/** Body accepted when a device uploads its usage counters. */
export const usageSnapshotInputSchema = z.object({
  plan: z.string().trim().min(1).max(64),
  tokensUsed: tokenCountSchema,
  tokensLimit: tokenCountSchema,
  periodEnd: z.string().trim().max(64).nullable().optional(),
});

export type UsageSnapshotInput = z.infer<typeof usageSnapshotInputSchema>;

/** A stored usage snapshot, as returned by the API. */
export const usageSnapshotSchema = z.object({
  id: z.number().int().positive(),
  clerkUserId: z.string().trim().min(1).max(128),
  plan: z.string().trim().min(1).max(64),
  tokensUsed: tokenCountSchema,
  tokensLimit: tokenCountSchema,
  periodEnd: z.string().max(64).nullable(),
  createdAt: z.string().datetime({ offset: true }),
});

export type UsageSnapshot = z.infer<typeof usageSnapshotSchema>;

/** Body for linking a browser account to Discord with an OAuth access token. */
export const discordLinkRequestSchema = z.object({
  accessToken: z.string().trim().min(1).max(4096),
});

export type DiscordLinkRequest = z.infer<typeof discordLinkRequestSchema>;

/**
 * Body for issuing a device key. The authenticated account is implied by the
 * request session; `label` is an optional human-readable note for the device.
 */
export const deviceKeyIssueRequestSchema = z.object({
  label: z.string().trim().min(1).max(64).optional(),
});

export type DeviceKeyIssueRequest = z.infer<typeof deviceKeyIssueRequestSchema>;

/** Normalized reminder fields plus the enums used by the schemas above. */
export type NormalizedReminder = ReminderInput;
export type ReminderRepeatRule = RepeatRule;
export type ReminderCategory = Category;
export type ReminderCurrency = Currency;
