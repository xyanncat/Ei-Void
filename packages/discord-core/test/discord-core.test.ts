import { beforeEach, describe, expect, it, vi } from 'vitest';
import {
  DiscordAuthError,
  PREFERRED_FALLBACK_CHANNEL_NAMES,
  buildReminderMessage,
  clearMembershipCache,
  inviteUrl,
  isHomeGuild,
  leaveForeignGuilds,
  selectFallbackChannel,
  sendDiscordReminder,
  userIsGuildMember,
  verifyDiscordOAuthToken,
  type DiscordChannel,
  type DiscordClient,
  type DiscordCollection,
  type DiscordGuild,
} from '../src/index.js';

const HOME_GUILD_ID = '848841415940898827';
const USER_ID = '123456789012345678';
const APPLICATION_ID = '1573240846223151144';
const FOREIGN_GUILD_ID = '999999999999999999';

class FakeCollection<T extends { id: string }> implements DiscordCollection<T> {
  private readonly items = new Map<string, T>();

  add(item: T): this {
    this.items.set(item.id, item);
    return this;
  }

  get(id: string): T | undefined {
    return this.items.get(id);
  }

  values(): IterableIterator<T> {
    return this.items.values();
  }
}

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  });
}

function makeChannel(
  overrides: Partial<DiscordChannel> & { id: string },
): DiscordChannel {
  return {
    isTextBased: () => true,
    isThread: () => false,
    permissionsFor: () => ({ has: () => true }),
    send: async () => ({}),
    ...overrides,
  };
}

function makeGuild(overrides: Partial<DiscordGuild> = {}): DiscordGuild {
  return {
    id: HOME_GUILD_ID,
    name: 'Ei Void',
    members: {
      me: { id: '999999999999999998' },
      fetch: async () => ({ id: USER_ID }),
    },
    channels: { cache: new FakeCollection<DiscordChannel>() },
    leave: async () => undefined,
    ...overrides,
  };
}

interface FakeClientOptions {
  guilds?: DiscordGuild[];
  channels?: DiscordChannel[];
  users?: DiscordClient['users'];
  ready?: boolean;
}

function makeClient(options: FakeClientOptions = {}): DiscordClient {
  const guilds = new FakeCollection<DiscordGuild>();
  for (const guild of options.guilds ?? []) guilds.add(guild);
  const channels = new FakeCollection<DiscordChannel>();
  // discord.js keeps guild channels in the client-level channel cache too.
  for (const guild of options.guilds ?? []) {
    for (const channel of guild.channels.cache.values()) channels.add(channel);
  }
  for (const channel of options.channels ?? []) channels.add(channel);

  return {
    isReady: () => options.ready ?? true,
    users: options.users ?? {
      fetch: async () => ({ send: async () => ({}) }),
    },
    guilds: { cache: guilds },
    channels: {
      cache: channels,
      fetch: async (channelId: string) => {
        const channel = channels.get(channelId);
        if (!channel) {
          throw Object.assign(new Error('Unknown Channel'), { status: 404 });
        }
        return channel;
      },
    },
  };
}

function channelsCollection(
  ...channels: DiscordChannel[]
): FakeCollection<DiscordChannel> {
  const collection = new FakeCollection<DiscordChannel>();
  for (const channel of channels) collection.add(channel);
  return collection;
}

beforeEach(() => {
  clearMembershipCache();
});

describe('isHomeGuild', () => {
  it('accepts only the configured home server', () => {
    expect(isHomeGuild(HOME_GUILD_ID, HOME_GUILD_ID)).toBe(true);
    expect(isHomeGuild(FOREIGN_GUILD_ID, HOME_GUILD_ID)).toBe(false);
  });

  it('refuses DMs, empty ids and a missing home id', () => {
    expect(isHomeGuild(null, HOME_GUILD_ID)).toBe(false);
    expect(isHomeGuild(undefined, HOME_GUILD_ID)).toBe(false);
    expect(isHomeGuild('', HOME_GUILD_ID)).toBe(false);
    expect(isHomeGuild(HOME_GUILD_ID, '')).toBe(false);
  });

  it('compares against the supplied id rather than a hard-coded one', () => {
    expect(isHomeGuild(FOREIGN_GUILD_ID, FOREIGN_GUILD_ID)).toBe(true);
    expect(isHomeGuild(HOME_GUILD_ID, FOREIGN_GUILD_ID)).toBe(false);
  });
});

describe('inviteUrl', () => {
  it('locks the invite to the guild when one is supplied', () => {
    expect(
      inviteUrl({
        applicationId: APPLICATION_ID,
        permissions: 18_432,
        guildId: HOME_GUILD_ID,
      }),
    ).toBe(
      'https://discord.com/oauth2/authorize?client_id=1573240846223151144' +
        '&permissions=18432&scope=bot%20applications.commands' +
        '&guild_id=848841415940898827&disable_guild_select=true',
    );
  });

  it('omits the guild parameters when no guild is supplied', () => {
    const url = inviteUrl({ applicationId: APPLICATION_ID });
    expect(url).toBe(
      'https://discord.com/oauth2/authorize?client_id=1573240846223151144' +
        '&permissions=3072&scope=bot%20applications.commands',
    );
    expect(url).not.toContain('guild_id=');
    expect(url).not.toContain('disable_guild_select');
  });

  it('rejects an empty application id', () => {
    expect(() => inviteUrl({ applicationId: '  ' })).toThrow(TypeError);
  });
});

describe('verifyDiscordOAuthToken', () => {
  it('returns the user and confirms home-guild membership', async () => {
    const fetchImpl = vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input);
      if (url.endsWith('/users/@me')) {
        return jsonResponse({ id: USER_ID, global_name: 'Void cat' });
      }
      if (url.endsWith('/users/@me/guilds')) {
        return jsonResponse([{ id: HOME_GUILD_ID }]);
      }
      return jsonResponse({}, 404);
    });

    await expect(
      verifyDiscordOAuthToken('oauth-token', {
        guildId: HOME_GUILD_ID,
        fetchImpl: fetchImpl as unknown as typeof fetch,
      }),
    ).resolves.toEqual({ id: USER_ID, username: 'Void cat' });

    expect(fetchImpl).toHaveBeenCalledTimes(2);
    const calls = fetchImpl.mock.calls as unknown as Array<[unknown, RequestInit?]>;
    const firstInit = calls[0]?.[1];
    expect((firstInit?.headers as Record<string, string>).Authorization).toBe(
      'Bearer oauth-token',
    );
  });

  it('returns the user when no guild check was requested', async () => {
    const fetchImpl = vi.fn(async () =>
      jsonResponse({ id: USER_ID, username: 'voidcat' }),
    );
    await expect(
      verifyDiscordOAuthToken('token', {
        fetchImpl: fetchImpl as unknown as typeof fetch,
      }),
    ).resolves.toEqual({ id: USER_ID, username: 'voidcat' });
    expect(fetchImpl).toHaveBeenCalledTimes(1);
  });

  it('rejects an empty token without calling Discord', async () => {
    const fetchImpl = vi.fn();
    await expect(
      verifyDiscordOAuthToken('   ', {
        fetchImpl: fetchImpl as unknown as typeof fetch,
      }),
    ).rejects.toMatchObject({
      name: 'DiscordAuthError',
      code: 'invalid_discord_token',
      statusCode: 401,
    });
    expect(fetchImpl).not.toHaveBeenCalled();
  });

  it('maps a rejected user token to invalid_discord_token', async () => {
    const fetchImpl = vi.fn(async () => new Response('', { status: 401 }));
    const error = await verifyDiscordOAuthToken('token', {
      fetchImpl: fetchImpl as unknown as typeof fetch,
    }).catch((caught: unknown) => caught);
    expect(error).toBeInstanceOf(DiscordAuthError);
    expect(error).toMatchObject({
      code: 'invalid_discord_token',
      statusCode: 401,
    });
  });

  it('maps rate limiting to discord_rate_limited', async () => {
    const fetchImpl = vi.fn(async () => new Response('', { status: 429 }));
    await expect(
      verifyDiscordOAuthToken('token', {
        fetchImpl: fetchImpl as unknown as typeof fetch,
      }),
    ).rejects.toMatchObject({
      code: 'discord_rate_limited',
      statusCode: 429,
    });
  });

  it('maps an unexpected Discord response to discord_unavailable', async () => {
    const fetchImpl = vi.fn(async () => new Response('', { status: 500 }));
    await expect(
      verifyDiscordOAuthToken('token', {
        fetchImpl: fetchImpl as unknown as typeof fetch,
      }),
    ).rejects.toMatchObject({
      code: 'discord_unavailable',
      statusCode: 503,
    });
  });

  it('maps a network failure to discord_unavailable', async () => {
    const fetchImpl = vi.fn(async () => {
      throw new Error('socket closed');
    });
    await expect(
      verifyDiscordOAuthToken('token', {
        fetchImpl: fetchImpl as unknown as typeof fetch,
      }),
    ).rejects.toMatchObject({
      code: 'discord_unavailable',
      statusCode: 503,
    });
  });

  it('maps a malformed user payload to invalid_discord_token', async () => {
    const fetchImpl = vi.fn(async () => jsonResponse({ id: 'not-a-snowflake' }));
    await expect(
      verifyDiscordOAuthToken('token', {
        fetchImpl: fetchImpl as unknown as typeof fetch,
      }),
    ).rejects.toMatchObject({
      code: 'invalid_discord_token',
      statusCode: 401,
    });
  });

  it('requires membership of the requested guild', async () => {
    const fetchImpl = vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input);
      if (url.endsWith('/users/@me')) {
        return jsonResponse({ id: USER_ID, username: 'voidcat' });
      }
      return jsonResponse([{ id: FOREIGN_GUILD_ID }]);
    });

    await expect(
      verifyDiscordOAuthToken('token', {
        guildId: HOME_GUILD_ID,
        fetchImpl: fetchImpl as unknown as typeof fetch,
      }),
    ).rejects.toMatchObject({
      code: 'guild_membership_required',
      statusCode: 403,
    });
  });

  it('treats a rejected guild list as an invalid token', async () => {
    const fetchImpl = vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input);
      if (url.endsWith('/users/@me')) {
        return jsonResponse({ id: USER_ID, username: 'voidcat' });
      }
      return new Response('', { status: 403 });
    });

    await expect(
      verifyDiscordOAuthToken('token', {
        guildId: HOME_GUILD_ID,
        fetchImpl: fetchImpl as unknown as typeof fetch,
      }),
    ).rejects.toMatchObject({
      code: 'invalid_discord_token',
      statusCode: 401,
    });
  });
});

describe('userIsGuildMember', () => {
  it('returns and caches a positive verdict', async () => {
    const fetchMember = vi.fn(async () => ({ id: USER_ID }));
    const client = makeClient({
      guilds: [
        makeGuild({
          members: { me: {}, fetch: fetchMember },
        }),
      ],
    });

    await expect(
      userIsGuildMember(client, HOME_GUILD_ID, USER_ID),
    ).resolves.toBe(true);
    await expect(
      userIsGuildMember(client, HOME_GUILD_ID, USER_ID),
    ).resolves.toBe(true);
    expect(fetchMember).toHaveBeenCalledTimes(1);
  });

  it('caches a 10007 not-a-member verdict', async () => {
    const fetchMember = vi.fn(async () => {
      throw Object.assign(new Error('Unknown Member'), { code: 10_007 });
    });
    const client = makeClient({
      guilds: [makeGuild({ members: { me: {}, fetch: fetchMember } })],
    });

    await expect(
      userIsGuildMember(client, HOME_GUILD_ID, USER_ID),
    ).resolves.toBe(false);
    await expect(
      userIsGuildMember(client, HOME_GUILD_ID, USER_ID),
    ).resolves.toBe(false);
    expect(fetchMember).toHaveBeenCalledTimes(1);
  });

  it('does not block delivery when Discord cannot answer', async () => {
    const fetchMember = vi.fn(async () => {
      throw new Error('gateway offline');
    });
    const client = makeClient({
      guilds: [makeGuild({ members: { me: {}, fetch: fetchMember } })],
    });

    await expect(
      userIsGuildMember(client, HOME_GUILD_ID, USER_ID),
    ).resolves.toBe(true);
  });

  it('does not block when the client is not ready', async () => {
    const fetchMember = vi.fn();
    const client = makeClient({
      ready: false,
      guilds: [makeGuild({ members: { me: {}, fetch: fetchMember } })],
    });

    await expect(
      userIsGuildMember(client, HOME_GUILD_ID, USER_ID),
    ).resolves.toBe(true);
    expect(fetchMember).not.toHaveBeenCalled();
  });

  it('rejects malformed ids without blocking on a malformed home guild', async () => {
    const fetchMember = vi.fn();
    const client = makeClient({
      guilds: [makeGuild({ members: { me: {}, fetch: fetchMember } })],
    });

    await expect(
      userIsGuildMember(client, HOME_GUILD_ID, 'not-a-user'),
    ).resolves.toBe(false);
    await expect(
      userIsGuildMember(client, 'not-a-guild', USER_ID),
    ).resolves.toBe(true);
    expect(fetchMember).not.toHaveBeenCalled();
  });
});

describe('buildReminderMessage', () => {
  it('formats the due date in the user timezone and includes every field', () => {
    const dueAt = Date.UTC(2025, 10, 12, 0, 0);
    const expectedDue = new Intl.DateTimeFormat('en-GB', {
      timeZone: 'Asia/Kolkata',
      dateStyle: 'medium',
      timeStyle: 'short',
    }).format(new Date(dueAt));

    const message = buildReminderMessage({
      title: 'Electricity bill',
      dueAt,
      merchant: 'Power Co',
      bank: 'HDFC',
      notes: 'Pay online before 6pm',
      timezone: 'Asia/Kolkata',
    });

    expect(message.title).toBe('Electricity bill');
    expect(message.body).toContain(`Due: ${expectedDue}`);
    expect(message.body).toContain('Merchant: Power Co');
    expect(message.body).toContain('Bank: HDFC');
    expect(message.body).toContain('Pay online before 6pm');
    expect(message.body.indexOf('Merchant:')).toBeLessThan(
      message.body.indexOf('Bank:'),
    );
  });

  it('omits empty optional fields', () => {
    const message = buildReminderMessage({
      title: 'Rent',
      dueAt: Date.UTC(2025, 0, 1, 9, 0),
      merchant: '   ',
      bank: '',
      notes: '',
      timezone: 'UTC',
    });
    expect(message.body).toBe('Due: 1 Jan 2025, 09:00');
  });

  it('falls back to a safe date label and a default title', () => {
    const message = buildReminderMessage({
      title: '   ',
      dueAt: 'not-a-date',
    });
    expect(message.title).toBe('Reminder');
    expect(message.body).toBe('Due: Unknown');
  });

  it('truncates the title and notes to their limits', () => {
    const message = buildReminderMessage({
      title: 't'.repeat(300),
      dueAt: Date.UTC(2025, 0, 1),
      notes: 'n'.repeat(2_000),
      timezone: 'UTC',
    });
    expect(message.title).toHaveLength(180);
    expect(message.body).toContain('n'.repeat(1_000));
    expect(message.body).not.toContain('n'.repeat(1_001));
  });
});

describe('selectFallbackChannel', () => {
  it('prefers a named notification channel the bot can post in', () => {
    const general = makeChannel({ id: '111111111111111111', name: 'general' });
    const alerts = makeChannel({
      id: '222222222222222222',
      name: PREFERRED_FALLBACK_CHANNEL_NAMES[0] ?? 'alerts',
    });
    const client = makeClient({
      guilds: [
        makeGuild({
          channels: { cache: channelsCollection(general, alerts) },
        }),
      ],
    });

    expect(selectFallbackChannel(client, HOME_GUILD_ID)).toBe(alerts.id);
  });

  it('skips channels without ViewChannel or SendMessages', () => {
    const denied = makeChannel({
      id: '111111111111111111',
      name: 'alerts',
      permissionsFor: () => ({ has: () => false }),
    });
    const allowed = makeChannel({ id: '222222222222222222', name: 'general' });
    const client = makeClient({
      guilds: [
        makeGuild({
          channels: { cache: channelsCollection(denied, allowed) },
        }),
      ],
    });

    expect(selectFallbackChannel(client, HOME_GUILD_ID)).toBe(allowed.id);
  });

  it('skips threads and non-text channels', () => {
    const thread = makeChannel({
      id: '111111111111111111',
      name: 'alerts',
      isThread: () => true,
    });
    const voice = makeChannel({
      id: '222222222222222222',
      name: 'alerts',
      isTextBased: () => false,
    });
    const text = makeChannel({ id: '333333333333333333', name: 'general' });
    const client = makeClient({
      guilds: [
        makeGuild({
          channels: { cache: channelsCollection(thread, voice, text) },
        }),
      ],
    });

    expect(selectFallbackChannel(client, HOME_GUILD_ID)).toBe(text.id);
  });

  it('returns an explicit channel that belongs to the home guild', () => {
    const channel = makeChannel({ id: '111111111111111111', guildId: HOME_GUILD_ID });
    const client = makeClient({ channels: [channel] });
    expect(
      selectFallbackChannel(client, HOME_GUILD_ID, channel.id),
    ).toBe(channel.id);
  });

  it('rejects an explicit channel known to belong to another guild', () => {
    const channel = makeChannel({
      id: '111111111111111111',
      guildId: FOREIGN_GUILD_ID,
    });
    const client = makeClient({ channels: [channel] });
    expect(
      selectFallbackChannel(client, HOME_GUILD_ID, channel.id),
    ).toBeNull();
  });

  it('returns null without a guild or a usable channel', () => {
    expect(selectFallbackChannel(makeClient(), HOME_GUILD_ID)).toBeNull();
    expect(selectFallbackChannel(makeClient(), 'not-a-guild')).toBeNull();
  });
});

describe('sendDiscordReminder', () => {
  it('delivers a DM and disables mention parsing', async () => {
    const send = vi.fn(async () => ({}));
    const client = makeClient({
      users: { fetch: async () => ({ send }) },
    });

    await expect(
      sendDiscordReminder(client, {
        userId: USER_ID,
        title: 'Bill',
        body: 'Due soon',
      }),
    ).resolves.toEqual({ kind: 'dm' });

    expect(send).toHaveBeenCalledWith({
      content: 'Bill\n\nDue soon',
      allowedMentions: { parse: [] },
    });
  });

  it('falls back to a channel mention when Discord returns 50007', async () => {
    const userSend = vi.fn(async () => {
      throw Object.assign(new Error('Cannot send messages to this user'), {
        code: 50_007,
      });
    });
    const channelSend = vi.fn(async () => ({}));
    const channel = makeChannel({
      id: '222222222222222222',
      guildId: HOME_GUILD_ID,
      send: channelSend,
    });
    const client = makeClient({
      users: { fetch: async () => ({ send: userSend }) },
      channels: [channel],
    });

    await expect(
      sendDiscordReminder(client, {
        userId: USER_ID,
        title: 'Bill',
        body: 'Due soon',
        fallbackChannelId: channel.id,
      }),
    ).resolves.toEqual({ kind: 'fallback', channelId: channel.id });

    expect(channelSend).toHaveBeenCalledWith({
      content: `<@${USER_ID}>\nBill\n\nDue soon`,
      allowedMentions: { parse: ['users'], users: [USER_ID] },
    });
  });

  it('falls back on a 403 as well', async () => {
    const channel = makeChannel({ id: '222222222222222222' });
    const client = makeClient({
      users: {
        fetch: async () => ({
          send: async () => {
            throw Object.assign(new Error('Forbidden'), { status: 403 });
          },
        }),
      },
      channels: [channel],
    });

    await expect(
      sendDiscordReminder(client, {
        userId: USER_ID,
        title: 'Bill',
        body: 'Due soon',
        fallbackChannelId: channel.id,
      }),
    ).resolves.toEqual({ kind: 'fallback', channelId: channel.id });
  });

  it('auto-selects the preferred fallback channel in the home guild', async () => {
    const channel = makeChannel({
      id: '222222222222222222',
      name: 'ei-snore-alerts',
    });
    const client = makeClient({
      users: {
        fetch: async () => ({
          send: async () => {
            throw Object.assign(new Error('blocked'), { code: 50_007 });
          },
        }),
      },
      guilds: [
        makeGuild({ channels: { cache: channelsCollection(channel) } }),
      ],
    });

    await expect(
      sendDiscordReminder(client, {
        userId: USER_ID,
        title: 'Bill',
        body: 'Due soon',
        guildId: HOME_GUILD_ID,
      }),
    ).resolves.toEqual({ kind: 'fallback', channelId: channel.id });
  });

  it('returns a classified error when DMs are blocked and no fallback exists', async () => {
    const client = makeClient({
      users: {
        fetch: async () => ({
          send: async () => {
            throw Object.assign(new Error('blocked'), { code: 50_007 });
          },
        }),
      },
    });

    await expect(
      sendDiscordReminder(client, {
        userId: USER_ID,
        title: 'Bill',
        body: 'Due soon',
      }),
    ).resolves.toEqual({
      kind: 'error',
      status: 403,
      code: 'discord_dm_blocked',
      fallbackAttempted: false,
    });
  });

  it('refuses delivery to a user who left the home guild', async () => {
    const send = vi.fn(async () => ({}));
    const client = makeClient({
      guilds: [
        makeGuild({
          members: {
            me: {},
            fetch: async () => {
              throw Object.assign(new Error('Unknown Member'), { code: 10_007 });
            },
          },
        }),
      ],
      users: { fetch: async () => ({ send }) },
    });

    await expect(
      sendDiscordReminder(client, {
        userId: USER_ID,
        title: 'Bill',
        body: 'Due soon',
        guildId: HOME_GUILD_ID,
      }),
    ).resolves.toEqual({
      kind: 'error',
      status: 403,
      code: 'guild_membership_required',
      fallbackAttempted: false,
    });
    expect(send).not.toHaveBeenCalled();
  });

  it('does not attempt a fallback for rate limits', async () => {
    const channelSend = vi.fn(async () => ({}));
    const channel = makeChannel({
      id: '222222222222222222',
      send: channelSend,
    });
    const client = makeClient({
      users: {
        fetch: async () => ({
          send: async () => {
            throw Object.assign(new Error('rate limited'), { status: 429 });
          },
        }),
      },
      channels: [channel],
    });

    await expect(
      sendDiscordReminder(client, {
        userId: USER_ID,
        title: 'Bill',
        body: 'Due soon',
        fallbackChannelId: channel.id,
      }),
    ).resolves.toEqual({
      kind: 'error',
      status: 429,
      code: 'discord_rate_limited',
      fallbackAttempted: false,
    });
    expect(channelSend).not.toHaveBeenCalled();
  });

  it('reports a failed fallback distinctly', async () => {
    const client = makeClient({
      users: {
        fetch: async () => ({
          send: async () => {
            throw Object.assign(new Error('blocked'), { code: 50_007 });
          },
        }),
      },
      channels: [
        makeChannel({
          id: '222222222222222222',
          send: async () => {
            throw Object.assign(new Error('missing access'), { status: 500 });
          },
        }),
      ],
    });

    await expect(
      sendDiscordReminder(client, {
        userId: USER_ID,
        title: 'Bill',
        body: 'Due soon',
        fallbackChannelId: '222222222222222222',
      }),
    ).resolves.toEqual({
      kind: 'error',
      status: 502,
      code: 'discord_fallback_failed',
      fallbackAttempted: true,
    });
  });

  it('rejects a malformed recipient without touching Discord', async () => {
    const fetchUser = vi.fn();
    const client = makeClient({ users: { fetch: fetchUser } });
    await expect(
      sendDiscordReminder(client, {
        userId: 'not-a-user',
        title: 'Bill',
        body: 'Due soon',
      }),
    ).resolves.toEqual({
      kind: 'error',
      status: 400,
      code: 'invalid_recipient',
      fallbackAttempted: false,
    });
    expect(fetchUser).not.toHaveBeenCalled();
  });
});

describe('leaveForeignGuilds', () => {
  it('leaves foreign guilds, keeps home and reports failures', async () => {
    const homeLeave = vi.fn(async () => undefined);
    const foreignLeave = vi.fn(async () => undefined);
    const failingLeave = vi.fn(async () => {
      throw new Error('missing access');
    });
    const client = makeClient({
      guilds: [
        makeGuild({ id: HOME_GUILD_ID, leave: homeLeave }),
        makeGuild({ id: FOREIGN_GUILD_ID, leave: foreignLeave }),
        makeGuild({ id: '888888888888888888', leave: failingLeave }),
      ],
    });

    await expect(
      leaveForeignGuilds(client, HOME_GUILD_ID),
    ).resolves.toEqual({
      left: [FOREIGN_GUILD_ID],
      failed: ['888888888888888888'],
    });
    expect(homeLeave).not.toHaveBeenCalled();
    expect(foreignLeave).toHaveBeenCalledTimes(1);
  });

  it('does nothing when the home id is malformed', async () => {
    const leave = vi.fn(async () => undefined);
    const client = makeClient({ guilds: [makeGuild({ leave })] });
    await expect(leaveForeignGuilds(client, 'not-a-guild')).resolves.toEqual({
      left: [],
      failed: [],
    });
    expect(leave).not.toHaveBeenCalled();
  });

  it('reports guilds that cannot be left without throwing', async () => {
    const client = makeClient({
      guilds: [makeGuild({ id: FOREIGN_GUILD_ID, leave: undefined })],
    });
    await expect(
      leaveForeignGuilds(client, HOME_GUILD_ID),
    ).resolves.toEqual({
      left: [],
      failed: [FOREIGN_GUILD_ID],
    });
  });
});
