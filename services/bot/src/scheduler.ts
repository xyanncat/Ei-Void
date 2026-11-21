import {
  buildReminderMessage,
  sendDiscordReminder,
  type DiscordClient,
  type DiscordReminderResult,
} from '@eivoid/discord-core';
import type { DeliveryChannel, DueReminder } from '@eivoid/db';
import type { MailResult, Mailer } from '@eivoid/mailer';
import { formatAmount } from './format.js';

export const SCHEDULER_INTERVAL_MS = 60_000;
export const SCHEDULER_BATCH_SIZE = 25;

/** The slice of `Store` the scheduler needs. Structural, so tests use a fake. */
export interface SchedulerStore {
  dueReminders(now?: number | Date, limit?: number): Promise<DueReminder[]>;
  claimReminderDelivery(
    id: number,
    channel: DeliveryChannel,
    now?: number | Date,
  ): Promise<boolean>;
  markDiscordNotified(id: number): Promise<boolean>;
  markEmailNotified(id: number): Promise<boolean>;
  releaseReminderClaim(id: number, channel: DeliveryChannel): Promise<boolean>;
}

export interface SchedulerConfig {
  readonly guildId: string;
  /** Empty when no fallback channel is configured. */
  readonly fallbackChannelId: string;
}

export interface SchedulerDependencies {
  readonly store: SchedulerStore;
  readonly client: DiscordClient;
  readonly mailer: Pick<Mailer, 'send'>;
  readonly config: SchedulerConfig;
  /** Injectable clock, primarily for tests. */
  readonly now?: () => number;
  /** Receives short, generic codes — never message content or addresses. */
  readonly log?: (code: string) => void;
  /** Injectable sender, primarily for tests. */
  readonly sendDiscord?: typeof sendDiscordReminder;
}

export interface ReminderScheduler {
  start(): void;
  stop(): void;
  tick(): Promise<void>;
  isRunning(): boolean;
  isProcessing(): boolean;
}

function defaultLog(code: string): void {
  console.warn(`[scheduler] ${code}`);
}

function reminderTitle(reminder: DueReminder): string {
  const amount =
    reminder.amountMinor > 0
      ? `${formatAmount(reminder.amountMinor, reminder.currency)} · `
      : '';
  return `${amount}${reminder.title}`.slice(0, 180);
}

function needsDiscord(reminder: DueReminder): boolean {
  return Boolean(reminder.discordUserId) && reminder.discordNotifiedDueAt !== reminder.dueAt;
}

function needsEmail(reminder: DueReminder): boolean {
  return (
    reminder.accountEmailEnabled &&
    reminder.accountEmail.trim().length > 0 &&
    reminder.emailNotifiedDueAt !== reminder.dueAt
  );
}

async function releaseClaim(
  store: SchedulerStore,
  id: number,
  channel: DeliveryChannel,
  log: (code: string) => void,
): Promise<void> {
  try {
    await store.releaseReminderClaim(id, channel);
  } catch {
    log('claim_release_failed');
  }
}

async function deliverDiscord(
  reminder: DueReminder,
  title: string,
  body: string,
  dependencies: SchedulerDependencies,
  log: (code: string) => void,
  sendDiscord: typeof sendDiscordReminder,
): Promise<void> {
  const userId = reminder.discordUserId;
  if (!userId) return;

  const claimed = await dependencies.store.claimReminderDelivery(reminder.id, 'discord');
  if (!claimed) return;

  let result: DiscordReminderResult;
  try {
    result = await sendDiscord(dependencies.client, {
      userId,
      title,
      body,
      ...(dependencies.config.fallbackChannelId
        ? { fallbackChannelId: dependencies.config.fallbackChannelId }
        : {}),
      guildId: dependencies.config.guildId,
    });
  } catch {
    await releaseClaim(dependencies.store, reminder.id, 'discord', log);
    log('discord_delivery_failed');
    return;
  }

  if (result.kind === 'dm' || result.kind === 'fallback') {
    await dependencies.store.markDiscordNotified(reminder.id);
    log(result.kind === 'dm' ? 'discord_dm_delivered' : 'discord_fallback_delivered');
    return;
  }

  // DM blocked, rate limited, membership gone, or Discord unreachable: the
  // claim is released so a later tick can retry without double-sending.
  await releaseClaim(dependencies.store, reminder.id, 'discord', log);
  log('discord_delivery_failed');
}

async function deliverEmail(
  reminder: DueReminder,
  title: string,
  body: string,
  dependencies: SchedulerDependencies,
  log: (code: string) => void,
): Promise<void> {
  const address = reminder.accountEmail.trim();
  if (!reminder.accountEmailEnabled || !address) return;

  const claimed = await dependencies.store.claimReminderDelivery(reminder.id, 'email');
  if (!claimed) return;

  let result: MailResult;
  try {
    result = await dependencies.mailer.send({
      to: address,
      subject: title,
      text: `${title}\n\n${body}`,
    });
  } catch {
    await releaseClaim(dependencies.store, reminder.id, 'email', log);
    log('email_delivery_failed');
    return;
  }

  if (result.ok) {
    await dependencies.store.markEmailNotified(reminder.id);
    log('email_delivered');
    return;
  }

  // Retrying an invalid address or a missing transport can never succeed. Mark
  // it terminal so it cannot occupy the batch forever and starve new rows.
  if (result.error === 'invalid_email' || result.error === 'email_not_configured') {
    await dependencies.store.markEmailNotified(reminder.id);
    log('email_delivery_terminal');
    return;
  }

  await releaseClaim(dependencies.store, reminder.id, 'email', log);
  log('email_delivery_failed');
}

async function processReminder(
  reminder: DueReminder,
  dependencies: SchedulerDependencies,
  log: (code: string) => void,
  sendDiscord: typeof sendDiscordReminder,
): Promise<void> {
  const dueAt = Number(reminder.dueAt);
  const message = buildReminderMessage({
    title: reminderTitle(reminder),
    dueAt,
    timezone: reminder.accountTimezone,
    merchant: reminder.merchant,
    bank: reminder.bank,
    notes: reminder.notes,
  });

  // Each channel is isolated: a Discord failure must not skip the email, and
  // vice versa.
  if (needsDiscord(reminder)) {
    try {
      await deliverDiscord(reminder, message.title, message.body, dependencies, log, sendDiscord);
    } catch {
      await releaseClaim(dependencies.store, reminder.id, 'discord', log);
      log('discord_delivery_failed');
    }
  }

  if (needsEmail(reminder)) {
    try {
      await deliverEmail(reminder, message.title, message.body, dependencies, log);
    } catch {
      await releaseClaim(dependencies.store, reminder.id, 'email', log);
      log('email_delivery_failed');
    }
  }
}

/**
 * One scheduler pass: query the due batch, then process each row in isolation.
 * A row that throws is logged generically and the loop continues.
 */
export async function runSchedulerOnce(dependencies: SchedulerDependencies): Promise<void> {
  const log = dependencies.log ?? defaultLog;
  const now = dependencies.now ?? Date.now;
  const sendDiscord = dependencies.sendDiscord ?? sendDiscordReminder;

  let reminders: DueReminder[];
  try {
    reminders = await dependencies.store.dueReminders(now(), SCHEDULER_BATCH_SIZE);
  } catch {
    log('scheduler_query_failed');
    return;
  }

  for (const reminder of reminders) {
    try {
      await processReminder(reminder, dependencies, log, sendDiscord);
    } catch {
      log('reminder_failed');
    }
  }
}

/**
 * Creates the 60-second scheduler. `start` also ticks immediately so a worker
 * restart catches up on reminders that came due while it was offline; the
 * database markers make every tick idempotent.
 */
export function createScheduler(dependencies: SchedulerDependencies): ReminderScheduler {
  const log = dependencies.log ?? defaultLog;
  let timer: NodeJS.Timeout | null = null;
  let processing = false;

  const tick = async (): Promise<void> => {
    if (processing) return;
    processing = true;
    try {
      await runSchedulerOnce(dependencies);
    } catch {
      log('scheduler_failed');
    } finally {
      processing = false;
    }
  };

  return {
    start(): void {
      if (timer) return;
      timer = setInterval(() => {
        void tick();
      }, SCHEDULER_INTERVAL_MS);
      void tick();
    },
    stop(): void {
      if (!timer) return;
      clearInterval(timer);
      timer = null;
    },
    tick,
    isRunning(): boolean {
      return timer !== null;
    },
    isProcessing(): boolean {
      return processing;
    },
  };
}
