import { createHash, randomBytes } from 'node:crypto';

/** Creates a URL-safe device key of the same shape the dashboard issues. */
export function generateDeviceKey(): string {
  return `void_${randomBytes(24).toString('base64url')}`;
}

/** SHA-256 hex digest. Only the digest is stored in the database. */
export function hashDeviceKey(key: string): string {
  return createHash('sha256').update(String(key), 'utf8').digest('hex');
}
