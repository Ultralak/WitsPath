"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");

const { handleMessage } = require("../src/handler");
const { SAFE_REPLY, INCOMPLETE_REPLY, MAX_ITERATIONS } = require("../src/prompt");
const { maybeFlagEdge, WINDOW_MS } = require("../src/flagging");
const { findPlaces, buildPlaces } = require("../src/places");
const { findUngrounded, collectNumbers, collectTextNumbers } = require("../src/guard");
const { resolveLanguage, declare } = require("../src/languages");
const { trimHistory } = require("../src/session");
const { graph, fakeRouting, memoryStore, scriptedClient, say, call, tool, text } = require("./helpers");

const DAY = 24 * 60 * 60 * 1000;

function send(script, body = {}, { store = memoryStore(), routing = fakeRouting(), identity = null } = {}) {
  const client = scriptedClient(script);
  return handleMessage({
    body: { text: "Take me to the Commerce Library", ...body },
    identity,
    deps: { client, store, routing },
  }).then((out) => ({ ...out, client, store, routing }));
}

const goodRoute = (toolId = "t2") => call(
  tool("t1", "declare_language", { lang: "en" }),
  tool(toolId, "get_route", { from_node_id: "msb", to_node_id: "lib", accessible: true })
);

// 1. No hallucination
test("a reply with an ungrounded number is replaced by the safe reply", async () => {
  const out = await send([say("It's 250 metres away.")]);
  assert.equal(out.body.reply, SAFE_REPLY);
  assert.equal(out.body.guardTriggered, true);
  assert.equal(out.body.route, null);
});

test("the stored history matches what the user saw after the guard fires", async () => {
  const out = await send([say("It's 250 metres away.")]);
  const stored = JSON.parse(out.store.sessions.get(out.body.sessionId).messagesJson);
  assert.equal(stored[stored.length - 1].content[0].text, SAFE_REPLY);
});

test("numbers that a tool returned are allowed, in any sensible form", async () => {
  const out = await send([goodRoute(), say("It is 68.4 metres, about 2 minutes (an estimate).")]);
  // get_travel_time was not called, but 2 is <= 10 and 68.4 came from get_route
  assert.equal(out.body.guardTriggered, false);
  assert.match(out.body.reply, /68\.4/);
});

test("kilometre forms of a grounded distance pass", () => {
  const grounded = collectNumbers({ distance_m: 1234 }, new Set());
  assert.deepEqual(findUngrounded("That is 1.2 kilometres.", grounded), []);
  assert.deepEqual(findUngrounded("That is 1234 metres.", grounded), []);
  assert.deepEqual(findUngrounded("That is 999 metres.", grounded), [999]);
});

test("digits inside ids do not ground numbers, but digits in names do", () => {
  const grounded = collectNumbers({ path: [{ node_id: "nd_mu83zm0ga", name: "Ramp 25" }] }, new Set());
  assert.deepEqual(findUngrounded("Go 83 metres", grounded), [83]);
  assert.deepEqual(findUngrounded("Take Ramp 25", grounded), []);
});

test("numbers the user wrote are grounded", () => {
  const grounded = collectTextNumbers("I am in room 204", new Set());
  assert.deepEqual(findUngrounded("Room 204 is on floor 2", grounded), []);
});

// 2. Ungrounded distance
test("get_travel_time with a distance get_route never returned is rejected", async () => {
  const out = await send([
    call(tool("t1", "declare_language", { lang: "en" }), tool("t2", "get_travel_time", { distance_m: 250 })),
    say("Sorry, I can't give a time without a route."),
  ]);
  const secondCall = out.client.calls[1];
  const results = secondCall.messages[secondCall.messages.length - 1].content;
  const tt = results.find((r) => r.tool_use_id === "t2");
  assert.equal(tt.is_error, true);
  assert.equal(JSON.parse(tt.content).error, "ungrounded_distance");
});

test("travel time uses the engine's own estimate and is attached to the route card", async () => {
  const out = await send([
    goodRoute(),
    call(tool("t3", "get_travel_time", { distance_m: 68.4 })),
    say("About 2 minutes, an estimate. The steps are shown in the app."),
  ]);
  assert.equal(out.body.route.travelTime.minutes, 2);
  assert.equal(out.body.route.travelTime.basis, "estimate");
  assert.equal(out.body.route.distanceM, 68.4);
  assert.equal(out.body.route.from, "School of Business Sciences (MSB)");
});

test("no travel time on the card when get_travel_time was not called", async () => {
  const out = await send([goodRoute(), say("Route is ready.")]);
  assert.equal(out.body.route.travelTime, null);
});

// 3. Out of scope
test("find_place for an ATM returns no matches", () => {
  const places = buildPlaces(graph.nodes);
  assert.deepEqual(findPlaces(places, "nearest ATM", true).matches, []);
});

test("find_place matches names, acronyms and typos, and keeps numbers exact", () => {
  const places = buildPlaces(graph.nodes);
  assert.equal(findPlaces(places, "where is the commerce library", true).matches[0].id, "lib");
  assert.equal(findPlaces(places, "MSB", true).matches[0].id, "msb");
  assert.equal(findPlaces(places, "comerce librery", true).matches[0].id, "lib");
  assert.equal(findPlaces(places, "CLM 2", true).matches[0].id, "clm2");
  assert.ok(!findPlaces(places, "CLM 2", true).matches.some((m) => m.id === "clm3"));
});

test("places exclude plain junctions, and the graph has no entrance data so all are unverified", () => {
  const places = buildPlaces(graph.nodes);
  assert.ok(!places.some((p) => p.id === "j1"));
  const r = findPlaces(places, "Commerce Library", true);
  assert.deepEqual(r.entrance_accessibility_unverified, ["lib"]);
});

test("accessible_only drops places known to have no accessible entrance", () => {
  const nodes = [{ ...graph.nodes[0], accessibleEntrance: false }];
  assert.deepEqual(findPlaces(buildPlaces(nodes), "Commerce Library", true).matches, []);
  assert.equal(findPlaces(buildPlaces(nodes), "Commerce Library", false).matches.length, 1);
});

// 4. Route failures never produce a card
async function routeFailure(routing, store) {
  const out = await send(
    [goodRoute(), say("I can't confirm a route right now.")],
    {},
    { routing, store }
  );
  const followUp = out.client.calls[1];
  const result = followUp.messages[followUp.messages.length - 1].content.find((r) => r.tool_use_id === "t2");
  return { out, result: JSON.parse(result.content), isError: result.is_error };
}

test("engine down: error result, no route card", async () => {
  const { out, result, isError } = await routeFailure(fakeRouting({ throws: true }));
  assert.equal(isError, true);
  assert.equal(result.error, "route_unavailable");
  assert.match(result.instruction, /can't confirm a route/);
  assert.equal(out.body.route, null);
});

test("a blocked node reported in path_status means route_blocked", async () => {
  const store = memoryStore({ pathStatus: { j1: { blocked: true, reason: "ramp closed" } } });
  const { out, result } = await routeFailure(fakeRouting(), store);
  assert.equal(result.error, "route_blocked");
  assert.equal(out.body.route, null);
});

test("no step-free route from the engine is reported as no_accessible_route", async () => {
  const { out, result } = await routeFailure(fakeRouting({ response: { ok: false, error: "no_route", message: "x" } }));
  assert.equal(result.error, "no_accessible_route");
  assert.equal(out.body.route, null);
});

test("an engine answer that does not match the graph is not trusted", async () => {
  const bad = fakeRouting({
    response: {
      ok: true,
      path: [{ node_id: "msb" }, { node_id: "lib" }], // no direct edge exists
      distanceM: 5, seconds: 10, stepFree: true, steps: [],
    },
  });
  const { result, out } = await routeFailure(bad);
  assert.equal(result.error, "route_unverified");
  assert.equal(out.body.route, null);
});

test("a wrong distance from the engine is caught by the recomputation", async () => {
  const r = fakeRouting();
  const ok = await r.route({ fromNodeId: "msb", toNodeId: "lib" });
  const lying = fakeRouting({ response: { ...ok, distanceM: 10 } });
  const { result } = await routeFailure(lying);
  assert.equal(result.error, "route_unverified");
});

test("unknown place ids are rejected", async () => {
  const out = await send([
    call(tool("t1", "declare_language", { lang: "en" }), tool("t2", "get_route", { from_node_id: "nope", to_node_id: "lib", accessible: true })),
    say("Which place do you mean?"),
  ]);
  const last = out.client.calls[1].messages.at(-1).content.find((r) => r.tool_use_id === "t2");
  assert.equal(JSON.parse(last.content).error, "unknown_place");
});

test("the user's mobility settings are sent to the routing engine", async () => {
  const out = await send(
    [goodRoute(), say("Route is ready.")],
    { mobilityProfile: "walking-aid", preferLifts: true, avoidSteepRamps: true, speedMultiplier: 9 }
  );
  const req = out.routing.calls[0];
  assert.equal(req.options.mobilityProfile, "walking_aid");
  assert.equal(req.options.preferLifts, true);
  assert.equal(req.options.avoidSteepRamps, true);
  assert.equal(req.options.speedMultiplier, 2);
});

// 5. Language
test("language priority: the user's choice beats the model, which beats the speech recogniser", () => {
  assert.deepEqual(resolveLanguage({ preferredLang: "zu", declaredLang: "en", inputLang: "af" }), { code: "zu", source: "user", tier: "limited" });
  assert.deepEqual(resolveLanguage({ declaredLang: "en", inputLang: "af" }), { code: "en", source: "model", tier: "full" });
  assert.deepEqual(resolveLanguage({ inputLang: "af-ZA" }), { code: "af", source: "speech", tier: "limited" });
  assert.deepEqual(resolveLanguage({}), { code: null, source: "unconfirmed", tier: "unconfirmed" });
});

test("tiers: isiXhosa is limited, French is unsupported, only English is full", () => {
  assert.equal(declare("xh").tier, "limited");
  assert.equal(declare("fr").tier, "unsupported");
  assert.equal(declare("other").lang, "other");
  assert.equal(declare("en").tier, "full");
  assert.equal(declare("zu").tier, "limited");
});

test("the language in the response follows the declare_language call", async () => {
  const out = await send([
    call(tool("t1", "declare_language", { lang: "xh" })),
    say("Molo. Support for isiXhosa is limited and may contain mistakes."),
  ]);
  assert.deepEqual(out.body.language, { code: "xh", source: "model", tier: "limited" });
});

// 6. Phrase gating: the engine decides what may be shown; the function forwards the stored phrases
test("stored phrases are forwarded to the routing engine for the user's language", async () => {
  const store = memoryStore();
  store.phrases = { turn_left: { text: "Jika ngasekhohlo", verifiedBy: "" } };
  const out = await send([goodRoute(), say("Route is ready.")], { preferredLang: "zu" }, { store });
  const req = out.routing.calls[0];
  assert.equal(req.lang, "zu");
  assert.deepEqual(req.phrases.turn_left, { text: "Jika ngasekhohlo", verifiedBy: "" });
});

test("English needs no phrases", async () => {
  const out = await send([goodRoute(), say("Route is ready.")]);
  assert.equal(out.routing.calls[0].lang, "en");
});

// 7. Flagging
const report = (userId, edgeId, ageMs = 0) => ({ userId, edgeId, nodeId: null, timestamp: Date.now() - ageMs });

test("three distinct signed-in users flag the edge", async () => {
  const store = memoryStore({ reports: [report("u1", "e1"), report("u2", "e1"), report("u3", "e1")] });
  assert.equal(await maybeFlagEdge(store, "e1"), true);
  assert.deepEqual(store.flagged, ["e1"]);
});

test("three reports from one user, anonymous reports and old reports do not flag", async () => {
  for (const reports of [
    [report("u1", "e1"), report("u1", "e1"), report("u1", "e1")],
    [report("u1", "e1"), report(null, "e1"), report(null, "e1"), report(null, "e1")],
    [report("u1", "e1"), report("u2", "e1"), report("u3", "e1", WINDOW_MS + DAY)],
  ]) {
    const store = memoryStore({ reports });
    assert.equal(await maybeFlagEdge(store, "e1"), false);
    assert.deepEqual(store.flagged, []);
  }
});

test("flagging is idempotent and only touches edges that are ok", async () => {
  const store = memoryStore({ reports: [report("u1", "e1"), report("u2", "e1"), report("u3", "e1")] });
  assert.equal(await maybeFlagEdge(store, "e1"), true);
  assert.equal(await maybeFlagEdge(store, "e1"), false);
  assert.equal(await maybeFlagEdge(store, "missing"), false);
});

test("report_issue saves the report and flags on the third distinct signed-in user", async () => {
  const store = memoryStore({ reports: [report("u1", "e1"), report("u2", "e1")] });
  const out = await send(
    [
      call(tool("t1", "declare_language", { lang: "en" }), tool("t2", "report_issue", { node_or_edge_id: "e1", description: "Ramp is blocked by a bin", issue_type: "blocked_or_broken_ramp" })),
      say("Thanks, I've filed the report."),
    ],
    {},
    { store, identity: { uid: "u3", anonymous: false } }
  );
  assert.equal(out.body.reportFiled, true);
  assert.deepEqual(store.flagged, ["e1"]);
  const saved = store.reports.at(-1);
  assert.equal(saved.source, "android-companion");
  assert.equal(saved.userId, "u3");
});

test("an anonymous report is saved without a user id and does not flag", async () => {
  const store = memoryStore({ reports: [report("u1", "e1"), report("u2", "e1")] });
  const out = await send(
    [call(tool("t1", "report_issue", { node_or_edge_id: "e1", description: "Blocked by a bin" })), say("Filed.")],
    {},
    { store, identity: { uid: "anon1", anonymous: true } }
  );
  assert.equal(store.reports.at(-1).userId, null);
  assert.deepEqual(store.flagged, []);
  assert.equal(out.body.reportFiled, true);
});

test("report_issue rejects unknown ids and bad descriptions", async () => {
  const out = await send([
    call(tool("t1", "report_issue", { node_or_edge_id: "zzz", description: "Blocked" }), tool("t2", "report_issue", { node_or_edge_id: "e1", description: "x" })),
    say("I couldn't file that."),
  ]);
  const results = out.client.calls[1].messages.at(-1).content;
  assert.equal(JSON.parse(results[0].content).error, "unknown_place");
  assert.equal(JSON.parse(results[1].content).error, "bad_input");
  assert.equal(out.store.reports.length, 0);
});

// 9. Iteration cap
test("a model that keeps calling tools gets the incomplete reply and valid stored history", async () => {
  const loop = call(tool("tx", "find_place", { query: "library", accessible_only: true }));
  const out = await send([loop]);
  assert.equal(out.body.reply, INCOMPLETE_REPLY);
  assert.equal(out.client.calls.length, MAX_ITERATIONS);
  const stored = JSON.parse(out.store.sessions.get(out.body.sessionId).messagesJson);
  assert.deepEqual(stored, []); // the partial exchange was dropped: no dangling tool_use
});

test("a tool call cut off by max_tokens is never executed", async () => {
  const cut = { stop_reason: "max_tokens", content: [tool("t1", "report_issue", { node_or_edge_id: "e1", description: "Blocked" })] };
  const store = memoryStore();
  const out = await send([cut], {}, { store });
  assert.equal(out.body.reply, INCOMPLETE_REPLY);
  assert.equal(store.reports.length, 0);
});

test("a tool that throws becomes tool_failed and does not crash the loop", async () => {
  const store = memoryStore();
  store.getPathStatus = async () => { throw new Error("boom"); };
  const out = await send([goodRoute(), say("I can't confirm a route right now.")], {}, { store });
  const r = out.client.calls[1].messages.at(-1).content.find((x) => x.tool_use_id === "t2");
  assert.equal(JSON.parse(r.content).error, "tool_failed");
  assert.equal(out.body.route, null);
});

// Sessions and input
test("history is cut only at a user text message", () => {
  const msgs = [];
  for (let i = 0; i < 12; i++) {
    msgs.push({ role: "user", content: [text(`q${i}`)] });
    msgs.push({ role: "assistant", content: [tool(`t${i}`, "find_place", {})] });
    msgs.push({ role: "user", content: [{ type: "tool_result", tool_use_id: `t${i}`, content: "{}" }] });
    msgs.push({ role: "assistant", content: [text("ok")] });
  }
  const trimmed = trimHistory(msgs, 10);
  assert.equal(trimmed.length, 40);
  assert.equal(trimmed[0].content[0].text, "q2");
});

test("a session owned by a signed-in user cannot be continued by someone else", async () => {
  const store = memoryStore();
  const first = await send([say("Hello.")], {}, { store, identity: { uid: "owner", anonymous: false } });
  const sid = first.body.sessionId;
  const other = await send([say("Hi.")], { sessionId: sid }, { store, identity: { uid: "someone-else", anonymous: false } });
  assert.notEqual(other.body.sessionId, sid);
  const same = await send([say("Hi again.")], { sessionId: sid }, { store, identity: { uid: "owner", anonymous: false } });
  assert.equal(same.body.sessionId, sid);
});

test("the input notes say what the app knows and are never written as the user's words", async () => {
  const out = await send([say("Hello.")], { inputMode: "voice", inputLang: "zu-ZA", preferredLang: "zu", mobilityProfile: "wheelchair" });
  const first = out.client.calls[0].messages.at(-1).content[0].text;
  assert.match(first, /^\[App input notes, not written by the user:/);
  assert.match(first, /input was spoken/);
  assert.match(first, /speech recognizer detected language "zu"/);
  assert.match(first, /chose "zu" as their reply language/);
  assert.match(first, /saved mobility profile is "wheelchair"/);
});

test("empty and over-long text is rejected", async () => {
  assert.equal((await send([say("x")], { text: "   " })).status, 400);
  assert.equal((await send([say("x")], { text: "a".repeat(1001) })).status, 400);
});

test("the API key never appears in the source the app or client could see", () => {
  const fs = require("fs");
  const path = require("path");
  const root = path.join(__dirname, "..", "src");
  for (const f of fs.readdirSync(root)) {
    const s = fs.readFileSync(path.join(root, f), "utf8");
    assert.ok(!/sk-ant-/.test(s), f);
  }
});
