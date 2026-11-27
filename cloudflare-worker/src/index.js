/**
 * Hiểu Đơn (MedRead VN) — Secure Gemini AI Proxy on Cloudflare Workers
 *
 * Benefits:
 * - Zero Gemini API keys embedded in the Android APK.
 * - Supports rotating multiple Gemini API keys (comma-separated in GEMINI_API_KEYS secret)
 *   with automatic failover on HTTP 429 (quota), 503 (high demand), or 403.
 * - Optional shared app client token (APP_CLIENT_TOKEN) verified with Web Crypto timing-safe compare.
 */

const GEMINI_ORIGIN = "https://generativelanguage.googleapis.com";
const MAX_BODY_BYTES = 18 * 1024 * 1024; // 18 MB (supports multi-page scans & PDFs)
const GENERATE_ROUTE_REGEX = /^\/v1beta\/models\/([a-zA-Z0-9._-]+):generateContent$/;

function jsonResponse(payload, status = 200) {
  return new Response(JSON.stringify(payload), {
    status,
    headers: {
      "content-type": "application/json; charset=utf-8",
      "cache-control": "no-store"
    }
  });
}

async function timingSafeTokenEqual(provided, expected) {
  const enc = new TextEncoder();
  const aHash = await crypto.subtle.digest("SHA-256", enc.encode(provided));
  const bHash = await crypto.subtle.digest("SHA-256", enc.encode(expected));
  const a = new Uint8Array(aHash);
  const b = new Uint8Array(bHash);
  let diff = 0;
  for (let i = 0; i < a.length; i++) {
    diff |= a[i] ^ b[i];
  }
  return diff === 0;
}

function loadKeyPool(env) {
  const multi = (env.GEMINI_API_KEYS || "")
    .split(/[\n,;]+/)
    .map((k) => k.trim())
    .filter(Boolean);
  const single = (env.GEMINI_API_KEY || "").trim();
  const combined = single && !multi.includes(single) ? [...multi, single] : multi;
  return combined;
}

function pickStartIndex(length) {
  if (length <= 1) return 0;
  const buf = new Uint32Array(1);
  crypto.getRandomValues(buf);
  return buf[0] % length;
}

async function forwardWithKeyRotation({ method, upstreamUrl, bodyBuffer, contentType, keys }) {
  const start = pickStartIndex(keys.length);
  let lastResponse = null;

  for (let attempt = 0; attempt < keys.length; attempt++) {
    const apiKey = keys[(start + attempt) % keys.length];
    const headers = new Headers();
    headers.set("x-goog-api-key", apiKey);
    if (contentType) {
      headers.set("content-type", contentType);
    }

    const upstreamResp = await fetch(upstreamUrl, {
      method,
      headers,
      body: bodyBuffer
    });

    if (upstreamResp.ok) {
      return upstreamResp;
    }

    lastResponse = upstreamResp;
    // Rotate to next API key if quota/rate-limit/key-error/temporary overload occurs
    const shouldRotate =
      upstreamResp.status === 429 ||
      upstreamResp.status === 503 ||
      upstreamResp.status === 403 ||
      upstreamResp.status === 401;

    if (!shouldRotate) {
      return upstreamResp;
    }

    // Drain failed response body before trying next key to free connection resources
    await upstreamResp.arrayBuffer().catch(() => {});
  }

  return lastResponse;
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    const path = url.pathname;

    // 1. Health check endpoint
    if (request.method === "GET" && (path === "/" || path === "/health")) {
      const keys = loadKeyPool(env);
      return jsonResponse({
        ok: true,
        service: "hieudon-gemini-proxy",
        keysConfigured: keys.length
      });
    }

    // 2. Verify optional APP_CLIENT_TOKEN (protects proxy from unauthorized third parties)
    const expectedToken = (env.APP_CLIENT_TOKEN || "").trim();
    if (expectedToken.length > 0) {
      const providedToken = (request.headers.get("x-hieudon-token") || "").trim();
      const valid = await timingSafeTokenEqual(providedToken, expectedToken);
      if (!valid) {
        return jsonResponse(
          { error: { message: "Unauthorized app client token." } },
          401
        );
      }
    }

    // 3. Load Gemini API key pool from Worker Secrets
    const keys = loadKeyPool(env);
    if (keys.length === 0) {
      return jsonResponse(
        { error: { message: "Proxy chưa cấu hình Secret GEMINI_API_KEYS trên Cloudflare." } },
        500
      );
    }

    // 4. Route: GET /v1beta/models (dynamic model discovery)
    if (request.method === "GET" && path === "/v1beta/models") {
      const upstreamUrl = `${GEMINI_ORIGIN}/v1beta/models${url.search}`;
      const resp = await forwardWithKeyRotation({
        method: "GET",
        upstreamUrl,
        bodyBuffer: null,
        contentType: null,
        keys
      });
      return new Response(resp.body, {
        status: resp.status,
        headers: {
          "content-type": resp.headers.get("content-type") || "application/json; charset=utf-8",
          "cache-control": "no-store"
        }
      });
    }

    // 5. Route: POST /v1beta/models/{model}:generateContent
    if (request.method === "POST" && GENERATE_ROUTE_REGEX.test(path)) {
      const contentLength = Number(request.headers.get("content-length") || "0");
      if (contentLength > MAX_BODY_BYTES) {
        return jsonResponse(
          { error: { message: "Tệp gửi lên vượt quá giới hạn 18MB." } },
          413
        );
      }

      const bodyBuffer = await request.arrayBuffer();
      if (bodyBuffer.byteLength > MAX_BODY_BYTES) {
        return jsonResponse(
          { error: { message: "Tệp gửi lên vượt quá giới hạn 18MB." } },
          413
        );
      }

      const upstreamUrl = `${GEMINI_ORIGIN}${path}`;
      const resp = await forwardWithKeyRotation({
        method: "POST",
        upstreamUrl,
        bodyBuffer,
        contentType: request.headers.get("content-type") || "application/json; charset=utf-8",
        keys
      });

      return new Response(resp.body, {
        status: resp.status,
        headers: {
          "content-type": resp.headers.get("content-type") || "application/json; charset=utf-8",
          "cache-control": "no-store"
        }
      });
    }

    return jsonResponse({ error: { message: "Route not found." } }, 404);
  }
};
