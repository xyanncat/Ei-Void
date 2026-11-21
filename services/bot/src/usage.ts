import type { ChatInputCommandInteraction } from 'discord.js';
import { planLimit } from '@eivoid/contracts';
import type { Account, UsageSnapshotRecord } from '@eivoid/db';
import { buildUsageEmbed } from './format.js';
import { NOT_LINKED_MESSAGE, replyEphemeral } from './replies.js';

export interface UsageStore {
  getAccountByDiscordId(discordUserId: string): Promise<Account | null>;
  latestUsage(clerkUserId: string): Promise<UsageSnapshotRecord | null>;
}

/**
 * `/usage` — the latest snapshot the app uploaded, or the plan's advertised
 * limit with `synced: false` when no snapshot exists yet. Both are ephemeral
 * and scoped to the caller's own account.
 */
export async function handleUsage(
  interaction: ChatInputCommandInteraction,
  store: UsageStore,
): Promise<void> {
  const account = await store.getAccountByDiscordId(interaction.user.id);
  if (!account) {
    await replyEphemeral(interaction, { content: NOT_LINKED_MESSAGE });
    return;
  }

  const snapshot = await store.latestUsage(account.clerkUserId);
  if (!snapshot) {
    await replyEphemeral(interaction, {
      embeds: [
        buildUsageEmbed({
          plan: account.plan,
          tokensUsed: 0,
          tokensLimit: planLimit(account.plan),
          periodEnd: null,
          synced: false,
        }),
      ],
    });
    return;
  }

  await replyEphemeral(interaction, {
    embeds: [
      buildUsageEmbed({
        plan: snapshot.plan,
        tokensUsed: snapshot.tokensUsed,
        tokensLimit: snapshot.tokensLimit,
        periodEnd: snapshot.periodEnd,
        synced: true,
      }),
    ],
  });
}
