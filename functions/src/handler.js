"use strict";

const crypto = require("crypto");
const { runTurn, BadInput } = require("./loop");
const { buildPlaces } = require("./places");
const { openSession, serialiseSession } = require("./session");
const { normaliseCode } = require("./languages");

const PROFILES = ["wheelchair", "walking_aid", "low_vision", "none"];

function optionsFromBody(body) {
  const profile = typeof body.mobilityProfile === "string" ? body.mobilityProfile.toLowerCase().replace(/-/g, "_") : "none";
  const speed = Number(body.speedMultiplier);
  return {
    mobilityProfile: PROFILES.includes(profile) ? profile : "none",
    preferLifts: body.preferLifts === true,
    avoidSteepRamps: body.avoidSteepRamps === true,
    speedMultiplier: Number.isFinite(speed) && speed > 0 ? Math.max(0.3, Math.min(2.0, speed)) : 1.0,
  };
}

/**
 * POST /api/companion/message
 * @param body      parsed JSON body
 * @param identity  { uid, anonymous } from a verified Firebase ID token, or null
 * @param deps      { client, store, routing, aliases }
 * @returns { status, body }
 */
async function handleMessage({ body, identity, deps }) {
  if (!body || typeof body !== "object") return { status: 400, body: { error: "bad_request" } };

  // Anonymous use is allowed, but anonymous reports never count toward flagging a path
  const userId = identity && !identity.anonymous ? identity.uid : null;
  const ownerUid = identity ? identity.uid : null;

  const graph = await deps.store.loadGraph();
  const places = buildPlaces(graph.nodes, deps.aliases || {});

  const sessionId = typeof body.sessionId === "string" && /^[A-Za-z0-9-]{8,64}$/.test(body.sessionId) ? body.sessionId : null;
  const stored = sessionId ? await deps.store.getSession(sessionId) : null;
  const { session, continued } = openSession(stored, ownerUid);
  const id = continued ? sessionId : crypto.randomUUID();
  if (!continued) session.ownerUid = ownerUid;

  // Verified translations for directions, in the language the user chose or spoke
  const phraseLang = normaliseCode(body.preferredLang) || normaliseCode(body.inputLang) || "en";
  const phrases = phraseLang === "en" ? { lang: "en", phrases: {} } : { lang: phraseLang, phrases: await deps.store.getPhrases(phraseLang) };

  try {
    const out = await runTurn({
      client: deps.client,
      deps: { graph, places, store: deps.store, routing: deps.routing, phrases, now: deps.now || Date.now },
      session,
      input: {
        text: body.text,
        inputMode: body.inputMode === "voice" ? "voice" : "text",
        inputLang: body.inputLang,
        preferredLang: body.preferredLang,
        options: optionsFromBody(body),
        userId,
      },
    });
    await deps.store.saveSession(id, serialiseSession(session));
    return {
      status: 200,
      body: {
        sessionId: id,
        reply: out.reply,
        language: { code: out.language.code || "", source: out.language.source, tier: out.language.tier },
        route: out.route,
        reportFiled: out.reportFiled,
        guardTriggered: out.guardTriggered,
      },
    };
  } catch (e) {
    if (e instanceof BadInput) return { status: 400, body: { error: "bad_request", message: e.message } };
    throw e;
  }
}

module.exports = { handleMessage, optionsFromBody };
