import { z } from 'zod';

/**
 * Stable machine-readable error codes returned by the HTTP API. The `error`
 * field is part of the public contract; human-facing detail is optional.
 */
export const ERROR_CODES = [
  'account_failed',
  'backend_not_configured',
  'bad_request',
  'clerk_issuer_required',
  'clerk_jwks_required',
  'clerk_not_configured',
  'database_not_configured',
  'disconnect_failed',
  'discord_dm_blocked',
  'discord_dm_channel_failed',
  'discord_dm_unavailable',
  'discord_fallback_failed',
  'discord_not_linked',
  'discord_rate_limited',
  'discord_send_failed',
  'discord_unlink_failed',
  'discord_unreachable',
  'email_not_configured',
  'email_not_synced',
  'email_send_failed',
  'guild_membership_required',
  'internal_error',
  'invalid_clerk_token',
  'invalid_clerk_user',
  'invalid_discord_token',
  'invalid_email',
  'invalid_reminder',
  'invalid_timezone',
  'not_found',
  'site_unavailable',
  'too_many_reminders',
  'too_many_requests',
  'update_asset_missing',
  'update_check_failed',
  'update_download_failed',
  'update_not_configured',
  'update_redirect_missing',
  'update_release_unavailable',
] as const;

export type ErrorCode = (typeof ERROR_CODES)[number];

export const errorCodeSchema = z.enum(ERROR_CODES);

export const errorResponseSchema = z.object({
  error: errorCodeSchema,
  detail: z.string().optional(),
});

export type ErrorResponse = z.infer<typeof errorResponseSchema>;
