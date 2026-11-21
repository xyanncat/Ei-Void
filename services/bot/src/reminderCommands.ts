import type { ChatInputCommandInteraction } from 'discord.js';
import { formatDueDate } from '@eivoid/discord-core';
import type {
  Account,
  ReminderRecord,
  SnoozeReminderOptions,
} from '@eivoid/db';
import { buildRemindersEmbed } from './format.js';
import { NOT_LINKED_MESSAGE, replyEphemeral } from './replies.js';

/**
 * The slice of `Store` these commands need. Keeping it structural lets tests
 * use a plain fake without a database.
 */
export interface ReminderStore {
  getAccountByDiscordId(discordUserId: string): Promise<Account | null>;
  listUpcomingByDiscordId(
    discordUserId: string,
    limit?: number,
    now?: number | Date,
  ): Promise<ReminderRecord[]>;
  snoozeReminderByDiscordId(
    discordUserId: string,
    localId: number,
    options?: number | SnoozeReminderOptions,
  ): Promise<ReminderRecord | null>;
  markPaidByDiscordId(
    discordUserId: string,
    localId: number,
    now?: number | Date,
  ): Promise<ReminderRecord | null>;
}

const NOT_FOUND_MESSAGE = 'That reminder was not found, or it is already paid.';

/** Resolves the caller's account, replying with the /link hint when absent. */
async function requireAccount(
  interaction: ChatInputCommandInteraction,
  store: ReminderStore,
): Promise<Account | null> {
  const account = await store.getAccountByDiscordId(interaction.user.id);
  if (!account) {
    await replyEphemeral(interaction, { content: NOT_LINKED_MESSAGE });
    return null;
  }
  return account;
}

/** `/reminders` — the five nearest upcoming reminders for this Discord user. */
export async function handleReminders(
  interaction: ChatInputCommandInteraction,
  store: ReminderStore,
  now: number = Date.now(),
): Promise<void> {
  const account = await requireAccount(interaction, store);
  if (!account) return;

  const reminders = await store.listUpcomingByDiscordId(interaction.user.id, 5, now);
  if (reminders.length === 0) {
    await replyEphemeral(interaction, { content: 'You have no upcoming reminders.' });
    return;
  }

  await replyEphemeral(interaction, {
    embeds: [buildRemindersEmbed(reminders, account.timezone)],
  });
}

/** `/snooze local_id [days]` — moves one of the caller's own reminders. */
export async function handleSnooze(
  interaction: ChatInputCommandInteraction,
  store: ReminderStore,
): Promise<void> {
  const account = await requireAccount(interaction, store);
  if (!account) return;

  const localId = interaction.options.getInteger('local_id', true);
  const days = interaction.options.getInteger('days') ?? 1;

  if (!Number.isSafeInteger(localId) || localId < 1) {
    await replyEphemeral(interaction, { content: 'Reminder id must be a positive integer.' });
    return;
  }
  if (!Number.isSafeInteger(days) || days < 1 || days > 30) {
    await replyEphemeral(interaction, { content: 'Days must be between 1 and 30.' });
    return;
  }

  const updated = await store.snoozeReminderByDiscordId(interaction.user.id, localId, days);
  if (!updated) {
    await replyEphemeral(interaction, { content: NOT_FOUND_MESSAGE });
    return;
  }

  const due = formatDueDate(updated.dueAt, account.timezone);
  await replyEphemeral(interaction, {
    content: `Snoozed “${updated.title}” by ${days} day${days === 1 ? '' : 's'}. New due date: ${due}.`,
  });
}

/** `/paid local_id` — marks one of the caller's own reminders as paid. */
export async function handlePaid(
  interaction: ChatInputCommandInteraction,
  store: ReminderStore,
): Promise<void> {
  const account = await requireAccount(interaction, store);
  if (!account) return;

  const localId = interaction.options.getInteger('local_id', true);
  if (!Number.isSafeInteger(localId) || localId < 1) {
    await replyEphemeral(interaction, { content: 'Reminder id must be a positive integer.' });
    return;
  }

  const updated = await store.markPaidByDiscordId(interaction.user.id, localId);
  if (!updated) {
    await replyEphemeral(interaction, { content: NOT_FOUND_MESSAGE });
    return;
  }

  await replyEphemeral(interaction, {
    content: `Marked “${updated.title}” as paid.`,
  });
}
