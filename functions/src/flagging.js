"use strict";

const FLAG_THRESHOLD = 3;
const WINDOW_MS = 30 * 24 * 60 * 60 * 1000;

/**
 * When 3 distinct signed-in users report the same edge within 30 days, flag it. Anonymous reports are
 * saved but do not count. Only an edge whose status is "ok" is flagged, and only the team clears a flag.
 * Idempotent: safe to call from both the tool and the Firestore onCreate trigger.
 *
 * @param store needs getRecentEdgeReports(edgeId, sinceMs) and flagEdge(edgeId)
 * @returns true if this call flagged the edge
 */
async function maybeFlagEdge(store, edgeId, nowMs = Date.now()) {
  if (!edgeId) return false;
  const reports = await store.getRecentEdgeReports(edgeId, nowMs - WINDOW_MS);
  const users = new Set();
  for (const r of reports) {
    if (r.userId) users.add(r.userId);
  }
  if (users.size < FLAG_THRESHOLD) return false;
  return store.flagEdge(edgeId);
}

module.exports = { maybeFlagEdge, FLAG_THRESHOLD, WINDOW_MS };
