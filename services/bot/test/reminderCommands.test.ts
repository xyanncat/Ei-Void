import { describe, expect, it, vi } from 'vitest';
import type { EmbedBuilder } from 'discord.js';
import type { ReminderStore } from '../src/reminderCommands.js';
import { handlePaid, handleReminders, handleSnooze } from '../src/reminderCommands.js';
import {
  DISCORD_USER_ID,
  makeAccount,
  makeInteraction,
  makeReminder,
  OTHER_USER_ID,
} from './fixtures.js';

function makeStore(overrides: Partial<ReminderStore> = {}): ReminderStore {
  return {
    getAccountByDiscordId: vi.fn(async () => makeAccount()),
    listUpcomingByDiscordId: vi.fn(async () => []),
    snoozeReminderByDiscordId: vi.fn(async () => null),
    markPaidByDiscordId: vi.fn(async () => null),
    ...overrides,
  };
}

function contentOf(editReply: ReturnType<typeof vi.fn>): string {
  return String((editReply.mock.calls[0]?.[0] as { content?: string }).content ?? '');
}

function embedOf(editReply: ReturnType<typeof vi.fn>): EmbedBuilder {
  const payload = editReply.mock.calls[0]?.[0] as { embeds?: EmbedBuilder[] };
  const embed = payload.embeds?.[0];
  expect(embed).toBeDefined();
  return embed!;
}

describe('handleReminders', () => {
  it('tells an unlinked caller to run /link and never queries their data', async () => {
    const listUpcomingByDiscordId = vi.fn(async () => []);
    const store = makeStore({
      getAccountByDiscordId: vi.fn(async () => null),
      listUpcomingByDiscordId,
    });
    const { interaction, editReply } = makeInteraction();

    await handleReminders(interaction, store);

    expect(listUpcomingByDiscordId).not.toHaveBeenCalled();
    expect(contentOf(editReply)).toContain('/link');
  });

  it('lists only the caller\'s next five reminders', async () => {
    const rows = [
      makeReminder(),
      makeReminder({
        id: 2,
        localId: 13,
        title: 'Internet',
        amountMinor: 99_900,
        dueAt: Date.UTC(2026, 11, 5, 9, 0),
      }),
    ];
    const listUpcomingByDiscordId = vi.fn(async () => rows);
    const store = makeStore({ listUpcomingByDiscordId });
    const { interaction, editReply } = makeInteraction();

    await handleReminders(interaction, store, 1_700_000_000_000);

    expect(listUpcomingByDiscordId).toHaveBeenCalledWith(DISCORD_USER_ID, 5, 1_700_000_000_000);
    const description = embedOf(editReply).data.description ?? '';
    expect(description).toContain('Rent');
    expect(description).toContain('2,500.00');
    expect(description).toContain('Internet');
    expect(description).toContain('Dec 2026');
  });

  it('reports an empty list without an embed', async () => {
    const { interaction, editReply } = makeInteraction();
    await handleReminders(interaction, makeStore());
    expect(contentOf(editReply)).toContain('no upcoming reminders');
  });
});

describe('handleSnooze', () => {
  it('snoozes the caller\'s own reminder for the requested days', async () => {
    const updated = makeReminder({ dueAt: Date.UTC(2026, 11, 6, 9, 0) });
    const snoozeReminderByDiscordId = vi.fn(async () => updated);
    const store = makeStore({ snoozeReminderByDiscordId });
    const { interaction, editReply } = makeInteraction({ local_id: 12, days: 3 });

    await handleSnooze(interaction, store);

    expect(snoozeReminderByDiscordId).toHaveBeenCalledWith(DISCORD_USER_ID, 12, 3);
    const content = contentOf(editReply);
    expect(content).toContain('Snoozed');
    expect(content).toContain('Rent');
    expect(content).toContain('3 days');
    expect(content).toContain('Dec 2026');
  });

  it('defaults to one day', async () => {
    const snoozeReminderByDiscordId = vi.fn(async () => makeReminder());
    const { interaction, editReply } = makeInteraction({ local_id: 12 });

    await handleSnooze(interaction, makeStore({ snoozeReminderByDiscordId }));

    expect(snoozeReminderByDiscordId).toHaveBeenCalledWith(DISCORD_USER_ID, 12, 1);
    expect(contentOf(editReply)).toContain('1 day');
  });

  it('reports not-found generically and never names another row', async () => {
    const snoozeReminderByDiscordId = vi.fn(async () => null);
    const { interaction, editReply } = makeInteraction({ local_id: 99 });

    await handleSnooze(interaction, makeStore({ snoozeReminderByDiscordId }));

    const content = contentOf(editReply);
    expect(content).toContain('not found');
    expect(content).not.toContain('Rent');
    expect(content).not.toContain(OTHER_USER_ID);
  });

  it('requires a linked account before validating ids', async () => {
    const snoozeReminderByDiscordId = vi.fn(async () => makeReminder());
    const store = makeStore({
      getAccountByDiscordId: vi.fn(async () => null),
      snoozeReminderByDiscordId,
    });
    const { interaction, editReply } = makeInteraction({ local_id: 0 });

    await handleSnooze(interaction, store);

    expect(snoozeReminderByDiscordId).not.toHaveBeenCalled();
    expect(contentOf(editReply)).toContain('/link');
  });

  it('rejects an invalid id or day count without touching the store', async () => {
    const snoozeReminderByDiscordId = vi.fn(async () => makeReminder());
    const store = makeStore({ snoozeReminderByDiscordId });

    const badId = makeInteraction({ local_id: 0 });
    await handleSnooze(badId.interaction, store);
    expect(snoozeReminderByDiscordId).not.toHaveBeenCalled();
    expect(contentOf(badId.editReply)).toContain('positive integer');

    const badDays = makeInteraction({ local_id: 12, days: 31 });
    await handleSnooze(badDays.interaction, store);
    expect(snoozeReminderByDiscordId).not.toHaveBeenCalled();
    expect(contentOf(badDays.editReply)).toContain('between 1 and 30');
  });
});

describe('handlePaid', () => {
  it('marks the caller\'s own reminder as paid', async () => {
    const markPaidByDiscordId = vi.fn(async () => makeReminder());
    const { interaction, editReply } = makeInteraction({ local_id: 12 });

    await handlePaid(interaction, makeStore({ markPaidByDiscordId }));

    expect(markPaidByDiscordId).toHaveBeenCalledWith(DISCORD_USER_ID, 12);
    const content = contentOf(editReply);
    expect(content).toContain('Rent');
    expect(content).toContain('paid');
  });

  it('reports not-found generically', async () => {
    const markPaidByDiscordId = vi.fn(async () => null);
    const { interaction, editReply } = makeInteraction({ local_id: 99 });

    await handlePaid(interaction, makeStore({ markPaidByDiscordId }));

    const content = contentOf(editReply);
    expect(content).toContain('not found');
    expect(content).not.toContain('Rent');
  });

  it('requires a linked account', async () => {
    const markPaidByDiscordId = vi.fn(async () => makeReminder());
    const store = makeStore({
      getAccountByDiscordId: vi.fn(async () => null),
      markPaidByDiscordId,
    });
    const { interaction, editReply } = makeInteraction({ local_id: 12 });

    await handlePaid(interaction, store);

    expect(markPaidByDiscordId).not.toHaveBeenCalled();
    expect(contentOf(editReply)).toContain('/link');
  });
});
