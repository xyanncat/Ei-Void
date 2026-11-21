/**
 * Load a local .env file when one exists. Node 20.12+ provides
 * process.loadEnvFile; production containers have no file and rely on the
 * host's environment instead, so a missing file is expected.
 */
try {
  process.loadEnvFile();
} catch {
  // No .env next to the process — use the ambient environment.
}
