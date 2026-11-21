#!/usr/bin/env node
import { pathToFileURL } from 'node:url';
import { createPool, migrate } from './index.js';

async function main(): Promise<void> {
  const databaseUrl = process.env.DATABASE_URL?.trim();
  if (!databaseUrl) {
    console.error('DATABASE_URL is not set; nothing to migrate.');
    process.exitCode = 1;
    return;
  }

  const pool = createPool(databaseUrl, { max: 1 });
  try {
    const result = await migrate(pool);
    if (result.applied.length === 0) {
      console.log(`Database schema is up to date (version ${result.currentVersion}).`);
    } else {
      console.log(
        `Applied migrations ${result.applied.join(', ')}; schema version ${result.currentVersion}.`,
      );
    }
  } finally {
    await pool.end();
  }
}

const entry = process.argv[1];
if (entry && import.meta.url === pathToFileURL(entry).href) {
  try {
    process.loadEnvFile();
  } catch {
    // No .env next to the process; use the ambient environment.
  }

  main().catch((error: unknown) => {
    const message = error instanceof Error ? error.message : String(error);
    console.error(`Migration failed: ${message}`);
    process.exitCode = 1;
  });
}
