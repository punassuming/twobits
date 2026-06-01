/**
 * Two Bits — Managed API Key Proxy (api.twobits.app)
 *
 * Validates a RevenueCat subscription receipt and proxies OpenAI API calls
 * on behalf of Pro subscribers so the OpenAI key never leaves the server.
 *
 * Setup:
 *   1. Deploy via: wrangler deploy  (routes to api.twobits.app automatically)
 *   2. Set secrets: wrangler secret put OPENAI_API_KEY
 *                   wrangler secret put REVENUECAT_API_KEY
 *   3. In the Android apps, set the Pro base URL to https://api.twobits.app
 *      and pass the RevenueCat App User ID as: Authorization: Bearer <userId>
 *
 * Request format (same as OpenAI):
 *   POST /v1/chat/completions     — proxies to OpenAI Chat Completions
 *   POST /v1/audio/transcriptions — proxies to OpenAI Whisper
 *
 * The worker validates the App User ID against RevenueCat before forwarding.
 */

const OPENAI_BASE = "https://api.openai.com";
const REVENUECAT_BASE = "https://api.revenuecat.com/v1";
const PRO_ENTITLEMENT = "pro";

export default {
  async fetch(request, env) {
    if (request.method === "OPTIONS") {
      return corsResponse(new Response(null, { status: 204 }));
    }

    const authHeader = request.headers.get("Authorization") || "";
    const appUserId = authHeader.startsWith("Bearer ")
      ? authHeader.slice(7).trim()
      : null;

    if (!appUserId) {
      return corsResponse(
        new Response(JSON.stringify({ error: "Missing Authorization header" }), {
          status: 401,
          headers: { "Content-Type": "application/json" },
        })
      );
    }

    const hasPro = await verifyProSubscription(appUserId, env);
    if (!hasPro) {
      return corsResponse(
        new Response(JSON.stringify({ error: "Pro subscription required" }), {
          status: 403,
          headers: { "Content-Type": "application/json" },
        })
      );
    }

    const url = new URL(request.url);
    const openAiUrl = `${OPENAI_BASE}${url.pathname}${url.search}`;

    const forwardHeaders = new Headers(request.headers);
    forwardHeaders.set("Authorization", `Bearer ${env.OPENAI_API_KEY}`);
    forwardHeaders.delete("cf-connecting-ip");
    forwardHeaders.delete("x-forwarded-for");

    const upstream = await fetch(openAiUrl, {
      method: request.method,
      headers: forwardHeaders,
      body: request.body,
    });

    return corsResponse(
      new Response(upstream.body, {
        status: upstream.status,
        headers: upstream.headers,
      })
    );
  },
};

async function verifyProSubscription(appUserId, env) {
  const cacheKey = `sub:${appUserId}`;
  const cached = await env.SUBSCRIPTION_CACHE?.get(cacheKey);
  if (cached !== null) {
    return cached === "1";
  }

  const response = await fetch(
    `${REVENUECAT_BASE}/subscribers/${encodeURIComponent(appUserId)}`,
    {
      headers: {
        Authorization: `Bearer ${env.REVENUECAT_API_KEY}`,
        "Content-Type": "application/json",
      },
    }
  );

  if (!response.ok) return false;

  const data = await response.json();
  const isActive =
    data?.subscriber?.entitlements?.[PRO_ENTITLEMENT]?.expires_date
      ? new Date(data.subscriber.entitlements[PRO_ENTITLEMENT].expires_date) > new Date()
      : false;

  // Cache result for 5 minutes to reduce RevenueCat API calls.
  await env.SUBSCRIPTION_CACHE?.put(cacheKey, isActive ? "1" : "0", {
    expirationTtl: 300,
  });

  return isActive;
}

function corsResponse(response) {
  const headers = new Headers(response.headers);
  headers.set("Access-Control-Allow-Origin", "*");
  headers.set("Access-Control-Allow-Methods", "POST, OPTIONS");
  headers.set("Access-Control-Allow-Headers", "Authorization, Content-Type");
  return new Response(response.body, { status: response.status, headers });
}
