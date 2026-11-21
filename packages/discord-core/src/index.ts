/**
 * Discord primitives shared by the Ei Void web service and bot worker.
 *
 * Everything here is defensive by design:
 *
 * - the bot is locked to one home guild, and the guard fails closed for
 *   interactions while a failed membership check never blocks a delivery that
 *   might still work;
 * - OAuth access tokens are used only as Bearer credentials and are never
 *   logged, stored or echoed in errors;
 * - DM failure is classified so a closed inbox can fall back to a channel
 *   mention instead of pretending the privacy setting was bypassed;
 * - message content and credentials never reach logs.
 */

import type { MessageCreateOptions, PermissionResolvable } from 'discord.js';

const DISCORD_API = 'https://discord.com/api/v10';
const SNOWFLAKE = /^\d{16,22}$/;

const DEFAULT_INVITE_PERMISSIONS = 3_072; // View Channels + Send Messages
const MEMBERSHIP_TTL_MS = 5 * 60 * 1_000;
const NON_MEMBERSHIP_TTL_MS = 60 * 1_000;
const MAX_TITLE_LENGTH = 180;
const MAX_FIELD_LENGTH = 180;
const MAX_NOTES_LENGTH = 1_000;
const MAX_DM_LENGTH = 1_900;
const MAX_FALLBACK_LENGTH = 1_990;

export const PREFERRED_FALLBACK_CHANNEL_NAMES: readonly string[] = [
  'ei-snore-alerts',
  'ei-snore',
  'eisnore',
  'reminders',
  'notifications',
  'alerts',
];

function asText(value: unknown): string {
  if (typeof value === 'string') return value;
  if (value === null || value === undefined) return '';
  return String(value);
}

function trimmed(value: unknown): string {
  return asText(value).trim();
}

function isSnowflake(value: string): boolean {
  return SNOWFLAKE.test(value);
}

function errorCodeOf(error: unknown): unknown {
  if (typeof error === 'object' && error !== null && 'code' in error) {
    return (error as { code?: unknown }).code;
  }
  return undefined;
}

function errorStatusOf(error: unknown): number | undefined {
  if (typeof error === 'object' && error !== null && 'status' in error) {
    const status = Number((error as { status?: unknown }).status);
    return Number.isFinite(status) ? status : undefined;
  }
  return undefined;
}

/** The minimum channel surface the reminder delivery code needs. */
export interface DiscordChannel {
  readonly id: string;
  readonly name?: string | null;
  readonly guildId?: string | null;
  isTextBased?(): boolean;
  isThread?(): boolean;
  permissionsFor?(member: unknown): {
    has(permission: PermissionResolvable): boolean;
  } | null;
  send?(options: DiscordSendOptions): Promise<unknown>;
}

/** The minimum user surface the reminder delivery code needs. */
export interface DiscordUser {
  send(options: DiscordSendOptions): Promise<unknown>;
}

/** The discord.js message payload shape accepted by `send`. */
export type DiscordSendOptions = MessageCreateOptions;

export interface DiscordGuild {
  readonly id: string;
  readonly name?: string;
  leave?(): Promise<unknown>;
  members: {
    me?: unknown;
    fetch(options: { user: string; force?: boolean }): Promise<unknown>;
  };
  channels: {
    cache: DiscordCollection<DiscordChannel>;
  };
}

/**
 * The slice of a discord.js `Collection` used here. `Collection`'s default
 * iterator yields `[key, value]` pairs, so iteration goes through `values()`.
 */
export interface DiscordCollection<T> {
  get(id: string): T | undefined;
  values(): IterableIterator<T>;
}

/**
 * Structurally compatible with discord.js 14's `Client`. Keeping the public
 * surface minimal lets tests use a fake client and keeps the package free of
 * gateway side effects.
 */
export interface DiscordClient {
  isReady?(): boolean;
  users?: {
    fetch(userId: string): Promise<DiscordUser>;
  };
  guilds: {
    cache: DiscordCollection<DiscordGuild>;
  };
  channels?: {
    cache: DiscordCollection<DiscordChannel>;
    fetch?(channelId: string): Promise<DiscordChannel | null>;
  };
}

/**
 * True only for the configured home server. A DM has `guildId === null`, an
 * empty guild id is not a server, and a missing home id matches nothing.
 */
export function isHomeGuild(
  guildId: string | null | undefined,
  homeGuildId: string,
): boolean {
  const home = trimmed(homeGuildId);
  return (
    home.length > 0 &&
    typeof guildId === 'string' &&
    guildId.length > 0 &&
    trimmed(guildId) === home
  );
}

export interface InviteUrlOptions {
  readonly applicationId: string;
  readonly permissions?: number;
  readonly guildId?: string;
}

/**
 * Builds the bot install URL. When a guild id is supplied the server picker is
 * hidden and the install is locked to that guild, matching the single-guild
 * policy.
 */
export function inviteUrl(options: InviteUrlOptions): string {
  const applicationId = trimmed(options.applicationId);
  if (!applicationId) {
    throw new TypeError('applicationId is required');
  }

  const requested = Number(options.permissions ?? DEFAULT_INVITE_PERMISSIONS);
  const permissions =
    Number.isFinite(requested) && requested >= 0
      ? Math.trunc(requested)
      : DEFAULT_INVITE_PERMISSIONS;

  const params = [
    `client_id=${encodeURIComponent(applicationId)}`,
    `permissions=${permissions}`,
    'scope=bot%20applications.commands',
  ];
  const guildId = trimmed(options.guildId);
  if (guildId) {
    params.push(`guild_id=${encodeURIComponent(guildId)}`);
    params.push('disable_guild_select=true');
  }
  return `https://discord.com/oauth2/authorize?${params.join('&')}`;
}

export type DiscordAuthErrorCode =
  | 'invalid_discord_token'
  | 'guild_membership_required'
  | 'discord_rate_limited'
  | 'discord_unavailable';

export class DiscordAuthError extends Error {
  readonly code: DiscordAuthErrorCode;
  readonly statusCode: number;

  constructor(code: DiscordAuthErrorCode, statusCode: number) {
    super(code);
    this.name = 'DiscordAuthError';
    this.code = code;
    this.statusCode = statusCode;
  }
}

export interface VerifiedDiscordUser {
  readonly id: string;
  readonly username: string;
}

export interface VerifyDiscordOAuthOptions {
  readonly guildId?: string;
  readonly fetchImpl?: typeof fetch;
  readonly timeoutMs?: number;
}

function authError(
  code: DiscordAuthErrorCode,
  statusCode: number,
): DiscordAuthError {
  return new DiscordAuthError(code, statusCode);
}

/**
 * Verifies a Discord OAuth access token and, when a guild id is supplied, that
 * the token's user is a member of that guild. Status mapping matches the Ei
 * Snore backend: 401/403 is an invalid token, 429 is a rate limit, and any
 * other failure is Discord being unavailable.
 */
export async function verifyDiscordOAuthToken(
  accessToken: string,
  options: VerifyDiscordOAuthOptions = {},
): Promise<VerifiedDiscordUser> {
  const token = trimmed(accessToken).replace(/^Bearer\s+/i, '').trim();
  if (!token) throw authError('invalid_discord_token', 401);

  const fetchImpl = options.fetchImpl ?? fetch;
  const timeoutMs = Math.max(1, Number(options.timeoutMs) || 10_000);
  const requestSignal = (): AbortSignal => AbortSignal.timeout(timeoutMs);

  let userResponse: Response;
  try {
    userResponse = await fetchImpl(`${DISCORD_API}/users/@me`, {
      headers: { Authorization: `Bearer ${token}` },
      signal: requestSignal(),
    });
  } catch {
    throw authError('discord_unavailable', 503);
  }
  if (userResponse.status === 401 || userResponse.status === 403) {
    throw authError('invalid_discord_token', 401);
  }
  if (userResponse.status === 429) {
    throw authError('discord_rate_limited', 429);
  }
  if (!userResponse.ok) {
    throw authError('discord_unavailable', 503);
  }

  const user = (await userResponse.json().catch(() => null)) as {
    id?: unknown;
    global_name?: unknown;
    username?: unknown;
  } | null;
  const id = trimmed(user?.id);
  if (!isSnowflake(id)) {
    throw authError('invalid_discord_token', 401);
  }
  const username =
    trimmed(user?.global_name) || trimmed(user?.username) || 'Discord user';

  const guildId = trimmed(options.guildId);
  if (!guildId) {
    return { id, username: username.slice(0, 120) };
  }

  let guildsResponse: Response;
  try {
    guildsResponse = await fetchImpl(`${DISCORD_API}/users/@me/guilds`, {
      headers: { Authorization: `Bearer ${token}` },
      signal: requestSignal(),
    });
  } catch {
    throw authError('discord_unavailable', 503);
  }
  if (
    guildsResponse.status === 400 ||
    guildsResponse.status === 401 ||
    guildsResponse.status === 403
  ) {
    throw authError('invalid_discord_token', 401);
  }
  if (guildsResponse.status === 429) {
    throw authError('discord_rate_limited', 429);
  }
  if (!guildsResponse.ok) {
    throw authError('discord_unavailable', 503);
  }

  const guilds = (await guildsResponse.json().catch(() => null)) as unknown;
  const member =
    Array.isArray(guilds) &&
    guilds.some((guild) => {
      if (typeof guild !== 'object' || guild === null) return false;
      return trimmed((guild as { id?: unknown }).id) === guildId;
    });
  if (!member) {
    throw authError('guild_membership_required', 403);
  }
  return { id, username: username.slice(0, 120) };
}

interface MembershipCacheEntry {
  readonly member: boolean;
  readonly expiresAt: number;
}

const membershipCache = new Map<string, MembershipCacheEntry>();

/** Clears the process-local membership cache. Intended for tests. */
export function clearMembershipCache(): void {
  membershipCache.clear();
}

/**
 * Checks that a Discord user is still in the given guild before a delivery is
 * attempted. Results are cached (5 minutes positive, 60 seconds negative). An
 * inconclusive lookup returns true so a Discord outage cannot suppress a
 * reminder that might still be deliverable.
 */
export async function userIsGuildMember(
  client: DiscordClient,
  guildId: string,
  userId: string,
): Promise<boolean> {
  const guild = trimmed(guildId);
  const user = trimmed(userId);

  if (!isSnowflake(guild)) return true;
  if (!isSnowflake(user)) return false;

  const key = `${guild}:${user}`;
  const cached = membershipCache.get(key);
  if (cached && Date.now() < cached.expiresAt) return cached.member;

  let member: boolean | null = null;
  const guildObject = client.guilds.cache.get(guild);
  if (guildObject && typeof client.isReady === 'function' && client.isReady()) {
    try {
      member = Boolean(
        await guildObject.members.fetch({ user, force: false }),
      );
    } catch (error) {
      const code = Number(errorCodeOf(error));
      const status = errorStatusOf(error);
      if (code === 10_007 || status === 404) member = false;
    }
  }

  if (member === null) {
    // Discord was unreachable or the client was not ready. Do not cache a
    // verdict and do not block a delivery that might still work.
    return true;
  }

  membershipCache.set(key, {
    member,
    expiresAt: Date.now() + (member ? MEMBERSHIP_TTL_MS : NON_MEMBERSHIP_TTL_MS),
  });
  return member;
}

export interface ReminderMessageInput {
  readonly title: string;
  readonly dueAt: number | Date | string;
  readonly merchant?: string | null;
  readonly bank?: string | null;
  readonly notes?: string | null;
  readonly timezone?: string | null;
}

export interface ReminderMessage {
  readonly title: string;
  readonly body: string;
}

function toEpochMs(value: number | Date | string): number {
  if (value instanceof Date) return value.getTime();
  if (typeof value === 'number') return value;
  const text = value.trim();
  if (/^-?\d+$/.test(text)) return Number(text);
  return Date.parse(text);
}

/** Formats an epoch timestamp in the user's timezone, falling back to UTC. */
export function formatDueDate(
  dueAt: number | Date | string,
  timezone?: string | null,
): string {
  const epochMs = toEpochMs(dueAt);
  if (!Number.isFinite(epochMs)) return 'Unknown';
  const date = new Date(epochMs);
  try {
    return new Intl.DateTimeFormat('en-GB', {
      timeZone: trimmed(timezone) || 'UTC',
      dateStyle: 'medium',
      timeStyle: 'short',
    }).format(date);
  } catch {
    return date.toUTCString();
  }
}

function cleanText(value: unknown, maxLength: number): string {
  return asText(value).trim().slice(0, maxLength);
}

/**
 * Builds the reminder title and body shared by Discord and email delivery.
 * Optional fields are omitted when empty, and notes are separated from the
 * structured lines by a blank line, matching the Ei Snore reminder format.
 */
export function buildReminderMessage(input: ReminderMessageInput): ReminderMessage {
  const title = cleanText(input.title, MAX_TITLE_LENGTH) || 'Reminder';
  const merchant = cleanText(input.merchant, MAX_FIELD_LENGTH);
  const bank = cleanText(input.bank, MAX_FIELD_LENGTH);
  const notes = cleanText(input.notes, MAX_NOTES_LENGTH);

  const body = [
    `Due: ${formatDueDate(input.dueAt, input.timezone)}`,
    merchant ? `Merchant: ${merchant}` : '',
    bank ? `Bank: ${bank}` : '',
    notes ? `\n${notes}` : '',
  ]
    .filter(Boolean)
    .join('\n');

  return { title, body };
}

function findGuildChannel(
  client: DiscordClient,
  guildId: string,
  channelId: string,
): DiscordChannel | undefined {
  const guild = client.guilds.cache.get(guildId);
  if (!guild) return undefined;
  for (const channel of guild.channels.cache.values()) {
    if (channel.id === channelId) return channel;
  }
  return undefined;
}

/**
 * Resolves the channel used when a DM cannot be delivered. An explicitly
 * configured channel is accepted only when it is known to belong to the home
 * guild. Otherwise the first text channel the bot can post in is used,
 * preferring clearly named notification channels.
 */
export function selectFallbackChannel(
  client: DiscordClient,
  guildId: string,
  preferredChannelId?: string,
): string | null {
  const guild = trimmed(guildId);
  if (!isSnowflake(guild)) return null;

  const preferred = trimmed(preferredChannelId);
  if (preferred) {
    const cached =
      client.channels?.cache.get(preferred) ??
      findGuildChannel(client, guild, preferred);
    if (cached?.guildId && cached.guildId !== guild) return null;
    return preferred;
  }

  const guildObject = client.guilds.cache.get(guild);
  if (!guildObject) return null;

  const me = guildObject.members.me;
  const candidates: DiscordChannel[] = [];
  for (const channel of guildObject.channels.cache.values()) {
    if (typeof channel.isTextBased === 'function' && !channel.isTextBased()) {
      continue;
    }
    if (typeof channel.isThread === 'function' && channel.isThread()) {
      continue;
    }
    if (me && typeof channel.permissionsFor === 'function') {
      const permissions = channel.permissionsFor(me);
      if (!permissions?.has('ViewChannel')) continue;
      if (!permissions.has('SendMessages')) continue;
    }
    candidates.push(channel);
  }

  const preferredNamed = candidates.find((channel) =>
    PREFERRED_FALLBACK_CHANNEL_NAMES.includes(
      trimmed(channel.name).toLowerCase(),
    ),
  );
  const chosen = preferredNamed ?? candidates[0];
  return chosen ? chosen.id : null;
}

export type DiscordDeliveryErrorCode =
  | 'invalid_recipient'
  | 'guild_membership_required'
  | 'discord_dm_blocked'
  | 'discord_rate_limited'
  | 'discord_unreachable'
  | 'discord_send_failed'
  | 'discord_fallback_failed';

export type DiscordReminderResult =
  | { readonly kind: 'dm' }
  | { readonly kind: 'fallback'; readonly channelId: string }
  | {
      readonly kind: 'error';
      readonly status: number;
      readonly code: DiscordDeliveryErrorCode;
      readonly fallbackAttempted: boolean;
    };

export interface SendDiscordReminderOptions {
  readonly userId: string;
  readonly title: string;
  readonly body: string;
  readonly fallbackChannelId?: string;
  readonly guildId?: string;
}

interface DmFailure {
  readonly kind: 'dm_failure';
  readonly status: number;
  readonly code: DiscordDeliveryErrorCode;
  readonly fallbackable: boolean;
}

function failure(
  status: number,
  code: DiscordDeliveryErrorCode,
  fallbackable: boolean,
): DmFailure {
  return { kind: 'dm_failure', status, code, fallbackable };
}

function errorResult(
  status: number,
  code: DiscordDeliveryErrorCode,
  fallbackAttempted: boolean,
): DiscordReminderResult {
  return { kind: 'error', status, code, fallbackAttempted };
}

async function sendDirectMessage(
  client: DiscordClient,
  userId: string,
  content: string,
): Promise<{ kind: 'dm' } | DmFailure> {
  if (!client.users) {
    return failure(503, 'discord_unreachable', true);
  }

  try {
    const user = await client.users.fetch(userId);
    if (typeof user.send !== 'function') {
      return failure(502, 'discord_send_failed', false);
    }
    await user.send({
      content,
      allowedMentions: { parse: [] },
    });
    return { kind: 'dm' };
  } catch (error) {
    const status = errorStatusOf(error);
    const code = Number(errorCodeOf(error));

    if (status === 429) {
      return failure(429, 'discord_rate_limited', false);
    }
    if (code === 50_007 || status === 403) {
      // 50007: "Cannot send messages to this user" — DMs closed or no shared
      // server. This is the only recoverable case, via a channel mention.
      return failure(403, 'discord_dm_blocked', true);
    }
    return failure(502, 'discord_send_failed', false);
  }
}

async function sendFallback(
  client: DiscordClient,
  channelId: string,
  userId: string,
  message: string,
  homeGuildId: string | undefined,
): Promise<DiscordReminderResult> {
  let channel: DiscordChannel | null | undefined =
    client.channels?.cache.get(channelId);
  if (!channel && typeof client.channels?.fetch === 'function') {
    try {
      channel = await client.channels.fetch(channelId);
    } catch {
      return errorResult(502, 'discord_fallback_failed', true);
    }
  }
  if (!channel) {
    return errorResult(502, 'discord_fallback_failed', true);
  }
  if (channel.guildId && homeGuildId && channel.guildId !== homeGuildId) {
    return errorResult(502, 'discord_fallback_failed', true);
  }
  if (typeof channel.isTextBased === 'function' && !channel.isTextBased()) {
    return errorResult(502, 'discord_fallback_failed', true);
  }
  if (typeof channel.send !== 'function') {
    return errorResult(502, 'discord_fallback_failed', true);
  }

  const content = `<@${userId}>\n${message}`.slice(0, MAX_FALLBACK_LENGTH);
  try {
    await channel.send({
      content,
      allowedMentions: { parse: ['users'], users: [userId] },
    });
    return { kind: 'fallback', channelId };
  } catch (error) {
    if (errorStatusOf(error) === 429) {
      return errorResult(429, 'discord_rate_limited', true);
    }
    return errorResult(502, 'discord_fallback_failed', true);
  }
}

/**
 * Delivers a reminder by DM, falling back to a channel mention when Discord
 * refuses the DM (50007 / 403). The result always says which path was used, or
 * which classified failure occurred; message content is never logged.
 */
export async function sendDiscordReminder(
  client: DiscordClient,
  options: SendDiscordReminderOptions,
): Promise<DiscordReminderResult> {
  const userId = trimmed(options.userId);
  if (!isSnowflake(userId)) {
    return errorResult(400, 'invalid_recipient', false);
  }

  const homeGuildId = trimmed(options.guildId) || undefined;
  if (homeGuildId) {
    const member = await userIsGuildMember(client, homeGuildId, userId);
    if (!member) {
      return errorResult(403, 'guild_membership_required', false);
    }
  }

  const message = `${asText(options.title)}\n\n${asText(options.body)}`.slice(
    0,
    MAX_DM_LENGTH,
  );

  const delivered = await sendDirectMessage(client, userId, message);
  if (delivered.kind === 'dm') return { kind: 'dm' };
  if (!delivered.fallbackable) {
    return errorResult(delivered.status, delivered.code, false);
  }

  const channelId =
    trimmed(options.fallbackChannelId) ||
    (homeGuildId ? selectFallbackChannel(client, homeGuildId) : null);
  if (!channelId) {
    return errorResult(delivered.status, delivered.code, false);
  }

  return sendFallback(client, channelId, userId, message, homeGuildId);
}

export interface LeaveForeignGuildsResult {
  readonly left: readonly string[];
  readonly failed: readonly string[];
}

/**
 * Leaves every guild except the configured home guild. A malformed home id is
 * treated as "do not act" rather than risking leaving the only good guild.
 */
export async function leaveForeignGuilds(
  client: DiscordClient,
  homeGuildId: string,
): Promise<LeaveForeignGuildsResult> {
  const home = trimmed(homeGuildId);
  if (!isSnowflake(home)) return { left: [], failed: [] };

  const left: string[] = [];
  const failed: string[] = [];

  for (const guild of client.guilds.cache.values()) {
    if (!guild || guild.id === home) continue;
    if (typeof guild.leave !== 'function') {
      failed.push(guild.id);
      continue;
    }
    try {
      await guild.leave();
      left.push(guild.id);
    } catch {
      failed.push(guild.id);
    }
  }

  return { left, failed };
}
