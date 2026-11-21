import { MessageFlags, type ChatInputCommandInteraction, type EmbedBuilder } from 'discord.js';

/** Shown whenever a command needs a linked account. */
export const NOT_LINKED_MESSAGE =
  'Your Discord account is not linked to an Ei Void account yet. Run /link to connect it.';

export interface EphemeralPayload {
  content?: string;
  embeds?: EmbedBuilder[];
}

/**
 * Replies ephemerally whether or not the interaction was already deferred.
 * The index defers before database work so Discord's three-second window is
 * never missed; handlers then edit that deferred reply.
 */
export async function replyEphemeral(
  interaction: ChatInputCommandInteraction,
  payload: EphemeralPayload,
): Promise<void> {
  if (interaction.deferred || interaction.replied) {
    await interaction.editReply(payload);
    return;
  }
  await interaction.reply({ ...payload, flags: MessageFlags.Ephemeral });
}
