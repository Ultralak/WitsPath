"use strict";

const GRAPH_TTL_MS = 60 * 1000;

/** Firestore implementation of the store the loop and tools use. */
function createFirestoreStore(admin) {
  const db = admin.firestore();
  const FieldValue = admin.firestore.FieldValue;
  let graphCache = null;

  async function readCollection(name, idField) {
    const snap = await db.collection(name).get();
    return snap.docs.map((d) => ({ ...d.data(), ...(idField && !d.data()[idField] ? { [idField]: d.id } : {}) }));
  }

  return {
    async loadGraph() {
      if (graphCache && Date.now() - graphCache.at < GRAPH_TTL_MS) return graphCache.graph;
      const [floors, nodes, edges] = await Promise.all([
        readCollection("floors", "floorId"),
        readCollection("nodes", "nodeId"),
        readCollection("edges", "edgeId"),
      ]);
      const graph = { floors, nodes, edges };
      graphCache = { at: Date.now(), graph };
      return graph;
    },

    async getSession(id) {
      const d = await db.collection("chat_sessions").doc(id).get();
      return d.exists ? d.data() : null;
    },

    async saveSession(id, data) {
      await db.collection("chat_sessions").doc(id).set({ ...data, updatedAt: FieldValue.serverTimestamp() });
    },

    async getPathStatus(nodeIds) {
      if (!nodeIds.length) return {};
      const refs = [...new Set(nodeIds)].map((id) => db.collection("path_status").doc(id));
      const docs = await db.getAll(...refs);
      const out = {};
      for (const d of docs) {
        if (d.exists) {
          const v = d.data();
          out[d.id] = {
            blocked: v.blocked === true,
            reason: v.reason || "",
            reportedAt: v.reportedAt && v.reportedAt.toDate ? v.reportedAt.toDate().toISOString() : null,
          };
        }
      }
      return out;
    },

    async addReport(report) {
      const ref = await db.collection("reports").add({ ...report, timestamp: FieldValue.serverTimestamp() });
      return ref.id;
    },

    // Equality filter only, so no composite index is needed; the time window is applied here
    async getRecentEdgeReports(edgeId, sinceMs) {
      const snap = await db.collection("reports").where("edgeId", "==", edgeId).limit(500).get();
      return snap.docs
        .map((d) => d.data())
        .filter((r) => r.timestamp && r.timestamp.toMillis() >= sinceMs)
        .map((r) => ({ userId: r.userId || null }));
    },

    async flagEdge(edgeId) {
      const ref = db.collection("edges").doc(edgeId);
      return db.runTransaction(async (tx) => {
        const d = await tx.get(ref);
        if (!d.exists) return false; // never create edges
        const status = String(d.data().status || "ok").toLowerCase();
        if (status !== "ok") return false; // only flag an edge that is currently ok
        tx.update(ref, { status: "flagged", flaggedAt: FieldValue.serverTimestamp() });
        return true;
      });
    },

    async getPhrases(lang) {
      const snap = await db.collection("phrase_templates").doc(lang).collection("phrases").get();
      const out = {};
      for (const d of snap.docs) {
        const v = d.data();
        out[d.id] = { text: v.text || "", verifiedBy: v.verifiedBy || "" };
      }
      return out;
    },
  };
}

module.exports = { createFirestoreStore };
