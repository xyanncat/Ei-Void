import {
  CATEGORY_SET,
  CURRENCY_SET,
  REPEAT_RULE_SET,
  type Category,
  type Currency,
  type RepeatRule,
} from './constants.js';

/** The normalized reminder shape accepted by the sync endpoint. */
export interface ReminderInput {
  localId: number;
  title: string;
  merchant: string;
  bank: string;
  amountMinor: number;
  currency: Currency;
  dueAt: number;
  notifyAt: number;
  repeatRule: RepeatRule;
  category: Category;
  notes: string;
  leadDays: number;
  isPaid: boolean;
  isActive: boolean;
  paidAt: number | null;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function toInteger(value: unknown, fallback = 0): number {
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

function isRepeatRule(value: string): value is RepeatRule {
  return REPEAT_RULE_SET.has(value);
}

function isCategory(value: string): value is Category {
  return CATEGORY_SET.has(value);
}

function isCurrency(value: string): value is Currency {
  return CURRENCY_SET.has(value);
}

/**
 * Normalizes one reminder exactly like the Ei Snore backend: numbers are
 * truncated, text is trimmed and truncated, enum-like fields are uppercased,
 * and local/due/notify timestamps must be positive. Returns null when the
 * reminder is invalid.
 */
export function normalizeReminder(input: unknown): ReminderInput | null {
  if (!isRecord(input)) return null;

  const localId = toInteger(input.localId, -1);
  const repeatRule = toText(input.repeatRule, 'NONE', 20).toUpperCase();
  const category = toText(input.category, 'OTHER', 20).toUpperCase();
  const currency = toText(input.currency, 'INR', 3).toUpperCase();
  const dueAt = toInteger(input.dueAt, 0);
  const notifyAt = toInteger(input.notifyAt, 0);
  const leadDays = toInteger(input.leadDays, 4);

  if (localId <= 0 || dueAt <= 0 || notifyAt <= 0) return null;
  if (!isRepeatRule(repeatRule) || !isCategory(category) || !isCurrency(currency)) {
    return null;
  }

  return {
    localId,
    title: toText(input.title, 'Payment reminder', 180),
    merchant: toText(input.merchant, '', 180),
    bank: toText(input.bank, '', 180),
    amountMinor: Math.max(0, toInteger(input.amountMinor, 0)),
    currency,
    dueAt,
    notifyAt,
    repeatRule,
    category,
    notes: toText(input.notes, '', 1000),
    leadDays: Math.max(0, Math.min(30, leadDays)),
    isPaid: Boolean(input.isPaid),
    isActive: Boolean(input.isActive),
    paidAt: input.paidAt == null ? null : toInteger(input.paidAt, 0),
  };
}
