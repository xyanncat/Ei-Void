import { describe, expect, it } from 'vitest';
import {
  buildRemindersEmbed,
  buildUsageEmbed,
  formatAmount,
  usageProgress,
} from '../src/format.js';

describe('usageProgress', () => {
  it('renders a half-full bar', () => {
    expect(usageProgress(50, 100)).toEqual({
      ratio: 0.5,
      percent: 50,
      bar: '█████░░░░░',
    });
  });

  it('clamps usage above the limit', () => {
    expect(usageProgress(120, 100).percent).toBe(100);
  });

  it('handles a zero limit and negative input', () => {
    expect(usageProgress(10, 0)).toEqual({
      ratio: 0,
      percent: 0,
      bar: '░░░░░░░░░░',
    });
    expect(usageProgress(-5, 100).percent).toBe(0);
  });
});

describe('buildUsageEmbed', () => {
  it('contains plan, used/limit and percent', () => {
    const embed = buildUsageEmbed({
      plan: 'Pro',
      tokensUsed: 40_000_000,
      tokensLimit: 80_000_000,
      periodEnd: '2026-12-01',
      synced: true,
    });

    expect(embed.data.title).toBe('Ei Void usage');
    expect(embed.data.description).toContain('50%');
    expect(embed.data.description).not.toContain('No usage');
    expect(embed.data.fields?.map((field) => field.name)).toEqual([
      'Plan',
      'Tokens',
      'Resets',
    ]);
    expect(embed.data.fields?.[0]?.value).toBe('Pro');
    expect(embed.data.fields?.[1]?.value).toBe('4,00,00,000 / 8,00,00,000');
  });

  it('marks an unsynced plan limit as such', () => {
    const embed = buildUsageEmbed({
      plan: 'Free',
      tokensUsed: 0,
      tokensLimit: 30_000_000,
      periodEnd: null,
      synced: false,
    });

    expect(embed.data.description).toContain('No usage has been synced');
    expect(embed.data.fields?.[0]?.value).toBe('Free');
    expect(embed.data.fields?.[1]?.value).toBe('0 / 3,00,00,000');
    expect(embed.data.fields?.map((field) => field.name)).toEqual(['Plan', 'Tokens']);
  });
});

describe('formatAmount', () => {
  it('formats minor units in the reminder currency', () => {
    expect(formatAmount(125_050, 'INR')).toContain('1,250.50');
    expect(formatAmount(125_050, 'USD')).toContain('1,250.50');
  });

  it('falls back for an unknown currency code', () => {
    expect(formatAmount(100, '??')).toContain('1.00');
  });
});

describe('buildRemindersEmbed', () => {
  it('shows title, amount and due date for each reminder', () => {
    const embed = buildRemindersEmbed(
      [
        {
          title: 'Rent *due*',
          amountMinor: 250_000,
          currency: 'INR',
          dueAt: Date.UTC(2026, 11, 1, 9, 0),
        },
        {
          title: 'Internet',
          amountMinor: 99_900,
          currency: 'INR',
          dueAt: Date.UTC(2026, 11, 5, 9, 0),
        },
      ],
      'UTC',
    );

    const description = embed.data.description ?? '';
    expect(description).toContain('Rent \\*due\\*');
    expect(description).toContain('2,500.00');
    expect(description).toContain('999.00');
    expect(description).toContain('Dec 2026');
    expect(description).toContain('Internet');
    expect(embed.data.title).toBe('Upcoming reminders');
    expect(embed.data.footer?.text).toContain('2');
  });
});
