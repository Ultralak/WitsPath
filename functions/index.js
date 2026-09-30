"use strict";

const admin = require("firebase-admin");
const Anthropic = require("@anthropic-ai/sdk");
const { GoogleAuth } = require("google-auth-library");
const { onRequest } = require("firebase-functions/v2/https");
const { onDocumentCreated } = require("firebase-functions/v2/firestore");
const { defineSecret, defineString } = require("firebase-functions/params");

const { handleMessage } = require("./src/handler");
const { createFirestoreStore } = require("./src/firestoreStore");
const { createRoutingClient } = require("./src/routing");
const { maybeFlagEdge } = require("./src/flagging");

admin.initializeApp();

// The key lives only here, as a secret. It is never sent to the app.
const ANTHROPIC_API_KEY = defineSecret("ANTHROPIC_API_KEY");
// Base URL of the Cloud Run routing-service
const ROUTING_URL = defineString("ROUTING_URL", { default: "" });

const RATE_LIMIT = 20; // messages per minute per user or address
const hits = new Map();

function rateLimited(key) {
  const now = Date.now();
  const recent = (hits.get(key) || []).filter((t) => now - t < 60_000);
  recent.push(now);
  hits.set(key, recent);
  if (hits.size > 5000) hits.clear();
  return recent.length > RATE_LIMIT;
}

async function identityFrom(req) {
  const header = req.get("Authorization") || "";
  if (!header.startsWith("Bearer ")) return null; // anonymous
  const decoded = await admin.auth().verifyIdToken(header.slice(7));
  return { uid: decoded.uid, anonymous: decoded.firebase && decoded.firebase.sign_in_provider === "anonymous" };
}

function routingTokenProvider(url) {
  const auth = new GoogleAuth();
  return async () => {
    try {
      const client = await auth.getIdTokenClient(url);
      const headers = await client.getRequestHeaders();
      return (headers.Authorization || headers.authorization || "").replace(/^Bearer /, "") || null;
    } catch (e) {
      return null;
    }
  };
}

exports.companionMessage = onRequest(
  { secrets: [ANTHROPIC_API_KEY], cors: true, maxInstances: 5, timeoutSeconds: 60, memory: "512MiB" },
  async (req, res) => {
    if (req.method !== "POST") return res.status(405).json({ error: "method_not_allowed" });

    let identity = null;
    try {
      identity = await identityFrom(req);
    } catch (e) {
      return res.status(401).json({ error: "unauthorized" });
    }
    if (rateLimited(identity ? identity.uid : req.ip)) return res.status(429).json({ error: "busy" });

    const url = ROUTING_URL.value();
    try {
      const out = await handleMessage({
        body: req.body,
        identity,
        deps: {
          client: new Anthropic({ apiKey: ANTHROPIC_API_KEY.value() }),
          store: createFirestoreStore(admin),
          routing: createRoutingClient({ url, getToken: routingTokenProvider(url) }),
        },
      });
      return res.status(out.status).json(out.body);
    } catch (e) {
      // Never leak details. The app shows one friendly message for any 503.
      console.error("companion_error", e && e.status, e && e.message);
      const busy = e instanceof Anthropic.RateLimitError;
      return res.status(503).json({ error: busy ? "busy" : "unavailable" });
    }
  }
);

// Reports written straight from the app also count toward flagging a path
exports.onReportCreated = onDocumentCreated("reports/{reportId}", async (event) => {
  const report = event.data && event.data.data();
  if (!report || !report.edgeId) return;
  await maybeFlagEdge(createFirestoreStore(admin), report.edgeId);
});
