"use strict";

const { MODEL, MAX_TOKENS, MAX_ITERATIONS, SYSTEM_PROMPT, TOOLS, SAFE_REPLY, INCOMPLETE_REPLY } = require("./prompt");
const { createToolHandlers } = require("./tools");
const { collectNumbers, collectTextNumbers, findUngrounded } = require("./guard");
const { resolveLanguage, normaliseCode } = require("./languages");
const { trimHistory, GROUNDED_NUMBERS_CAP, CARDS_KEEP } = require("./session");

const MAX_TEXT = 1000;

class BadInput extends Error {}

function validateText(text) {
  if (typeof text !== "string" || !text.trim()) throw new BadInput("text is required");
  if (text.length > MAX_TEXT) throw new BadInput(`text must be at most ${MAX_TEXT} characters`);
  return text.trim();
}

/** Context the app supplies that the user did not write. Never contains the user's words. */
function inputNote({ inputMode, inputLang, preferredLang, mobilityProfile }) {
  const parts = [];
  if (inputMode === "voice") {
    parts.push("input was spoken");
    if (normaliseCode(inputLang)) parts.push(`the speech recognizer detected language "${normaliseCode(inputLang)}"`);
  }
  if (normaliseCode(preferredLang)) parts.push(`the user chose "${normaliseCode(preferredLang)}" as their reply language`);
  if (mobilityProfile && mobilityProfile !== "none") parts.push(`the user's saved mobility profile is "${mobilityProfile}"`);
  return parts.length ? `[App input notes, not written by the user: ${parts.join("; ")}.]` : null;
}

function textOf(content) {
  return content
    .filter((b) => b.type === "text")
    .map((b) => b.text)
    .join("")
    .trim();
}

/**
 * One conversation turn: the tool-use loop, then the number guard.
 *
 * @param client   Anthropic client (or a fake with the same messages.create)
 * @param deps     { graph, places, store, routing, phrases, now }
 * @param session  decoded session (mutated and returned by the caller for saving)
 * @param input    { text, inputMode, inputLang, preferredLang, options, userId }
 */
async function runTurn({ client, deps, session, input }) {
  const text = validateText(input.text);
  const grounded = new Set(session.groundedNumbers || []);
  collectTextNumbers(text, grounded);

  const history = trimHistory(session.messages || []);
  const note = inputNote({
    inputMode: input.inputMode,
    inputLang: input.inputLang,
    preferredLang: input.preferredLang,
    mobilityProfile: input.options && input.options.mobilityProfile,
  });
  const userMessage = {
    role: "user",
    content: [...(note ? [{ type: "text", text: note }] : []), { type: "text", text }],
  };
  const messages = [...history, userMessage];
  const baseLength = messages.length - 1; // where this turn's exchange starts

  const turn = { declaredLang: null, lastRoute: null, travel: null, reportFiled: false };
  const handlers = createToolHandlers({
    graph: deps.graph,
    places: deps.places,
    store: deps.store,
    routing: deps.routing,
    phrases: deps.phrases,
    options: input.options || {},
    session,
    turn,
    userId: input.userId || null,
    now: deps.now || Date.now,
  });

  let final = null;
  for (let i = 0; i < MAX_ITERATIONS; i++) {
    const response = await client.messages.create({
      model: MODEL,
      max_tokens: MAX_TOKENS,
      system: SYSTEM_PROMPT,
      tools: TOOLS,
      messages,
    });
    messages.push({ role: "assistant", content: response.content });

    const toolUses = response.content.filter((b) => b.type === "tool_use");
    if (response.stop_reason !== "tool_use") {
      // A tool call cut off by max_tokens is never executed
      final = toolUses.length === 0 ? response : null;
      break;
    }

    const results = await Promise.all(
      toolUses.map(async (b) => {
        let result;
        try {
          const fn = handlers[b.name];
          result = fn ? await fn(b.input || {}) : { error: "unknown_tool", message: `No tool named ${b.name}` };
        } catch (e) {
          result = { error: "tool_failed", message: "The tool failed.", instruction: "Tell the user you can't do that right now." };
        }
        collectNumbers(result, grounded);
        return { type: "tool_result", tool_use_id: b.id, content: JSON.stringify(result), ...(result.error ? { is_error: true } : {}) };
      })
    );
    messages.push({ role: "user", content: results });
  }

  let reply;
  let guardTriggered = false;
  let route = null;
  let reportFiled = turn.reportFiled;

  if (!final) {
    // Drop this turn's partial exchange so the stored history stays valid
    messages.length = baseLength;
    reply = INCOMPLETE_REPLY;
  } else {
    reply = textOf(final.content);
    if (findUngrounded(reply, grounded).length) {
      guardTriggered = true;
      reply = SAFE_REPLY;
      // The history must match what the user saw
      messages[messages.length - 1] = { role: "assistant", content: [{ type: "text", text: SAFE_REPLY }] };
    } else if (turn.lastRoute) {
      route = { ...turn.lastRoute };
      if (turn.travel && turn.travel.distance_m === route.distanceM) {
        route.travelTime = { minutes: turn.travel.minutes, basis: "estimate" };
      }
    }
    if (!reply) reply = INCOMPLETE_REPLY;
  }

  const language = resolveLanguage({
    preferredLang: input.preferredLang,
    declaredLang: turn.declaredLang,
    inputLang: input.inputLang,
  });

  session.messages = messages;
  session.groundedNumbers = [...grounded].slice(-GROUNDED_NUMBERS_CAP);
  if (route) session.cards = [...(session.cards || []), { routeId: route.routeId, to: route.to }].slice(-CARDS_KEEP);

  return { reply, language, route, reportFiled, guardTriggered, incomplete: !final };
}

module.exports = { runTurn, BadInput, validateText, inputNote, MAX_TEXT };
