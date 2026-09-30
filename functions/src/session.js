"use strict";

const HISTORY_USER_TURNS = 10;
const GROUNDED_NUMBERS_CAP = 500;
const CARDS_KEEP = 10;

/** A user message the person wrote, as opposed to one that only carries tool results. */
function isUserTextMessage(m) {
  if (!m || m.role !== "user") return false;
  if (typeof m.content === "string") return true;
  return Array.isArray(m.content) && m.content.some((b) => b.type === "text") && !m.content.some((b) => b.type === "tool_result");
}

/**
 * The last {@code n} user turns, cut only at a user text message so a tool_use is never separated from
 * its tool_result.
 */
function trimHistory(messages, n = HISTORY_USER_TURNS) {
  const starts = [];
  messages.forEach((m, i) => {
    if (isUserTextMessage(m)) starts.push(i);
  });
  if (starts.length <= n) {
    const first = starts.length ? starts[0] : messages.length;
    return messages.slice(first);
  }
  return messages.slice(starts[starts.length - n]);
}

function emptySession(ownerUid) {
  return { ownerUid: ownerUid || null, messages: [], groundedNumbers: [], groundedRoutes: [], cards: [] };
}

/** Decode a stored session. A session owned by a signed-in user can only be continued by that user. */
function openSession(stored, userId) {
  if (!stored) return { session: emptySession(userId), continued: false };
  if (stored.ownerUid && stored.ownerUid !== userId) return { session: emptySession(userId), continued: false };
  let messages = [];
  try {
    messages = JSON.parse(stored.messagesJson || "[]");
  } catch (e) {
    messages = [];
  }
  return {
    session: {
      ownerUid: stored.ownerUid || null,
      messages,
      groundedNumbers: stored.groundedNumbers || [],
      groundedRoutes: stored.groundedRoutes || [],
      cards: stored.cards || [],
    },
    continued: true,
  };
}

/** The shape written to chat_sessions/{id}. Messages are stored as JSON to avoid Firestore nesting limits. */
function serialiseSession(session, transcriptEntry) {
  return {
    ownerUid: session.ownerUid || null,
    messagesJson: JSON.stringify(session.messages),
    groundedNumbers: session.groundedNumbers.slice(-GROUNDED_NUMBERS_CAP),
    groundedRoutes: session.groundedRoutes,
    cards: session.cards.slice(-CARDS_KEEP),
    ...(transcriptEntry ? { lastTurn: transcriptEntry } : {}),
  };
}

module.exports = {
  isUserTextMessage,
  trimHistory,
  emptySession,
  openSession,
  serialiseSession,
  GROUNDED_NUMBERS_CAP,
  CARDS_KEEP,
};
