import 'dotenv/config';
import { createPool, migrate, Store } from '@eivoid/db';
import { createMailer } from '@eivoid/mailer';
import { createApp } from './app.js';
import { createClerkAuth } from './auth/clerk.js';
import { loadConfig } from './config.js';

async function main(): Promise<void> {
  const config = loadConfig();

  if (!config.databaseUrl) {
    throw new Error(
      'DATABASE_URL is required. Create the Postgres database and set DATABASE_URL before starting the web service.',
    );
  }

  const pool = createPool(config.databaseUrl);
  await migrate(pool);

  const store = new Store(pool);
  const auth = createClerkAuth(config);
  const mailer = createMailer(config.mail);

  if (!config.clerk.secretKey || !config.clerk.publishableKey) {
    console.warn('Clerk is not configured; browser sign-in and bearer auth will reject requests.');
  }
  if (mailer.provider === null) {
    console.warn('No mail provider is configured; the test email endpoint will return 503.');
  }
  if (!config.discord.token) {
    console.warn('DISCORD_BOT_TOKEN is not set; test DMs will return 501.');
  }
  if (!config.github.token) {
    console.warn('GITHUB_TOKEN is not set; the app release proxy will return 503.');
  }

  const app = createApp({ config, store, auth, mailer });
  const server = app.listen(config.port, () => {
    console.log(`Ei Void web listening on ${config.baseUrl} (Postgres store).`);
  });

  const shutdown = (signal: string) => {
    console.log(`${signal} received; closing the web service.`);
    server.close(() => {
      void pool.end().finally(() => process.exit(0));
    });
    setTimeout(() => process.exit(1), 10_000).unref();
  };

  process.once('SIGTERM', () => shutdown('SIGTERM'));
  process.once('SIGINT', () => shutdown('SIGINT'));
}

main().catch((error: unknown) => {
  const message = error instanceof Error ? error.message : 'unknown error';
  console.error('Ei Void web failed to start:', message);
  process.exit(1);
});
