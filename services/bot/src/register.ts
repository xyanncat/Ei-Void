import './env.js';
import { REST, Routes } from 'discord.js';
import { ConfigError as SharedConfigError } from '@eivoid/config';
import { commandData } from './commands.js';
import { ConfigError, readConfig } from './config.js';

try {
  const config = readConfig(process.env);
  const rest = new REST({ version: '10' }).setToken(config.token);

  // Register in the home guild first, so a failure below cannot leave the bot
  // with no commands at all.
  await rest.put(Routes.applicationGuildCommands(config.clientId, config.guildId), {
    body: commandData,
  });
  console.log(`Registered ${commandData.length} commands in the home guild.`);

  // Remove any global commands left by an earlier deployment. A global command
  // would appear in every server and undermine the single-guild lock.
  await rest.put(Routes.applicationCommands(config.clientId), { body: [] });
  console.log('Cleared global commands from any earlier deployment.');
} catch (error) {
  if (error instanceof ConfigError || error instanceof SharedConfigError) {
    console.error(`Configuration error: ${error.message}`);
  } else {
    console.error(
      'Command registration failed. Check that the bot token owns the application ' +
        'and that it is invited to DISCORD_GUILD_ID.',
    );
  }
  process.exit(1);
}
