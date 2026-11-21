import { EmbedBuilder } from 'discord.js';
import { formatDueDate } from '@eivoid/discord-core';

export interface UsageProgress {
  ratio: number;
  percent: number;
  bar: string;
}

/** Progress bar used by the usage embed. Ratios are clamped to 0..1. */
export function usageProgress(tokensUsed: number, tokensLimit: number): UsageProgress {
  const used = Number.isFinite(tokensUsed) ? Math.max(0, tokensUsed) : 0;
  const limit = Number.isFinite(tokensLimit) ? Math.max(0, tokensLimit) : 0;
  const ratio = limit === 0 ? 0 : Math.min(1, used / limit);
  const percent = Math.round(ratio * 100);
  const filled = Math.round(ratio * 10);
  return {
    ratio,
    percent,
    bar: '█'.repeat(filled) + '░'.repeat(10 - filled),
  };
}

const numbers = new Intl.NumberFormat('en-IN');

export interface UsageEmbedInput {
  readonly plan: string;
  readonly tokensUsed: number;
  readonly tokensLimit: number;
  readonly periodEnd?: string | null;
  /** False when the app has never uploaded a usage snapshot. */
  readonly synced: boolean;
}

/** Builds the /usage embed: plan, used/limit and a percentage bar. */
export function buildUsageEmbed(usage: UsageEmbedInput): EmbedBuilder {
  const plan = usage.plan.trim() || 'Free';
  const progress = usageProgress(usage.tokensUsed, usage.tokensLimit);
  const note = usage.synced ? '' : '\nNo usage has been synced from the app yet.';

  const embed = new EmbedBuilder()
    .setTitle('Ei Void usage')
    .setDescription(`${progress.bar} **${progress.percent}%**${note}`)
    .setColor(0xb8b8b4)
    .addFields(
      { name: 'Plan', value: plan, inline: true },
      {
        name: 'Tokens',
        value: `${numbers.format(Math.max(0, usage.tokensUsed))} / ${numbers.format(
          Math.max(0, usage.tokensLimit),
        )}`,
        inline: true,
      },
    );

  if (usage.periodEnd) {
    embed.addFields({ name: 'Resets', value: usage.periodEnd, inline: false });
  }
  return embed;
}

/** Formats an amount stored in minor units, e.g. 125050 INR -> ₹1,250.50. */
export function formatAmount(amountMinor: number, currency: string): string {
  const minor = Number.isFinite(amountMinor) ? Math.trunc(amountMinor) : 0;
  const code = /^[A-Za-z]{3}$/.test(currency) ? currency.toUpperCase() : 'INR';
  try {
    return new Intl.NumberFormat('en-IN', {
      style: 'currency',
      currency: code,
      maximumFractionDigits: 2,
    }).format(minor / 100);
  } catch {
    return `${code} ${(minor / 100).toFixed(2)}`;
  }
}

function escapeMarkdown(value: string): string {
  return value.replace(/([\\*_~`>|])/g, '\\$1');
}

export interface ReminderSummary {
  readonly title: string;
  readonly amountMinor: number;
  readonly currency: string;
  readonly dueAt: number;
}

/**
 * Lists a user's upcoming reminders with title, amount and due date. The
 * caller only ever supplies rows already scoped to that user.
 */
export function buildRemindersEmbed(
  reminders: readonly ReminderSummary[],
  timezone?: string | null,
): EmbedBuilder {
  const lines = reminders.map((reminder, index) => {
    const amount = formatAmount(reminder.amountMinor, reminder.currency);
    const due = formatDueDate(reminder.dueAt, timezone);
    return `**${index + 1}. ${escapeMarkdown(reminder.title || 'Reminder')}**\n${amount} · Due ${due}`;
  });

  return new EmbedBuilder()
    .setTitle('Upcoming reminders')
    .setDescription(lines.join('\n\n') || 'No upcoming reminders.')
    .setColor(0xb8b8b4)
    .setFooter({ text: `Showing ${reminders.length} of your next reminders` });
}
