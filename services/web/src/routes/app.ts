import { Readable } from 'node:stream';
import { Router, type Request, type Response } from 'express';
import type { Config } from '../config.js';

const GITHUB_API = 'https://api.github.com';
const USER_AGENT = 'Ei-Void-Updater/1.0';
const CACHE_MS = 60_000;

export interface ReleaseDeps {
  config: Config;
  /** Injectable fetch for tests; defaults to the global fetch. */
  fetchImpl?: typeof fetch;
}

interface GitHubAsset {
  id?: number;
  name?: string;
  size?: number;
  url?: string;
}

interface GitHubRelease {
  tag_name?: string;
  body?: string;
  published_at?: string;
  assets?: GitHubAsset[];
}

interface LatestRelease {
  version: string;
  assetId: number;
  assetName: string;
  assetSize: number;
  assetUrl: string;
  notes: string;
  publishedAt: string;
}

class ReleaseError extends Error {
  constructor(
    readonly code: string,
    readonly status: number,
  ) {
    super(code);
    this.name = 'ReleaseError';
  }
}

function githubHeaders(token: string, accept: string): Record<string, string> {
  return {
    Accept: accept,
    'User-Agent': USER_AGENT,
    'X-GitHub-Api-Version': '2022-11-28',
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
  };
}

function sendError(res: Response, error: unknown): void {
  const releaseError = error instanceof ReleaseError ? error : null;
  res
    .status(releaseError?.status ?? 502)
    .json({ error: releaseError?.code ?? 'update_check_failed' });
}

export function createAppReleaseRouter({ config, fetchImpl }: ReleaseDeps): Router {
  const router = Router();
  const doFetch = fetchImpl ?? fetch;
  let cache: { at: number; value: LatestRelease } | null = null;

  async function latestRelease(): Promise<LatestRelease> {
    const now = Date.now();
    if (cache && now - cache.at < CACHE_MS) return cache.value;
    if (!config.github.token) {
      throw new ReleaseError('update_not_configured', 503);
    }

    const response = await doFetch(
      `${GITHUB_API}/repos/${config.github.repo}/releases/latest`,
      { headers: githubHeaders(config.github.token, 'application/vnd.github+json') },
    );
    if (!response.ok) {
      throw new ReleaseError('update_release_unavailable', 502);
    }

    const release = (await response.json().catch(() => null)) as GitHubRelease | null;
    const version = String(release?.tag_name ?? '').replace(/^v/i, '').trim();
    const asset = Array.isArray(release?.assets)
      ? release.assets.find((entry) => /\.apk$/i.test(String(entry?.name ?? '')))
      : undefined;
    if (!version || !asset || typeof asset.id !== 'number') {
      throw new ReleaseError('update_asset_missing', 502);
    }

    const value: LatestRelease = {
      version,
      assetId: asset.id,
      assetName: String(asset.name ?? `ei-void-${version}.apk`),
      assetSize: Number(asset.size ?? 0),
      assetUrl:
        asset.url ||
        `${GITHUB_API}/repos/${config.github.repo}/releases/assets/${asset.id}`,
      notes: String(release?.body ?? '').slice(0, 2000),
      publishedAt: String(release?.published_at ?? ''),
    };
    cache = { at: now, value };
    return value;
  }

  async function releaseAsset(release: LatestRelease): Promise<globalThis.Response> {
    const token = config.github.token;
    if (!token) throw new ReleaseError('update_not_configured', 503);
    let response: globalThis.Response = await doFetch(release.assetUrl, {
      headers: githubHeaders(token, 'application/octet-stream'),
      redirect: 'manual',
    });
    if ([301, 302, 303, 307, 308].includes(response.status)) {
      const location = response.headers.get('location');
      if (!location) throw new ReleaseError('update_redirect_missing', 502);
      response = await doFetch(location, { headers: { 'User-Agent': USER_AGENT } });
    }
    if (!response.ok || !response.body) {
      throw new ReleaseError('update_download_failed', 502);
    }
    return response;
  }

  router.get('/latest', async (_req: Request, res: Response) => {
    try {
      const release = await latestRelease();
      res.set('Cache-Control', 'no-store');
      res.json({
        version: release.version,
        apkUrl: '/app/download',
        notes: release.notes,
        publishedAt: release.publishedAt,
        assetName: release.assetName,
        size: release.assetSize,
      });
    } catch (error) {
      sendError(res, error);
    }
  });

  router.get('/download', async (_req: Request, res: Response) => {
    try {
      const release = await latestRelease();
      const asset = await releaseAsset(release);
      res.status(200);
      res.set({
        'Content-Type': 'application/vnd.android.package-archive',
        'Content-Disposition': `attachment; filename="${release.assetName.replace(/"/g, '')}"`,
        'Cache-Control': 'no-store',
        'X-Content-Type-Options': 'nosniff',
      });
      const length = release.assetSize || Number(asset.headers.get('content-length') ?? 0);
      if (length > 0) res.set('Content-Length', String(length));
      Readable.fromWeb(asset.body as Parameters<typeof Readable.fromWeb>[0]).pipe(res);
    } catch (error) {
      if (!res.headersSent) {
        sendError(res, error);
        return;
      }
      res.destroy(error instanceof Error ? error : undefined);
    }
  });

  return router;
}
