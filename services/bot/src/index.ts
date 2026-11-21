import './env.js';
import { Client, Events, GatewayIntentBits, MessageFlags, type Interaction } from 'discord.js';
import { ConfigError as SharedConfigError } from '@eivoid/config';
import { createPool, migrate, Store } from '@eivoid/db';
import { isHomeGuild, leaveForeignGuilds } from '@eivoid/discord-core';
import { createMailer } from '@eivoid/mailer';
import { ConfigError, readConfig, type BotConfig } from './config.js';
import { startHealthServer } from './health.js';
import { handleLink } from './link.js';
import { handlePaid, handleReminders, handleSnooze } from './reminderCommands.js';
import { replyEphemeral } from './replies.js';
import { createScheduler } from './scheduler.js';
import { handleUsage } from './usage.js';

function loadConfig(): BotConfig {
  try {
    return readConfig(process.env);
  } catch (error) {
    if (error instanceof ConfigError || error instanceof SharedConfigError) {
      console.error(`Configuration error: ${error.message}`);
      process.exit(1);
    }
    throw error;
  }
}

const config = loadConfig();
const pool = createPool(config.databaseUrl);
const store = new Store(pool);
const mailer = createMailer(config.mail);
const client = new Client({ intents: [GatewayIntentBits.Guilds] });

const scheduler = createScheduler({
  store,
  client,
  mailer,
  config: {
    guildId: config.guildId,
    fallbackChannelId: config.fallbackChannelId,
  },
  log: (code) => console.warn(`[scheduler] ${code}`),
});

function aboutText(): string {
  const portal = config.webBaseUrl ? `\n${config.webBaseUrl}` : '';
  return (
    'Ei Void is a native Android AI coding agent. It clones a repository onto your phone, ' +
    `edits it through guarded tools, and pushes the result.${portal}`
  );
}

async function handleInteraction(interaction: Interaction): Promise<void> {
  if (!interaction.isChatInputCommand()) return;

  // One server, no DMs. A DM interaction has guildId === null.
  if (!isHomeGuild(interaction.guildId, config.guildId)) {
    await interaction
      .reply({
        content: 'Ei Void only answers in its home server.',
        flags: MessageFlags.Ephemeral,
      })
      .catch(() => undefined);
    return;
  }

  try {
    // Database-backed commands defer immediately so Discord's three-second
    // acknowledgement window is never missed. Handlers then edit the reply.
    if (
      interaction.commandName === 'usage' ||
      interaction.commandName === 'link' ||
      interaction.commandName === 'reminders' ||
      interaction.commandName === 'snooze' ||
      interaction.commandName === 'paid'
    ) {
      await interaction.deferReply({ flags: MessageFlags.Ephemeral });
    }

    switch (interaction.commandName) {
      case 'usage':
        await handleUsage(interaction, store);
        break;
      case 'link':
        await handleLink(interaction, store, { webBaseUrl: config.webBaseUrl });
        break;
      case 'reminders':
        await handleReminders(interaction, store);
        break;
      case 'snooze':
        await handleSnooze(interaction, store);
        break;
      case 'paid':
        await handlePaid(interaction, store);
        break;
      case 'about':
        await replyEphemeral(interaction, { content: aboutText() });
        break;
      default:
        await replyEphemeral(interaction, { content: 'Unknown command.' });
        break;
    }
  } catch {
    // Never echo the error: database and Discord errors can contain row data.
    console.error(`command_failed:${interaction.commandName}`);
    await replyEphemeral(interaction, {
      content: 'Something went wrong while handling that command.',
    }).catch(() => undefined);
  }
}

let health: ReturnType<typeof startHealthServer> | null = null;

async function main(): Promise<void> {
  const migration = await migrate(pool);
  console.log(
    `Database schema is at version ${migration.currentVersion} ` +
      `(${migration.applied.length} migration(s) applied).`,
  );

  health = startHealthServer({
    client,
    pool,
    mailer,
    scheduler,
    guildId: config.guildId,
    port: config.healthPort,
    log: (code) => console.warn(`[health] ${code}`),
  });

  client.once(Events.ClientReady, async (ready) => {
    console.log(`Ei Void bot is online as ${ready.user.username}.`);
    console.log(`Home server: ${config.guildId}. Commands are guild-scoped.`);

    try {
      const result = await leaveForeignGuilds(client, config.guildId);
      if (result.left.length > 0) {
        console.warn(`Left ${result.left.length} guild(s) outside the home server.`);
      }
      if (result.failed.length > 0) {
        console.warn(`Could not leave ${result.failed.length} guild(s) outside the home server.`);
      }
    } catch {
      console.warn('guild_lock_failed');
    }

    scheduler.start();
  });

  client.on(Events.GuildCreate, async (guild) => {
    if (isHomeGuild(guild.id, config.guildId)) return;
    try {
      await guild.leave();
      console.warn('Left a guild outside the home server.');
    } catch {
      console.warn('foreign_guild_leave_failed');
    }
  });

  client.on(Events.InteractionCreate, (interaction) => {
    void handleInteraction(interaction);
  });

  await client.login(config.token);
}

main().catch(() => {
  // Deliberately generic: this path must never print the token or DATABASE_URL.
  console.error('Bot startup failed. Check configuration, DATABASE_URL and Discord access.');
  process.exit(1);
});

let shuttingDown = false;

async function shutdown(signal: string): Promise<void> {
  if (shuttingDown) return;
  shuttingDown = true;
  console.log(`Received ${signal}; shutting down.`);

  scheduler.stop();
  if (health) health.close();

  // A hung pool or gateway must not keep the container alive forever.
  const force = setTimeout(() => process.exit(1), 10_000);
  force.unref();

  try {
    await client.destroy();
  } catch {
    // Already disconnected.
  }
  try {
    await pool.end();
  } catch {
    // Already closed.
  }
  process.exit(0);
}

process.on('SIGTERM', () => {
  void shutdown('SIGTERM');
});
process.on('SIGINT', () => {
  void shutdown('SIGINT');
});
