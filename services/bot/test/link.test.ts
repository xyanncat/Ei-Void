import { describe, expect, it, vi } from 'vitest';
import type { LinkCode } from '@eivoid/db';
import { handleLink, LINK_CODE_TTL_MS } from '../src/link.js';
import { DISCORD_USER_ID, makeInteraction } from './fixtures.js';

const code: LinkCode = {
  code: 'abc123def456',
  discordUserId: DISCORD_USER_ID,
  discordUsername: 'voidcat',
  createdAt: new Date('2026-01-01T00:00:00.000Z'),
  expiresAt: new Date('2026-01-01T00:10:00.000Z'),
  usedAt: null,
};

describe('handleLink', () => {
  it('creates a ten-minute code and returns the website URL', async () => {
    const createLinkCode = vi.fn(async () => code);
    const store = { createLinkCode };
    const { interaction, editReply, reply } = makeInteraction();

    await handleLink(interaction, store, { webBaseUrl: 'https://void.example.com' });

    expect(createLinkCode).toHaveBeenCalledWith({
      discordUserId: DISCORD_USER_ID,
      discordUsername: 'voidcat',
      ttlMs: LINK_CODE_TTL_MS,
    });
    expect(LINK_CODE_TTL_MS).toBe(600_000);
    expect(reply).not.toHaveBeenCalled();
    const content = String((editReply.mock.calls[0]?.[0] as { content?: string }).content);
    expect(content).toContain('https://void.example.com/link?code=abc123def456');
    expect(content).toContain('expires in 10 minutes');
  });

  it('refuses without a public website URL', async () => {
    const createLinkCode = vi.fn(async () => code);
    const { interaction, editReply } = makeInteraction();

    await handleLink(interaction, { createLinkCode }, { webBaseUrl: '' });

    expect(createLinkCode).not.toHaveBeenCalled();
    const content = String((editReply.mock.calls[0]?.[0] as { content?: string }).content);
    expect(content).toContain('not configured');
  });
});
