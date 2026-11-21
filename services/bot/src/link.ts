import type { ChatInputCommandInteraction } from 'discord.js';
import type { CreateLinkCodeInput, LinkCode } from '@eivoid/db';
import { replyEphemeral } from './replies.js';

/** Link codes live for ten minutes, matching the website's /link page. */
export const LINK_CODE_TTL_MS = 10 * 60_000;

export interface LinkStore {
  createLinkCode(input: CreateLinkCodeInput): Promise<LinkCode>;
}

export interface LinkConfig {
  /** Public website base, already validated and stripped of a trailing slash. */
  readonly webBaseUrl: string;
}

/**
 * `/link` — creates a single-use code and returns the website URL that
 * consumes it. The code is never logged, and the bot token is never involved.
 */
export async function handleLink(
  interaction: ChatInputCommandInteraction,
  store: LinkStore,
  config: LinkConfig,
): Promise<void> {
  if (!config.webBaseUrl) {
    await replyEphemeral(interaction, {
      content: 'Account linking is not configured for this bot yet.',
    });
    return;
  }

  const code = await store.createLinkCode({
    discordUserId: interaction.user.id,
    discordUsername: interaction.user.username,
    ttlMs: LINK_CODE_TTL_MS,
  });

  const url = `${config.webBaseUrl}/link?code=${encodeURIComponent(code.code)}`;
  await replyEphemeral(interaction, {
    content: `Link your Ei Void account: ${url}\nThis link expires in 10 minutes.`,
  });
}
