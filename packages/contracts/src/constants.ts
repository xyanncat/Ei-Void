/** Reminder repeat rules, in the same order as the Ei Snore client. */
export const REPEAT_RULES = [
  'NONE',
  'WEEKLY',
  'MONTHLY',
  'QUARTERLY',
  'YEARLY',
] as const;

export type RepeatRule = (typeof REPEAT_RULES)[number];

/** Set form for code ported from the Ei Snore backend. */
export const REPEAT_RULE_SET: ReadonlySet<string> = new Set(REPEAT_RULES);

export const CATEGORIES = [
  'BILL',
  'SUBSCRIPTION',
  'EMI',
  'RENT',
  'INSURANCE',
  'OTHER',
] as const;

export type Category = (typeof CATEGORIES)[number];

export const CATEGORY_SET: ReadonlySet<string> = new Set(CATEGORIES);

export const CURRENCIES = ['INR', 'USD'] as const;

export type Currency = (typeof CURRENCIES)[number];

export const CURRENCY_SET: ReadonlySet<string> = new Set(CURRENCIES);

/** Token allowances for the three plans. Values are monthly token counts. */
export const PLAN_LIMITS = Object.freeze({
  Free: 30_000_000,
  Pro: 80_000_000,
  Max: 200_000_000,
} as const);

export type PlanName = keyof typeof PLAN_LIMITS;

const FREE_PLAN_LIMIT: number = PLAN_LIMITS.Free;

/**
 * Returns the monthly token limit for a plan name. Unknown or missing plans
 * fall back to Free, matching the website's previous behaviour.
 */
export function planLimit(plan: string | null | undefined): number {
  if (!plan) return FREE_PLAN_LIMIT;
  return PLAN_LIMITS[plan as PlanName] ?? FREE_PLAN_LIMIT;
}
