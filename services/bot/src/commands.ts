import { SlashCommandBuilder } from 'discord.js';

const LOCAL_ID_MAX = Number.MAX_SAFE_INTEGER;

/**
 * Guild-scoped command definitions. `/snooze` and `/paid` identify a reminder
 * by the caller's own `local_id`, which is the id shown by `/reminders`.
 */
export const commandData = [
  new SlashCommandBuilder()
    .setName('usage')
    .setDescription('Show your Ei Void token usage'),
  new SlashCommandBuilder()
    .setName('link')
    .setDescription('Link your Discord account to your Ei Void account'),
  new SlashCommandBuilder()
    .setName('reminders')
    .setDescription('List your next five upcoming reminders'),
  new SlashCommandBuilder()
    .setName('snooze')
    .setDescription('Move one of your reminders forward')
    .addIntegerOption((option) =>
      option
        .setName('local_id')
        .setDescription('Reminder id shown by /reminders')
        .setMinValue(1)
        .setMaxValue(LOCAL_ID_MAX)
        .setRequired(true),
    )
    .addIntegerOption((option) =>
      option
        .setName('days')
        .setDescription('How many days to snooze (1-30)')
        .setMinValue(1)
        .setMaxValue(30)
        .setRequired(false),
    ),
  new SlashCommandBuilder()
    .setName('paid')
    .setDescription('Mark one of your reminders as paid')
    .addIntegerOption((option) =>
      option
        .setName('local_id')
        .setDescription('Reminder id shown by /reminders')
        .setMinValue(1)
        .setMaxValue(LOCAL_ID_MAX)
        .setRequired(true),
    ),
  new SlashCommandBuilder()
    .setName('about')
    .setDescription('About Ei Void, the coding agent on your phone'),
].map((command) => command.toJSON());
