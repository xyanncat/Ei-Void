import nodemailer from 'nodemailer';
import type {
  GmailConfig,
  MailConfig,
  MailProvider,
  SmtpConfig,
} from '@eivoid/config';

export type { MailConfig, MailProvider } from '@eivoid/config';

/**
 * Outbound email for Ei Void.
 *
 * The transport is chosen once, at creation time, from the supplied
 * configuration:
 *
 *   1. an explicit `provider` (the `MAIL_PROVIDER` value),
 *   2. Gmail REST API,
 *   3. Brevo HTTPS API,
 *   4. SendGrid HTTPS API,
 *   5. SMTP through nodemailer.
 *
 * Gmail, Brevo and SendGrid speak HTTPS, so they work on hosts that block
 * outbound SMTP. Nothing in this module logs credentials, recipient addresses
 * or message contents; failures are returned as generic error codes.
 */

/**
 * A relaxed view of `MailConfig` used by `createMailer` and provider
 * detection. The strict `MailConfig` from `@eivoid/config` is assignable to
 * this shape; partial objects are accepted so tooling and tests can probe a
 * single provider without inventing credentials for the others.
 */
export interface MailerConfigInput {
  /** Sender address (`MAIL_FROM`). Required for every provider. */
  readonly from?: string;
  /** Alias for `from` used by env-shaped configs. */
  readonly mailFrom?: string;
  /** Sender display name (`MAIL_FROM_NAME`). */
  readonly fromName?: string;
  /** Alias for `fromName` used by env-shaped configs. */
  readonly mailFromName?: string;
  /** Explicit provider selection (`MAIL_PROVIDER`); null means auto-detect. */
  readonly provider?: MailProvider | string | null;
  /** Alias for `provider` used by env-shaped configs. */
  readonly mailProvider?: MailProvider | string | null;

  readonly gmail?: Partial<GmailConfig>;
  readonly gmailClientId?: string;
  readonly gmailClientSecret?: string;
  readonly gmailRefreshToken?: string;

  readonly brevoApiKey?: string;
  readonly sendgridApiKey?: string;

  readonly smtp?: Partial<SmtpConfig> & { readonly secure?: boolean };
  readonly smtpHost?: string;
  readonly smtpPort?: number;
  readonly smtpUser?: string;
  readonly smtpPass?: string;
}

export interface MailMessage {
  readonly to: string;
  readonly subject?: string;
  readonly text?: string;
}

export type MailErrorCode =
  | 'email_not_configured'
  | 'invalid_email'
  | 'email_send_failed';

export type MailResult =
  | { readonly ok: true; readonly provider: MailProvider }
  | { readonly ok: false; readonly status: number; readonly error: MailErrorCode };

export interface Mailer {
  /** The detected provider, or null when no provider is fully configured. */
  readonly provider: MailProvider | null;
  send(message: MailMessage): Promise<MailResult>;
}

export interface MailerDependencies {
  /** Injectable fetch for tests; defaults to the global fetch. */
  readonly fetchImpl?: typeof fetch;
}

const GMAIL_TOKEN_URL = 'https://oauth2.googleapis.com/token';
const GMAIL_SEND_URL =
  'https://gmail.googleapis.com/gmail/v1/users/me/messages/send';
const BREVO_SEND_URL = 'https://api.brevo.com/v3/smtp/email';
const SENDGRID_SEND_URL = 'https://api.sendgrid.com/v3/mail/send';

const DEFAULT_FROM_NAME = 'Ei Void';
const DEFAULT_SUBJECT = 'Ei Void reminder';
const DEFAULT_BODY = 'Reminder due.';
const MAX_SUBJECT_LENGTH = 180;
const MAX_BODY_LENGTH = 4_000;
const MAX_ADDRESS_LENGTH = 254;
const REQUEST_TIMEOUT_MS = 15_000;
const TOKEN_EXPIRY_MARGIN_MS = 60_000;

const EMAIL_PATTERN = /^[^\s@,;<>]+@[^\s@,;<>]+\.[^\s@,;<>]+$/;

function asText(value: unknown): string {
  if (typeof value === 'string') return value;
  if (value === null || value === undefined) return '';
  return String(value);
}

function trimmed(value: unknown): string {
  return asText(value).trim();
}

function isMailProvider(value: string): value is MailProvider {
  return value === 'gmail' || value === 'brevo' || value === 'sendgrid' || value === 'smtp';
}

interface ResolvedMailSettings {
  readonly from: string;
  readonly fromName: string;
  readonly gmail: GmailConfig | null;
  readonly brevoApiKey: string;
  readonly sendgridApiKey: string;
  readonly smtp: (SmtpConfig & { readonly secure: boolean }) | null;
}

function resolveMailSettings(
  config: MailConfig | MailerConfigInput,
): ResolvedMailSettings {
  // The strict `MailConfig` from @eivoid/config is assignable to
  // `MailerConfigInput`; the cast only exposes the relaxed alias fields.
  const input = config as MailerConfigInput;
  const from = trimmed(input.from ?? input.mailFrom);
  const fromName = trimmed(input.fromName ?? input.mailFromName) || DEFAULT_FROM_NAME;

  const gmailSource = input.gmail ?? {};
  const gmailClientId = trimmed(gmailSource.clientId ?? input.gmailClientId);
  const gmailClientSecret = trimmed(
    gmailSource.clientSecret ?? input.gmailClientSecret,
  );
  const gmailRefreshToken = trimmed(
    gmailSource.refreshToken ?? input.gmailRefreshToken,
  );
  const gmail =
    gmailClientId && gmailClientSecret && gmailRefreshToken
      ? {
          clientId: gmailClientId,
          clientSecret: gmailClientSecret,
          refreshToken: gmailRefreshToken,
        }
      : null;

  const smtpSource = input.smtp ?? {};
  const smtpHost = trimmed(smtpSource.host ?? input.smtpHost);
  const smtpUser = trimmed(smtpSource.user ?? input.smtpUser);
  const smtpPass = trimmed(smtpSource.pass ?? input.smtpPass);
  const rawPort = Number(smtpSource.port ?? input.smtpPort ?? 465);
  const smtpPort = Number.isInteger(rawPort) && rawPort > 0 ? rawPort : 465;
  const smtpSecure =
    'secure' in smtpSource && typeof smtpSource.secure === 'boolean'
      ? smtpSource.secure
      : smtpPort === 465;
  const smtp =
    smtpHost && smtpUser && smtpPass
      ? {
          host: smtpHost,
          port: smtpPort,
          user: smtpUser,
          pass: smtpPass,
          secure: smtpSecure,
        }
      : null;

  return {
    from,
    fromName,
    gmail,
    brevoApiKey: trimmed(input.brevoApiKey),
    sendgridApiKey: trimmed(input.sendgridApiKey),
    smtp,
  };
}

function isConfigured(provider: MailProvider, settings: ResolvedMailSettings): boolean {
  if (!settings.from) return false;
  switch (provider) {
    case 'gmail':
      return settings.gmail !== null;
    case 'brevo':
      return settings.brevoApiKey.length > 0;
    case 'sendgrid':
      return settings.sendgridApiKey.length > 0;
    case 'smtp':
      return settings.smtp !== null;
    default:
      return false;
  }
}

/**
 * Resolves the provider exactly as the Ei Snore backend did: a recognised
 * explicit provider is authoritative (and yields null when it is missing
 * credentials); otherwise the first fully configured HTTPS provider wins,
 * with SMTP last.
 */
export function detectMailProvider(
  config: MailConfig | MailerConfigInput,
): MailProvider | null {
  const settings = resolveMailSettings(config);
  const input = config as MailerConfigInput;
  const explicit = trimmed(input.provider ?? input.mailProvider).toLowerCase();

  if (isMailProvider(explicit)) {
    return isConfigured(explicit, settings) ? explicit : null;
  }

  for (const provider of ['gmail', 'brevo', 'sendgrid', 'smtp'] as const) {
    if (isConfigured(provider, settings)) return provider;
  }
  return null;
}

function encodeMailHeader(value: string): string {
  // Headers containing CR, LF or other non-ASCII characters are encoded as a
  // whole, which also removes any header-injection surface.
  if (/^[\x20-\x7E]*$/.test(value)) return value;
  return `=?UTF-8?B?${Buffer.from(value, 'utf8').toString('base64')}?=`;
}

function base64Lines(value: string): string {
  return Buffer.from(value, 'utf8')
    .toString('base64')
    .replace(/(.{76})/g, '$1\r\n');
}

function buildGmailRaw(
  from: string,
  fromName: string,
  to: string,
  subject: string,
  body: string,
): string {
  const message = [
    `From: ${encodeMailHeader(fromName)} <${from}>`,
    `To: ${to}`,
    `Subject: ${encodeMailHeader(subject)}`,
    'MIME-Version: 1.0',
    'Content-Type: text/plain; charset="UTF-8"',
    'Content-Transfer-Encoding: base64',
    '',
    base64Lines(body),
  ].join('\r\n');
  return Buffer.from(message, 'utf8').toString('base64url');
}

function isValidAddress(address: string): boolean {
  return (
    address.length > 0 &&
    address.length <= MAX_ADDRESS_LENGTH &&
    EMAIL_PATTERN.test(address)
  );
}

function normalizeSubject(subject: unknown): string {
  return (trimmed(subject) || DEFAULT_SUBJECT).slice(0, MAX_SUBJECT_LENGTH);
}

function normalizeBody(text: unknown): string {
  return (trimmed(text) || DEFAULT_BODY).slice(0, MAX_BODY_LENGTH);
}

function createSmtpTransport(settings: ResolvedMailSettings) {
  const smtp = settings.smtp;
  if (!smtp) throw new Error('smtp_not_configured');
  return nodemailer.createTransport({
    host: smtp.host,
    port: smtp.port,
    secure: smtp.secure,
    connectionTimeout: 10_000,
    greetingTimeout: 10_000,
    socketTimeout: 20_000,
    auth: {
      user: smtp.user,
      pass: smtp.pass,
    },
  });
}

interface GmailTokenCache {
  value: string;
  expiresAt: number;
}

export function createMailer(
  config: MailConfig | MailerConfigInput,
  dependencies: MailerDependencies = {},
): Mailer {
  const settings = resolveMailSettings(config);
  const provider = detectMailProvider(config);
  const fetchImpl = dependencies.fetchImpl ?? fetch;

  let tokenCache: GmailTokenCache | null = null;
  let smtpTransport: ReturnType<typeof nodemailer.createTransport> | null = null;

  async function gmailAccessToken(forceRefresh = false): Promise<string> {
    if (
      !forceRefresh &&
      tokenCache !== null &&
      Date.now() < tokenCache.expiresAt - TOKEN_EXPIRY_MARGIN_MS
    ) {
      return tokenCache.value;
    }
    const gmail = settings.gmail;
    if (!gmail) throw new Error('gmail_not_configured');

    const response = await fetchImpl(GMAIL_TOKEN_URL, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: new URLSearchParams({
        client_id: gmail.clientId,
        client_secret: gmail.clientSecret,
        refresh_token: gmail.refreshToken,
        grant_type: 'refresh_token',
      }),
      signal: AbortSignal.timeout(REQUEST_TIMEOUT_MS),
    });
    if (!response.ok) {
      throw new Error(`gmail_oauth_http_${response.status}`);
    }
    const json = (await response.json().catch(() => null)) as {
      access_token?: unknown;
      expires_in?: unknown;
    } | null;
    const token = trimmed(json?.access_token);
    if (!token) throw new Error('gmail_oauth_no_token');

    const expiresIn = Math.max(60, Number(json?.expires_in) || 3_600);
    tokenCache = {
      value: token,
      expiresAt: Date.now() + expiresIn * 1_000,
    };
    return token;
  }

  async function sendViaGmail(
    address: string,
    subject: string,
    text: string,
  ): Promise<void> {
    const raw = buildGmailRaw(
      settings.from,
      settings.fromName,
      address,
      subject,
      text,
    );
    const attempt = (token: string) =>
      fetchImpl(GMAIL_SEND_URL, {
        method: 'POST',
        headers: {
          Authorization: `Bearer ${token}`,
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ raw }),
        signal: AbortSignal.timeout(REQUEST_TIMEOUT_MS),
      });

    let response = await attempt(await gmailAccessToken());
    if (response.status === 401) {
      // A revoked or expired access token is recoverable with the refresh
      // token, so refresh once and retry the same message.
      response = await attempt(await gmailAccessToken(true));
    }
    if (!response.ok) {
      throw new Error(`gmail_api_http_${response.status}`);
    }
  }

  async function sendViaBrevo(
    address: string,
    subject: string,
    text: string,
  ): Promise<void> {
    const response = await fetchImpl(BREVO_SEND_URL, {
      method: 'POST',
      headers: {
        'api-key': settings.brevoApiKey,
        Accept: 'application/json',
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        sender: { email: settings.from, name: settings.fromName },
        to: [{ email: address }],
        subject,
        textContent: text,
      }),
      signal: AbortSignal.timeout(REQUEST_TIMEOUT_MS),
    });
    if (!response.ok) {
      throw new Error(`brevo_http_${response.status}`);
    }
  }

  async function sendViaSendGrid(
    address: string,
    subject: string,
    text: string,
  ): Promise<void> {
    const response = await fetchImpl(SENDGRID_SEND_URL, {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${settings.sendgridApiKey}`,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        personalizations: [{ to: [{ email: address }] }],
        from: { email: settings.from, name: settings.fromName },
        subject,
        content: [{ type: 'text/plain', value: text }],
      }),
      signal: AbortSignal.timeout(REQUEST_TIMEOUT_MS),
    });
    if (!response.ok) {
      throw new Error(`sendgrid_http_${response.status}`);
    }
  }

  async function sendViaSmtp(
    address: string,
    subject: string,
    text: string,
  ): Promise<void> {
    if (!smtpTransport) smtpTransport = createSmtpTransport(settings);
    await smtpTransport.sendMail({
      from: settings.from,
      to: address,
      subject,
      text,
    });
  }

  return {
    provider,
    async send(message: MailMessage): Promise<MailResult> {
      if (provider === null) {
        return { ok: false, status: 503, error: 'email_not_configured' };
      }

      const address = trimmed(message.to).toLowerCase();
      if (!isValidAddress(address)) {
        return { ok: false, status: 400, error: 'invalid_email' };
      }

      const subject = normalizeSubject(message.subject);
      const text = normalizeBody(message.text);

      try {
        switch (provider) {
          case 'gmail':
            await sendViaGmail(address, subject, text);
            break;
          case 'brevo':
            await sendViaBrevo(address, subject, text);
            break;
          case 'sendgrid':
            await sendViaSendGrid(address, subject, text);
            break;
          case 'smtp':
            await sendViaSmtp(address, subject, text);
            break;
        }
        return { ok: true, provider };
      } catch {
        // Never surface provider responses: they can echo the recipient,
        // credentials or message contents. Callers get a generic failure.
        return { ok: false, status: 502, error: 'email_send_failed' };
      }
    },
  };
}
