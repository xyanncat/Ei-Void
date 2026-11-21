import { afterEach, describe, expect, it, vi } from 'vitest';
import type { DueReminder } from '@eivoid/db';
import type {
  DiscordClient,
  DiscordReminderResult,
  SendDiscordReminderOptions,
} from '@eivoid/discord-core';
import type { MailMessage, MailResult } from '@eivoid/mailer';
import {
  createScheduler,
  runSchedulerOnce,
  SCHEDULER_BATCH_SIZE,
  SCHEDULER_INTERVAL_MS,
  type SchedulerDependencies,
  type SchedulerStore,
} from '../src/scheduler.js';
import {
  DISCORD_USER_ID,
  FALLBACK_CHANNEL_ID,
  GUILD_ID,
  makeDueReminder,
} from './fixtures.js';

const NOW = 1_700_000_000_000;

interface HarnessOptions {
  rows?: DueReminder[];
  discordResult?: DiscordReminderResult;
  mailResult?: MailResult;
}

function makeHarness(options: HarnessOptions = {}) {
  const dueReminders = vi.fn(async () => options.rows ?? []);
  const claimReminderDelivery = vi.fn(async () => true);
  const markDiscordNotified = vi.fn(async () => true);
  const markEmailNotified = vi.fn(async () => true);
  const releaseReminderClaim = vi.fn(async () => true);

  const store: SchedulerStore = {
    dueReminders,
    claimReminderDelivery,
    markDiscordNotified,
    markEmailNotified,
    releaseReminderClaim,
  };

  const send = vi.fn(
    async (_message: MailMessage): Promise<MailResult> =>
      options.mailResult ?? { ok: true, provider: 'gmail' },
  );
  const sendDiscord = vi.fn(
    async (
      _client: DiscordClient,
      _options: SendDiscordReminderOptions,
    ): Promise<DiscordReminderResult> => options.discordResult ?? { kind: 'dm' },
  );
  const log = vi.fn();

  const deps: SchedulerDependencies = {
    store,
    client: {} as DiscordClient,
    mailer: { send },
    config: { guildId: GUILD_ID, fallbackChannelId: FALLBACK_CHANNEL_ID },
    now: () => NOW,
    log,
    sendDiscord,
  };

  return {
    deps,
    send,
    sendDiscord,
    log,
    mocks: {
      dueReminders,
      claimReminderDelivery,
      markDiscordNotified,
      markEmailNotified,
      releaseReminderClaim,
    },
  };
}

afterEach(() => {
  vi.useRealTimers();
});

describe('runSchedulerOnce', () => {
  it('delivers Discord and email and marks both channels notified', async () => {
    const reminder = makeDueReminder();
    const harness = makeHarness({ rows: [reminder] });

    await runSchedulerOnce(harness.deps);

    expect(harness.mocks.dueReminders).toHaveBeenCalledWith(NOW, SCHEDULER_BATCH_SIZE);
    expect(harness.mocks.claimReminderDelivery).toHaveBeenCalledWith(reminder.id, 'discord');
    expect(harness.mocks.claimReminderDelivery).toHaveBeenCalledWith(reminder.id, 'email');

    const discordOptions = harness.sendDiscord.mock.calls[0]?.[1];
    expect(discordOptions?.userId).toBe(DISCORD_USER_ID);
    expect(discordOptions?.title).toContain('Rent');
    expect(discordOptions?.title).toContain('2,500.00');
    expect(discordOptions?.body).toContain('Merchant: Landlord');
    expect(discordOptions?.body).toContain('Due:');
    expect(discordOptions?.guildId).toBe(GUILD_ID);
    expect(discordOptions?.fallbackChannelId).toBe(FALLBACK_CHANNEL_ID);
    expect(harness.mocks.markDiscordNotified).toHaveBeenCalledWith(reminder.id);

    expect(harness.send).toHaveBeenCalledWith({
      to: reminder.accountEmail,
      subject: expect.stringContaining('Rent'),
      text: expect.stringContaining('Merchant: Landlord'),
    });
    expect(harness.mocks.markEmailNotified).toHaveBeenCalledWith(reminder.id);
  });

  it('treats a fallback channel delivery as delivered', async () => {
    const harness = makeHarness({
      rows: [makeDueReminder({ accountEmailEnabled: false })],
      discordResult: { kind: 'fallback', channelId: FALLBACK_CHANNEL_ID },
    });

    await runSchedulerOnce(harness.deps);

    expect(harness.mocks.markDiscordNotified).toHaveBeenCalledWith(1);
    expect(harness.mocks.releaseReminderClaim).not.toHaveBeenCalled();
  });

  it('skips channels whose due marker already matches (restart safety)', async () => {
    const base = makeDueReminder();
    const reminder = makeDueReminder({
      discordNotifiedDueAt: base.dueAt,
      emailNotifiedDueAt: base.dueAt,
    });
    const harness = makeHarness({ rows: [reminder] });

    await runSchedulerOnce(harness.deps);

    expect(harness.mocks.claimReminderDelivery).not.toHaveBeenCalled();
    expect(harness.sendDiscord).not.toHaveBeenCalled();
    expect(harness.send).not.toHaveBeenCalled();
  });

  it('delivers only the channel that still needs it after a restart', async () => {
    const base = makeDueReminder();
    const reminder = makeDueReminder({
      discordNotifiedDueAt: base.dueAt,
      emailNotifiedDueAt: 0,
    });
    const harness = makeHarness({ rows: [reminder] });

    await runSchedulerOnce(harness.deps);

    expect(harness.sendDiscord).not.toHaveBeenCalled();
    expect(harness.mocks.claimReminderDelivery).toHaveBeenCalledWith(reminder.id, 'email');
    expect(harness.send).toHaveBeenCalledTimes(1);
  });

  it('does not send when another worker holds the claim', async () => {
    const harness = makeHarness({ rows: [makeDueReminder()] });
    harness.mocks.claimReminderDelivery.mockResolvedValue(false);

    await runSchedulerOnce(harness.deps);

    expect(harness.sendDiscord).not.toHaveBeenCalled();
    expect(harness.send).not.toHaveBeenCalled();
    expect(harness.mocks.markDiscordNotified).not.toHaveBeenCalled();
    expect(harness.mocks.markEmailNotified).not.toHaveBeenCalled();
  });

  it('does not let one failing row block the next', async () => {
    const first = makeDueReminder({ id: 1, localId: 1, accountEmailEnabled: false });
    const second = makeDueReminder({
      id: 2,
      localId: 2,
      title: 'Internet',
      accountEmailEnabled: false,
    });
    const harness = makeHarness({ rows: [first, second] });
    harness.sendDiscord.mockRejectedValueOnce(new Error('discord down'));

    await runSchedulerOnce(harness.deps);

    expect(harness.mocks.releaseReminderClaim).toHaveBeenCalledWith(first.id, 'discord');
    expect(harness.mocks.markDiscordNotified).toHaveBeenCalledWith(second.id);
    expect(harness.log).toHaveBeenCalledWith('discord_delivery_failed');
  });

  it('releases the claim when Discord returns a classified error', async () => {
    const harness = makeHarness({
      rows: [makeDueReminder({ accountEmailEnabled: false })],
      discordResult: {
        kind: 'error',
        status: 403,
        code: 'discord_dm_blocked',
        fallbackAttempted: true,
      },
    });

    await runSchedulerOnce(harness.deps);

    expect(harness.mocks.releaseReminderClaim).toHaveBeenCalledWith(1, 'discord');
    expect(harness.mocks.markDiscordNotified).not.toHaveBeenCalled();
    expect(harness.log).toHaveBeenCalledWith('discord_delivery_failed');
  });

  it('releases a transient email failure for a later retry', async () => {
    const harness = makeHarness({
      rows: [makeDueReminder({ discordUserId: null })],
      mailResult: { ok: false, status: 502, error: 'email_send_failed' },
    });

    await runSchedulerOnce(harness.deps);

    expect(harness.mocks.releaseReminderClaim).toHaveBeenCalledWith(1, 'email');
    expect(harness.mocks.markEmailNotified).not.toHaveBeenCalled();
    expect(harness.log).toHaveBeenCalledWith('email_delivery_failed');
  });

  it.each(['invalid_email', 'email_not_configured'] as const)(
    'marks a permanent email failure (%s) terminal',
    async (error) => {
      const harness = makeHarness({
        rows: [makeDueReminder({ discordUserId: null })],
        mailResult: {
          ok: false,
          status: error === 'invalid_email' ? 400 : 503,
          error,
        },
      });

      await runSchedulerOnce(harness.deps);

      expect(harness.mocks.markEmailNotified).toHaveBeenCalledWith(1);
      expect(harness.mocks.releaseReminderClaim).not.toHaveBeenCalled();
      expect(harness.log).toHaveBeenCalledWith('email_delivery_terminal');
    },
  );

  it('does not throw when the due query fails', async () => {
    const harness = makeHarness();
    harness.mocks.dueReminders.mockRejectedValueOnce(new Error('database down'));

    await expect(runSchedulerOnce(harness.deps)).resolves.toBeUndefined();
    expect(harness.log).toHaveBeenCalledWith('scheduler_query_failed');
  });
});

describe('createScheduler', () => {
  it('ticks every 60 seconds and stops cleanly', async () => {
    vi.useFakeTimers();
    const harness = makeHarness();
    const scheduler = createScheduler(harness.deps);

    expect(scheduler.isRunning()).toBe(false);
    scheduler.start();
    expect(scheduler.isRunning()).toBe(true);

    await vi.advanceTimersByTimeAsync(1);
    const initialCalls = harness.mocks.dueReminders.mock.calls.length;
    expect(initialCalls).toBeGreaterThan(0);

    await vi.advanceTimersByTimeAsync(SCHEDULER_INTERVAL_MS);
    expect(harness.mocks.dueReminders.mock.calls.length).toBeGreaterThan(initialCalls);

    scheduler.stop();
    expect(scheduler.isRunning()).toBe(false);

    const afterStop = harness.mocks.dueReminders.mock.calls.length;
    await vi.advanceTimersByTimeAsync(SCHEDULER_INTERVAL_MS * 2);
    expect(harness.mocks.dueReminders.mock.calls.length).toBe(afterStop);
  });
});
