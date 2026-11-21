import { describe, expect, it, vi } from 'vitest';
import type { EmbedBuilder } from 'discord.js';
import type { UsageSnapshotRecord } from '@eivoid/db';
import { handleUsage } from '../src/usage.js';
import { makeAccount, makeInteraction } from './fixtures.js';

function embedFrom(editReply: ReturnType<typeof vi.fn>): EmbedBuilder {
  const payload = editReply.mock.calls[0]?.[0] as { embeds?: EmbedBuilder[] };
  const embed = payload.embeds?.[0];
  expect(embed).toBeDefined();
  return embed!;
}

const snapshot: UsageSnapshotRecord = {
  id: 7,
  clerkUserId: 'user_clerk_1',
  plan: 'Pro',
  tokensUsed: 40_000_000,
  tokensLimit: 80_000_000,
  periodEnd: '2026-12-01',
  createdAt: new Date('2026-11-01T00:00:00.000Z'),
};

describe('handleUsage', () => {
  it('tells an unlinked caller to run /link', async () => {
    const getAccountByDiscordId = vi.fn(async () => null);
    const latestUsage = vi.fn(async () => snapshot);
    const { interaction, editReply } = makeInteraction();

    await handleUsage(interaction, { getAccountByDiscordId, latestUsage });

    expect(latestUsage).not.toHaveBeenCalled();
    expect(String((editReply.mock.calls[0]?.[0] as { content?: string }).content)).toContain(
      '/link',
    );
  });

  it('falls back to the plan limit with synced=false when nothing is stored', async () => {
    const getAccountByDiscordId = vi.fn(async () => makeAccount({ plan: 'Free' }));
    const latestUsage = vi.fn(async () => null);
    const { interaction, editReply } = makeInteraction();

    await handleUsage(interaction, { getAccountByDiscordId, latestUsage });

    const embed = embedFrom(editReply);
    expect(embed.data.description).toContain('No usage has been synced');
    expect(embed.data.fields?.[0]?.value).toBe('Free');
    expect(embed.data.fields?.[1]?.value).toBe('0 / 3,00,00,000');
  });

  it('uses the latest stored snapshot for the caller', async () => {
    const getAccountByDiscordId = vi.fn(async () => makeAccount());
    const latestUsage = vi.fn(async () => snapshot);
    const { interaction, editReply } = makeInteraction();

    await handleUsage(interaction, { getAccountByDiscordId, latestUsage });

    expect(latestUsage).toHaveBeenCalledWith('user_clerk_1');
    const embed = embedFrom(editReply);
    expect(embed.data.description).toContain('50%');
    expect(embed.data.fields?.[0]?.value).toBe('Pro');
    expect(embed.data.fields?.[1]?.value).toBe('4,00,00,000 / 8,00,00,000');
    expect(embed.data.fields?.[2]).toMatchObject({ name: 'Resets', value: '2026-12-01' });
  });
});
