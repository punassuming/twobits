/**
 * Two Bits — Managed API Key Proxy (api.twobits.app)
 *
 * Validates RevenueCat Pro subscriptions and proxies OpenAI API calls
 * on behalf of Pro subscribers. The OpenAI key never leaves this worker.
 * Enforces a per-user monthly spend cap to prevent abuse.
 *
 * Setup:
 *   1. wrangler kv:namespace create SUBSCRIPTION_CACHE
 *      Paste the returned ID into wrangler.toml.
 *   2. wrangler secret put OPENAI_API_KEY
 *   3. wrangler secret put REVENUECAT_API_KEY
 *   4. wrangler deploy
 *
 * Endpoints proxied (same contract as OpenAI):
 *   POST /v1/chat/completions     — Chat Completions (streaming + non-streaming)
 *   POST /v1/audio/transcriptions — Whisper transcription
 *
 * Auth: Authorization: Bearer <RevenueCat App User ID>
 */

const OPENAI_BASE        = "https://api.openai.com";
const REVENUECAT_BASE    = "https://api.revenuecat.com/v1";
const PRO_ENTITLEMENT    = "pro";
const MONTHLY_BUDGET_USD = 2.00;

// Pricing per 1M tokens (input / output).
// Must stay in sync with OpenAiTransformModel.kt and OpenAiProfileSuggestionModel.kt.
// Verify against https://openai.com/pricing when OpenAI updates rates.
const CHAT_PRICING = {
  // GPT-5 family
  "gpt-5-nano":    { input: 0.10,  output:  0.80 },
  "gpt-5-mini":    { input: 0.25,  output:  2.00 },
  "gpt-5":         { input: 1.25,  output: 10.00 },
  "gpt-5.1":       { input: 0.63,  output:  5.00 },
  "gpt-5.4-mini":  { input: 0.75,  output:  4.50 },
  "gpt-5.4":       { input: 2.50,  output: 15.00 },
  // GPT-4.1 family
  "gpt-4.1-nano":  { input: 0.10,  output:  0.40 },
  "gpt-4.1-mini":  { input: 0.40,  output:  1.60 },
  // Legacy GPT-4o (Shelf Snap vision analysis + price research)
  "gpt-4o-mini":   { input: 0.15,  output:  0.60 },
  "gpt-4o":        { input: 2.50,  output: 10.00 },
};
const WHISPER_PRICE_PER_MIN = 0.006; // $0.006 / minute

export default {
  async fetch(request, env, ctx) {
    if (request.method === "OPTIONS") {
      return corsResponse(new Response(null, { status: 204 }));
    }

    // --- Authentication ---
    const appUserId = extractBearer(request);
    if (!appUserId) {
      return corsResponse(jsonError("Missing Authorization header", 401));
    }

    // --- Subscription validation (cached 5 min) ---
    if (!await verifyPro(appUserId, env)) {
      return corsResponse(jsonError("Pro subscription required", 403));
    }

    // --- Monthly spend gate ---
    const spent = await getMonthlySpend(appUserId, env);
    if (spent >= MONTHLY_BUDGET_USD) {
      return corsResponse(jsonError(
        `Monthly usage limit of $${MONTHLY_BUDGET_USD.toFixed(2)} reached. Resets on the 1st of next month.`,
        429,
      ));
    }

    // --- Build upstream request ---
    const url     = new URL(request.url);
    const isAudio = url.pathname.startsWith("/v1/audio/");

    let model      = "gpt-5-mini"; // fallback for cost estimation
    let isStreaming = false;
    let upstreamBody;

    if (isAudio) {
      // Multipart — stream through without buffering
      upstreamBody = request.body;
    } else {
      const raw = await request.text();
      try {
        const parsed = JSON.parse(raw);
        model       = parsed.model ?? model;
        isStreaming  = parsed.stream === true;
        // Inject include_usage so we can read token counts from the stream
        if (isStreaming) {
          parsed.stream_options = { ...(parsed.stream_options ?? {}), include_usage: true };
        }
        upstreamBody = JSON.stringify(parsed);
      } catch {
        upstreamBody = raw;
      }
    }

    const upstreamHeaders = new Headers(request.headers);
    upstreamHeaders.set("Authorization", `Bearer ${env.OPENAI_API_KEY}`);
    upstreamHeaders.delete("cf-connecting-ip");
    upstreamHeaders.delete("x-forwarded-for");

    const upstream = await fetch(`${OPENAI_BASE}${url.pathname}${url.search}`, {
      method:  request.method,
      headers: upstreamHeaders,
      body:    upstreamBody,
    });

    // Pass non-2xx errors straight through
    if (!upstream.ok) {
      const errText = await upstream.text();
      return corsResponse(new Response(errText, {
        status:  upstream.status,
        headers: upstream.headers,
      }));
    }

    // --- Forward response and track spend ---

    if (isAudio) {
      // Whisper returns small JSON with a `duration` field (seconds)
      const body = await upstream.text();
      try {
        const json    = JSON.parse(body);
        const costUsd = json.duration ? (json.duration / 60) * WHISPER_PRICE_PER_MIN : 0;
        ctx.waitUntil(addSpend(appUserId, costUsd, env));
      } catch { /* ignore parse errors */ }
      return corsResponse(new Response(body, {
        status:  upstream.status,
        headers: upstream.headers,
      }));
    }

    if (!isStreaming) {
      // Non-streaming: buffer, extract usage, forward
      const body = await upstream.text();
      try {
        const json = JSON.parse(body);
        if (json.usage) ctx.waitUntil(addSpend(appUserId, chatCost(model, json.usage), env));
      } catch { /* ignore */ }
      return corsResponse(new Response(body, {
        status:  upstream.status,
        headers: upstream.headers,
      }));
    }

    // Streaming: tee — forward one branch to client, drain the other for usage
    const [forClient, forTracking] = upstream.body.tee();
    ctx.waitUntil(
      drainStreamForUsage(forTracking, model)
        .then(costUsd => addSpend(appUserId, costUsd, env)),
    );
    return corsResponse(new Response(forClient, {
      status:  upstream.status,
      headers: upstream.headers,
    }));
  },
};

// ---------------------------------------------------------------------------
// Cost tracking
// ---------------------------------------------------------------------------

function chatCost(model, usage) {
  const p = CHAT_PRICING[model] ?? CHAT_PRICING["gpt-5-mini"];
  return (usage.prompt_tokens     / 1_000_000) * p.input
       + (usage.completion_tokens / 1_000_000) * p.output;
}

async function drainStreamForUsage(stream, model) {
  const reader  = stream.getReader();
  const decoder = new TextDecoder();
  let lastDataLine = "";
  try {
    for (;;) {
      const { done, value } = await reader.read();
      if (done) break;
      const text = decoder.decode(value, { stream: true });
      for (const line of text.split("\n")) {
        if (line.startsWith("data: ") && !line.includes("[DONE]")) {
          lastDataLine = line.slice(6).trim();
        }
      }
    }
  } catch { /* stream closed early */ }
  try {
    const chunk = JSON.parse(lastDataLine);
    if (chunk.usage) return chatCost(model, chunk.usage);
  } catch { /* no usage chunk found */ }
  return 0;
}

function monthKey() {
  const d = new Date();
  return `${d.getUTCFullYear()}-${String(d.getUTCMonth() + 1).padStart(2, "0")}`;
}

async function getMonthlySpend(userId, env) {
  const val = await env.SUBSCRIPTION_CACHE.get(`spend:${userId}:${monthKey()}`);
  return val ? parseFloat(val) : 0;
}

async function addSpend(userId, costUsd, env) {
  if (costUsd <= 0) return;
  const key     = `spend:${userId}:${monthKey()}`;
  const current = await env.SUBSCRIPTION_CACHE.get(key);
  const next    = (current ? parseFloat(current) : 0) + costUsd;
  // TTL 35 days — outlasts any calendar month so keys expire automatically
  await env.SUBSCRIPTION_CACHE.put(key, next.toFixed(8), {
    expirationTtl: 35 * 24 * 60 * 60,
  });
}

// ---------------------------------------------------------------------------
// RevenueCat subscription validation
// ---------------------------------------------------------------------------

async function verifyPro(appUserId, env) {
  const cacheKey = `sub:${appUserId}`;
  const cached   = await env.SUBSCRIPTION_CACHE.get(cacheKey);
  if (cached !== null && cached !== undefined) return cached === "1";

  const res = await fetch(
    `${REVENUECAT_BASE}/subscribers/${encodeURIComponent(appUserId)}`,
    {
      headers: {
        Authorization: `Bearer ${env.REVENUECAT_API_KEY}`,
        "Content-Type": "application/json",
      },
    },
  );
  if (!res.ok) return false;

  const data     = await res.json();
  const expires  = data?.subscriber?.entitlements?.[PRO_ENTITLEMENT]?.expires_date;
  const isActive = expires ? new Date(expires) > new Date() : false;

  await env.SUBSCRIPTION_CACHE.put(cacheKey, isActive ? "1" : "0", {
    expirationTtl: 300, // re-check every 5 minutes
  });
  return isActive;
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function extractBearer(request) {
  const h = request.headers.get("Authorization") ?? "";
  return h.startsWith("Bearer ") ? h.slice(7).trim() : null;
}

function jsonError(message, status) {
  return new Response(JSON.stringify({ error: message }), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

function corsResponse(response) {
  const headers = new Headers(response.headers);
  headers.set("Access-Control-Allow-Origin",  "*");
  headers.set("Access-Control-Allow-Methods", "POST, OPTIONS");
  headers.set("Access-Control-Allow-Headers", "Authorization, Content-Type");
  return new Response(response.body, { status: response.status, headers });
}
