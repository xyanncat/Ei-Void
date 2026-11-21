import { describe, expect, it, vi } from 'vitest';
import {
  createMailer,
  detectMailProvider,
  type MailerConfigInput,
} from '../src/index.js';

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  });
}

const gmailConfig: MailerConfigInput = {
  from: 'sender@example.com',
  fromName: 'Ei Void',
  gmail: {
    clientId: 'gmail-client-id',
    clientSecret: 'gmail-client-secret',
    refreshToken: 'gmail-refresh-token',
  },
};

describe('detectMailProvider', () => {
  it('prefers a fully configured Gmail account', () => {
    expect(
      detectMailProvider({ ...gmailConfig, brevoApiKey: 'brevo-key' }),
    ).toBe('gmail');
  });

  it('honours an explicit provider over the detection order', () => {
    expect(
      detectMailProvider({
        ...gmailConfig,
        provider: 'brevo',
        brevoApiKey: 'brevo-key',
      }),
    ).toBe('brevo');
  });

  it('does not fall through when the explicit provider is incomplete', () => {
    expect(
      detectMailProvider({
        ...gmailConfig,
        provider: 'brevo',
        brevoApiKey: '',
      }),
    ).toBeNull();
  });

  it('normalises the explicit provider value', () => {
    expect(
      detectMailProvider({
        from: 'sender@example.com',
        provider: '  GMAIL ',
        ...gmailConfig,
      }),
    ).toBe('gmail');
  });

  it('falls back to Brevo, then SendGrid, then SMTP', () => {
    const base = { from: 'sender@example.com' };
    expect(detectMailProvider({ ...base, brevoApiKey: 'brevo-key' })).toBe('brevo');
    expect(detectMailProvider({ ...base, sendgridApiKey: 'sendgrid-key' })).toBe(
      'sendgrid',
    );
    expect(
      detectMailProvider({
        ...base,
        smtp: { host: 'smtp.example.com', user: 'user', pass: 'pass' },
      }),
    ).toBe('smtp');
  });

  it('prefers Brevo over SendGrid and SMTP', () => {
    expect(
      detectMailProvider({
        from: 'sender@example.com',
        brevoApiKey: 'brevo-key',
        sendgridApiKey: 'sendgrid-key',
        smtp: { host: 'smtp.example.com', user: 'user', pass: 'pass' },
      }),
    ).toBe('brevo');
  });

  it('ignores an unknown explicit provider and auto-detects', () => {
    expect(
      detectMailProvider({
        from: 'sender@example.com',
        provider: 'mailgun',
        sendgridApiKey: 'sendgrid-key',
      }),
    ).toBe('sendgrid');
  });

  it('requires a sender address for every provider', () => {
    expect(
      detectMailProvider({
        gmail: gmailConfig.gmail,
      }),
    ).toBeNull();
  });

  it('returns null when nothing is configured', () => {
    expect(detectMailProvider({})).toBeNull();
  });

  it('accepts flat env-shaped configuration', () => {
    expect(
      detectMailProvider({
        mailFrom: 'sender@example.com',
        mailProvider: 'sendgrid',
        sendgridApiKey: 'sendgrid-key',
      }),
    ).toBe('sendgrid');
  });
});

describe('createMailer', () => {
  it('reports email_not_configured instead of throwing', async () => {
    const fetchImpl = vi.fn();
    const mailer = createMailer({}, { fetchImpl: fetchImpl as unknown as typeof fetch });

    expect(mailer.provider).toBeNull();
    await expect(
      mailer.send({ to: 'user@example.com', subject: 'Hi', text: 'There' }),
    ).resolves.toEqual({
      ok: false,
      status: 503,
      error: 'email_not_configured',
    });
    expect(fetchImpl).not.toHaveBeenCalled();
  });

  it('rejects an invalid recipient without calling a provider', async () => {
    const fetchImpl = vi.fn();
    const mailer = createMailer(gmailConfig, {
      fetchImpl: fetchImpl as unknown as typeof fetch,
    });

    await expect(
      mailer.send({ to: 'not-an-email', subject: 'Hi', text: 'There' }),
    ).resolves.toEqual({ ok: false, status: 400, error: 'invalid_email' });
    expect(fetchImpl).not.toHaveBeenCalled();
  });

  it('rejects an address containing whitespace or header separators', async () => {
    const fetchImpl = vi.fn();
    const mailer = createMailer(gmailConfig, {
      fetchImpl: fetchImpl as unknown as typeof fetch,
    });

    for (const to of ['user @example.com', 'user@example.com\r\nBcc: x@y.z', 'a,b@example.com']) {
      await expect(
        mailer.send({ to, subject: 'Hi', text: 'There' }),
      ).resolves.toMatchObject({ ok: false, status: 400, error: 'invalid_email' });
    }
    expect(fetchImpl).not.toHaveBeenCalled();
  });

  it('sends through the Gmail REST API with the expected request shape', async () => {
    const fetchImpl = vi.fn(
      async (input: RequestInfo | URL, init?: RequestInit): Promise<Response> => {
        if (String(input).includes('oauth2.googleapis.com/token')) {
          expect(init?.method).toBe('POST');
          expect(init?.headers).toEqual({
            'Content-Type': 'application/x-www-form-urlencoded',
          });
          const body = init?.body as URLSearchParams;
          expect(body.get('grant_type')).toBe('refresh_token');
          expect(body.get('client_id')).toBe('gmail-client-id');
          expect(body.get('client_secret')).toBe('gmail-client-secret');
          expect(body.get('refresh_token')).toBe('gmail-refresh-token');
          return jsonResponse({ access_token: 'gmail-access-token', expires_in: 3600 });
        }
        return new Response('', { status: 200 });
      },
    );
    const mailer = createMailer(gmailConfig, {
      fetchImpl: fetchImpl as unknown as typeof fetch,
    });

    await expect(
      mailer.send({
        to: 'User@Example.com',
        subject: 'Electricity bill',
        text: 'Pay by Friday.',
      }),
    ).resolves.toEqual({ ok: true, provider: 'gmail' });

    expect(fetchImpl).toHaveBeenCalledTimes(2);
    const [sendUrl, sendInit] = fetchImpl.mock.calls[1] ?? [];
    expect(String(sendUrl)).toBe(
      'https://gmail.googleapis.com/gmail/v1/users/me/messages/send',
    );
    expect(sendInit?.method).toBe('POST');
    expect(
      (sendInit?.headers as Record<string, string>).Authorization,
    ).toBe('Bearer gmail-access-token');
    expect((sendInit?.headers as Record<string, string>)['Content-Type']).toBe(
      'application/json',
    );

    const payload = JSON.parse(String(sendInit?.body)) as { raw: string };
    const raw = Buffer.from(payload.raw, 'base64url').toString('utf8');
    expect(raw).toContain('From: Ei Void <sender@example.com>');
    expect(raw).toContain('To: user@example.com');
    expect(raw).toContain('Subject: Electricity bill');
    expect(raw).toContain('Content-Type: text/plain; charset="UTF-8"');
    expect(raw).toContain('Content-Transfer-Encoding: base64');
  });

  it('refreshes a Gmail token once and retries after a 401', async () => {
    let oauthCalls = 0;
    let sendCalls = 0;
    const fetchImpl = vi.fn(
      async (input: RequestInfo | URL): Promise<Response> => {
        if (String(input).includes('oauth2.googleapis.com/token')) {
          oauthCalls += 1;
          return jsonResponse({
            access_token: `gmail-token-${oauthCalls}`,
            expires_in: 3600,
          });
        }
        sendCalls += 1;
        return new Response('', { status: sendCalls === 1 ? 401 : 200 });
      },
    );
    const mailer = createMailer(gmailConfig, {
      fetchImpl: fetchImpl as unknown as typeof fetch,
    });

    await expect(
      mailer.send({ to: 'user@example.com', subject: 'Hi', text: 'There' }),
    ).resolves.toEqual({ ok: true, provider: 'gmail' });

    expect(oauthCalls).toBe(2);
    expect(sendCalls).toBe(2);
    const calls = fetchImpl.mock.calls as unknown as Array<[unknown, RequestInit?]>;
    const secondSend = calls[3]?.[1];
    expect(
      (secondSend?.headers as Record<string, string>).Authorization,
    ).toBe('Bearer gmail-token-2');
  });

  it('caches the Gmail access token between sends', async () => {
    let oauthCalls = 0;
    const fetchImpl = vi.fn(
      async (input: RequestInfo | URL): Promise<Response> => {
        if (String(input).includes('oauth2.googleapis.com/token')) {
          oauthCalls += 1;
          return jsonResponse({ access_token: 'cached-token', expires_in: 3600 });
        }
        return new Response('', { status: 200 });
      },
    );
    const mailer = createMailer(gmailConfig, {
      fetchImpl: fetchImpl as unknown as typeof fetch,
    });

    await mailer.send({ to: 'one@example.com', subject: 'One', text: 'One' });
    await mailer.send({ to: 'two@example.com', subject: 'Two', text: 'Two' });

    expect(oauthCalls).toBe(1);
    expect(fetchImpl).toHaveBeenCalledTimes(3);
  });

  it('returns a generic failure when the Gmail API rejects the message', async () => {
    const fetchImpl = vi.fn(
      async (input: RequestInfo | URL): Promise<Response> => {
        if (String(input).includes('oauth2.googleapis.com/token')) {
          return jsonResponse({ access_token: 'token', expires_in: 3600 });
        }
        return new Response('provider detail that must not leak', { status: 500 });
      },
    );
    const mailer = createMailer(gmailConfig, {
      fetchImpl: fetchImpl as unknown as typeof fetch,
    });

    const result = await mailer.send({
      to: 'user@example.com',
      subject: 'Hi',
      text: 'There',
    });
    expect(result).toEqual({
      ok: false,
      status: 502,
      error: 'email_send_failed',
    });
    expect(JSON.stringify(result)).not.toContain('provider detail');
  });

  it('sends through Brevo with the expected request shape', async () => {
    const fetchImpl = vi.fn(
      async (_input: RequestInfo | URL, _init?: RequestInit): Promise<Response> =>
        new Response('', { status: 201 }),
    );
    const mailer = createMailer(
      {
        from: 'sender@example.com',
        fromName: 'Ei Void',
        brevoApiKey: 'brevo-secret',
      },
      { fetchImpl: fetchImpl as unknown as typeof fetch },
    );

    await expect(
      mailer.send({ to: 'user@example.com', subject: 'Bill', text: 'Due soon' }),
    ).resolves.toEqual({ ok: true, provider: 'brevo' });

    expect(fetchImpl).toHaveBeenCalledTimes(1);
    const [url, init] = fetchImpl.mock.calls[0] ?? [];
    expect(String(url)).toBe('https://api.brevo.com/v3/smtp/email');
    expect(init?.method).toBe('POST');
    const headers = init?.headers as Record<string, string>;
    expect(headers['api-key']).toBe('brevo-secret');
    expect(headers.Accept).toBe('application/json');
    expect(JSON.parse(String(init?.body))).toEqual({
      sender: { email: 'sender@example.com', name: 'Ei Void' },
      to: [{ email: 'user@example.com' }],
      subject: 'Bill',
      textContent: 'Due soon',
    });
  });

  it('sends through SendGrid with the expected request shape', async () => {
    const fetchImpl = vi.fn(
      async (_input: RequestInfo | URL, _init?: RequestInit): Promise<Response> =>
        new Response('', { status: 202 }),
    );
    const mailer = createMailer(
      { from: 'sender@example.com', sendgridApiKey: 'sendgrid-secret' },
      { fetchImpl: fetchImpl as unknown as typeof fetch },
    );

    await expect(
      mailer.send({ to: 'user@example.com', subject: 'Bill', text: 'Due soon' }),
    ).resolves.toEqual({ ok: true, provider: 'sendgrid' });

    expect(fetchImpl).toHaveBeenCalledTimes(1);
    const [url, init] = fetchImpl.mock.calls[0] ?? [];
    expect(String(url)).toBe('https://api.sendgrid.com/v3/mail/send');
    expect(init?.method).toBe('POST');
    expect((init?.headers as Record<string, string>).Authorization).toBe(
      'Bearer sendgrid-secret',
    );
    expect(JSON.parse(String(init?.body))).toEqual({
      personalizations: [{ to: [{ email: 'user@example.com' }] }],
      from: { email: 'sender@example.com', name: 'Ei Void' },
      subject: 'Bill',
      content: [{ type: 'text/plain', value: 'Due soon' }],
    });
  });

  it('caps the subject and body before handing them to a provider', async () => {
    const fetchImpl = vi.fn(
      async (_input: RequestInfo | URL, _init?: RequestInit): Promise<Response> =>
        new Response('', { status: 201 }),
    );
    const mailer = createMailer(
      { from: 'sender@example.com', brevoApiKey: 'brevo-secret' },
      { fetchImpl: fetchImpl as unknown as typeof fetch },
    );

    await mailer.send({
      to: 'user@example.com',
      subject: 's'.repeat(500),
      text: 'b'.repeat(10_000),
    });

    const body = JSON.parse(String(fetchImpl.mock.calls[0]?.[1]?.body)) as {
      subject: string;
      textContent: string;
    };
    expect(body.subject).toHaveLength(180);
    expect(body.textContent).toHaveLength(4_000);
  });

  it('fills safe defaults for an empty subject and body', async () => {
    const fetchImpl = vi.fn(
      async (_input: RequestInfo | URL, _init?: RequestInit): Promise<Response> =>
        new Response('', { status: 201 }),
    );
    const mailer = createMailer(
      { from: 'sender@example.com', brevoApiKey: 'brevo-secret' },
      { fetchImpl: fetchImpl as unknown as typeof fetch },
    );

    await mailer.send({ to: 'user@example.com', subject: '   ', text: '' });

    const body = JSON.parse(String(fetchImpl.mock.calls[0]?.[1]?.body)) as {
      subject: string;
      textContent: string;
    };
    expect(body.subject).toBe('Ei Void reminder');
    expect(body.textContent).toBe('Reminder due.');
  });
});
