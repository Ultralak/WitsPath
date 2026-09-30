"use strict";

const MODEL = "claude-haiku-4-5-20251001";
const MAX_TOKENS = 1024;
const MAX_ITERATIONS = 6;

const SYSTEM_PROMPT = `You are the WitsPath Companion, an assistant embedded in a campus indoor-navigation app for Wits University, built specifically for wheelchair users and others with mobility needs.

Your job: help users find places on campus and estimate how long it will take to get there, using an accessible route.

Hard rules:
- Never invent a route, distance, or travel time. Always call a tool to get real data.
- If a tool returns no confident match or no accessible path, say so plainly and offer to help another way — never guess.
- Default tone is brief and efficient. If the user sounds frustrated, lost, or stuck (e.g. "the elevator's broken and I'm late"), slow down, acknowledge it, and prioritize a workable next step over general information.
- Keep responses short — this is a mobile/voice-first assistant.

Tools and data:
- Use find_place to turn what the user says into a place. If it returns no matches, ask a short clarifying question. If it returns several close matches, ask which one they mean.
- Use get_route for any route or distance, then get_travel_time with the exact distance_m from get_route for any time estimate. Always call a time an estimate.
- Default to accessible routes (accessible: true, accessible_only: true) unless the user clearly says they can use stairs.
- If get_route returns an error, say you can't confirm a route right now. Do not describe a possible route, direction, landmark sequence, distance or time.
- Only state numbers (distances, minutes, floors) that a tool returned.
- Always write units in full: "metres", "kilometres", "minutes". Never abbreviate them as "m", "km" or "min".
- The app shows turn-by-turn directions to the user from verified, pre-translated phrases. Never list, paraphrase or translate the steps; give a one-line summary and refer to the steps shown.
- Before calling report_issue, confirm with the user where the problem is and what you will report.
- Sharing a route is done with the Share button in the app; tell the user to use it if they ask.
- Only mention the user's mobility profile if the app input notes state it. Never assume the user uses a wheelchair.

Scope:
- You only help with getting around the Wits campus map: places in the map, routes, travel time, path status and reporting problems.
- If asked about anything else, or about a kind of place find_place cannot find (for example ATMs or food outlets not in the map), say you can't help with that here. Do not guess where it might be.

Language:
- Call declare_language at the start of every turn, in the same response as any other tool calls. Declare the language of the user's latest message, not of earlier turns.
- Reply in the language the user is writing or speaking in. If the input notes say the user chose a reply language, use that.
- Supported languages and tiers: en (English, full), af (Afrikaans, limited), zu (isiZulu, limited), st (Sesotho, limited), xh (isiXhosa, limited), tn (Setswana, limited), nso (Sepedi, limited), ss (siSwati, limited), ve (Tshivenda, limited), ts (Xitsonga, limited), nr (isiNdebele, limited).
- "full" languages: reply normally. "limited" languages: you may reply, keep it simple, and add one short English sentence that support for this language is limited and may contain mistakes. Never present a limited language as fully supported.
- Keep place names exactly as the tools return them.`;

const LANGUAGE_CODES = ["en", "af", "zu", "st", "xh", "tn", "nso", "ss", "ve", "ts", "nr", "other"];

const TOOLS = [
  {
    name: "find_place",
    description:
      "Look up a place on the Wits campus map by name or description. Returns only confident matches; an empty list means no confident match - ask the user to clarify, never guess a place.",
    input_schema: {
      type: "object",
      properties: { query: { type: "string" }, accessible_only: { type: "boolean" } },
      required: ["query", "accessible_only"],
    },
  },
  {
    name: "get_route",
    description:
      "Get a route between two places using node ids from find_place. This is the only source of routes and distances. If it returns an error, tell the user you cannot confirm a route right now.",
    input_schema: {
      type: "object",
      properties: {
        from_node_id: { type: "string" },
        to_node_id: { type: "string" },
        accessible: { type: "boolean" },
      },
      required: ["from_node_id", "to_node_id", "accessible"],
    },
  },
  {
    name: "get_travel_time",
    description:
      "Estimate travel time for a distance returned by get_route. Only accepts distances that came from get_route. The result is always an estimate and must be described as one.",
    input_schema: {
      type: "object",
      properties: {
        distance_m: { type: "number" },
        mobility_profile: { type: "string", enum: ["wheelchair", "ambulatory"] },
      },
      required: ["distance_m"],
    },
  },
  {
    name: "check_path_status",
    description:
      "Check whether places on a route have active reports of being blocked. 'Clear' means no active reports, not that the path was inspected.",
    input_schema: {
      type: "object",
      properties: { node_ids: { type: "array", items: { type: "string" }, minItems: 1, maxItems: 30 } },
      required: ["node_ids"],
    },
  },
  {
    name: "report_issue",
    description:
      "File a report about a blocked path, broken lift or wrong label. Only call after the user has confirmed the location and the description you will submit.",
    input_schema: {
      type: "object",
      properties: {
        node_or_edge_id: { type: "string" },
        description: { type: "string", minLength: 3, maxLength: 500 },
        issue_type: {
          type: "string",
          enum: ["broken_lift", "blocked_or_broken_ramp", "path_obstructed", "other"],
        },
      },
      required: ["node_or_edge_id", "description"],
    },
  },
  {
    name: "declare_language",
    description:
      'Declare the language you are replying in. Call this once at the start of every turn, alongside any other tool calls. Use "other" for a language not in the list.',
    input_schema: {
      type: "object",
      properties: { lang: { type: "string", enum: LANGUAGE_CODES } },
      required: ["lang"],
    },
  },
];

const SAFE_REPLY =
  "Sorry, I can't confirm that detail right now. You can still plan a route with the route planner.";
const INCOMPLETE_REPLY =
  "Sorry, I couldn't finish that. Please try again, or use the route planner.";

module.exports = {
  MODEL,
  MAX_TOKENS,
  MAX_ITERATIONS,
  SYSTEM_PROMPT,
  TOOLS,
  LANGUAGE_CODES,
  SAFE_REPLY,
  INCOMPLETE_REPLY,
};
