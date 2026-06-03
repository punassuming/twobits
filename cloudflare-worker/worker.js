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
 *   2. wrangler durable-object namespace create SPEND_TRACKER
 *      Paste the returned ID into wrangler.toml.
 *   3. wrangler secret put OPENAI_API_KEY
 *   4. wrangler secret put REVENUECAT_API_KEY
 *   5. wrangler deploy
 *
 * Endpoints proxied (same contract as OpenAI):
 *   POST /v1/chat/completions     — Chat Completions (streaming + non-streaming + vision)
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
//
// Vision note: image tokens are reported inside prompt_tokens in the OpenAI usage
// response, so vision calls through /v1/chat/completions are billed correctly
// by the same pricing entries — no separate vision pricing is needed.
// OpenAI image token formula (high detail): 85 base + 170 per 512x512 tile.
// A typical resized phone photo costs ~800–1500 image tokens.
//
// Unknown models: requests specifying a model not in this table are rejected with 422.
// Never fall back to a cheap default — an unlisted model may cost far more.
const CHAT_PRICING = {
  // GPT-5 family (Scrybe transforms, diarization, Smart Analyze)
  "gpt-5-nano":    { input: 0.10,  output:  0.80 },
  "gpt-5-mini":    { input: 0.25,  output:  2.00 }, // default transform model
  "gpt-5":         { input: 1.25,  output: 10.00 },
  "gpt-5.1":       { input: 0.63,  output:  5.00 },
  "gpt-5.4-mini":  { input: 0.75,  output:  4.50 },
  "gpt-5.4":       { input: 2.50,  output: 15.00 }, // March 2026 flagship
  // GPT-4.1 family (Scrybe fallbacks)
  "gpt-4.1-nano":  { input: 0.10,  output:  0.40 },
  "gpt-4.1-mini":  { input: 0.40,  output:  1.60 },
  // GPT-4o family (Shelf Snap: vision analysis + price research; both support vision)
  "gpt-4o-mini":   { input: 0.15,  output:  0.60 }, // price research + vision capable
  "gpt-4o":        { input: 2.50,  output: 10.00 }, // item photo analysis (vision)
};
const WHISPER_PRICE_PER_MIN = 0.006; // $0.006 / minute (Scrybe transcription)

// Conservative pessimistic cost ceiling per request used to pre-reserve budget
// in the SpendTracker Durable Object before forwarding to OpenAI.
// Based on 4 096 input + 8 192 output tokens at each model's rates.
// Audio uses 25 minutes (the OpenAI per-file maximum).
function reservationCost(model) {
  const p = CHAT_PRICING[model];
  return (4_096 / 1_000_000) * p.input + (8_192 / 1_000_000) * p.output;
}
const AUDIO_RESERVATION_USD = 25 * WHISPER_PRICE_PER_MIN; // ~$0.15

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

    // --- Build upstream request ---
    const url     = new URL(request.url);
    const isAudio = url.pathname.startsWith("/v1/audio/");

    let model      = null;
    let isStreaming = false;
    let upstreamBody;

    if (isAudio) {
      upstreamBody = request.body;
    } else {
      const raw = await request.text();
      try {
        const parsed = JSON.parse(raw);
        model = parsed.model ?? null;

        // Reject any model not in the pricing table so unlisted or newly added
        // expensive models are never silently charged as the cheapest fallback.
        if (!model || !CHAT_PRICING[model]) {
          return corsResponse(jsonError(
            `Model "${model ?? "(unspecified)"}" is not supported by this proxy. ` +
            `Allowed models: ${Object.keys(CHAT_PRICING).join(", ")}.`,
            422,
          ));
        }

        isStreaming = parsed.stream === true;
        if (isStreaming) {
          parsed.stream_options = { ...(parsed.stream_options ?? {}), include_usage: true };
        }
        upstreamBody = JSON.stringify(parsed);
      } catch {
        return corsResponse(jsonError("Invalid JSON request body", 400));
      }
    }

    // --- Atomic monthly spend gate + reservation via Durable Object ---
    // SpendTracker serializes all spend mutations for this user so concurrent
    // requests cannot each read the same KV total and undercount charges.
    const reservation = isAudio ? AUDIO_RESERVATION_USD : reservationCost(model);
    const spend       = spendStub(appUserId, env);
    const month       = monthKey();

    const gateResp = await spend.fetch("http://internal/reserve", {
      method: "POST",
      body:   JSON.stringify({ month, amount: reservation, budget: MONTHLY_BUDGET_USD }),
    });
    const { ok: allowed } = await gateResp.json();

    if (!allowed) {
      return corsResponse(jsonError(
        `Monthly usage limit of $${MONTHLY_BUDGET_USD.toFixed(2)} reached. Resets on the 1st of next month.`,
        429,
      ));
    }

    // --- Forward to OpenAI ---
    const upstreamHeaders = new Headers(request.headers);
    upstreamHeaders.set("Authorization", `Bearer ${env.OPENAI_API_KEY}`);
    upstreamHeaders.delete("cf-connecting-ip");
    upstreamHeaders.delete("x-forwarded-for");

    const upstream = await fetch(`${OPENAI_BASE}${url.pathname}${url.search}`, {
      method:  request.method,
      headers: upstreamHeaders,
      body:    upstreamBody,
    });

    // Pass non-2xx errors through; refund the full reservation.
    if (!upstream.ok) {
      ctx.waitUntil(settleSpend(spend, month, 0, reservation));
      const errText = await upstream.text();
      return corsResponse(new Response(errText, {
        status:  upstream.status,
        headers: upstream.headers,
      }));
    }

    // --- Forward response and settle actual spend ---

    if (isAudio) {
      const body = await upstream.text();
      try {
        const json   = JSON.parse(body);
        const actual = json.duration ? (json.duration / 60) * WHISPER_PRICE_PER_MIN : 0;
        ctx.waitUntil(settleSpend(spend, month, actual, reservation));
      } catch {
        ctx.waitUntil(settleSpend(spend, month, 0, reservation));
      }
      return corsResponse(new Response(body, {
        status:  upstream.status,
        headers: upstream.headers,
      }));
    }

    if (!isStreaming) {
      const body = await upstream.text();
      try {
        const json   = JSON.parse(body);
        const actual = json.usage ? chatCost(model, json.usage) : 0;
        ctx.waitUntil(settleSpend(spend, month, actual, reservation));
      } catch {
        ctx.waitUntil(settleSpend(spend, month, 0, reservation));
      }
      return corsResponse(new Response(body, {
        status:  upstream.status,
        headers: upstream.headers,
      }));
    }

    // Streaming: tee — forward one branch to client, drain the other for usage.
    const [forClient, forTracking] = upstream.body.tee();
    ctx.waitUntil(
      drainStreamForUsage(forTracking, model)
        .then(actual => settleSpend(spend, month, actual, reservation)),
    );
    return corsResponse(new Response(forClient, {
      status:  upstream.status,
      headers: upstream.headers,
    }));
  },
};

// ---------------------------------------------------------------------------
// SpendTracker Durable Object
//
// One instance per user (keyed by userId). All spend operations for a given
// user are serialized through this single instance, eliminating the
// read-modify-write race that KV's eventual consistency allows.
//
// /reserve  — atomically check budget and pre-deduct the pessimistic estimate.
// /settle   — replace reservation with actual cost (delta may be negative).
// ---------------------------------------------------------------------------

export class SpendTracker {
  constructor(state, env) {
    this.state = state;
  }

  async fetch(request) {
    const { month, amount, budget } = await request.json();
    const key     = `m:${month}`;
    const current = (await this.state.storage.get(key)) ?? 0;
    const url     = new URL(request.url);

    if (url.pathname === "/reserve") {
      if (current >= budget) {
        return Response.json({ ok: false, total: current });
      }
      const next = current + amount;
      await this.state.storage.put(key, next);
      return Response.json({ ok: true, total: next });
    }

    if (url.pathname === "/settle") {
      // amount = actual - reserved (negative when actual < reserved → refund).
      const next = Math.max(0, current + amount);
      await this.state.storage.put(key, next);
      return Response.json({ ok: true, total: next });
    }

    return new Response("Not found", { status: 404 });
  }
}

// ---------------------------------------------------------------------------
// Cost tracking helpers
// ---------------------------------------------------------------------------

function spendStub(userId, env) {
  const id = env.SPEND_TRACKER.idFromName(userId);
  return env.SPEND_TRACKER.get(id);
}

/** Settles a request by adjusting reserved → actual cost. */
async function settleSpend(stub, month, actualUsd, reservedUsd) {
  const delta = actualUsd - reservedUsd;
  if (Math.abs(delta) < 0.000_001) return;
  await stub.fetch("http://internal/settle", {
    method: "POST",
    body:   JSON.stringify({ month, amount: delta }),
  });
}

function chatCost(model, usage) {
  const p = CHAT_PRICING[model];
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
