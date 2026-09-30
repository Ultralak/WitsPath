"use strict";

const graph = {
  floors: [{ floorId: "f1", name: "West Campus", level: 0, imageWidth: 647, imageHeight: 717, metresPerPixel: 1 }],
  nodes: [
    { nodeId: "lib", floorId: "f1", type: "entrance", label: "Commerce Library", x: 0, y: 0 },
    { nodeId: "msb", floorId: "f1", type: "entrance", label: "School of Business Sciences (MSB)", x: 68, y: 0 },
    { nodeId: "clm2", floorId: "f1", type: "entrance", label: "CLM 2", x: 100, y: 0 },
    { nodeId: "clm3", floorId: "f1", type: "entrance", label: "CLM 3", x: 130, y: 0 },
    { nodeId: "j1", floorId: "f1", type: "node", label: "", x: 30, y: 0 },
  ],
  edges: [
    { edgeId: "e1", fromNodeId: "lib", toNodeId: "j1", distance: 30.2, status: "ok" },
    { edgeId: "e2", fromNodeId: "j1", toNodeId: "msb", distance: 38.2, status: "ok" },
  ],
};

/** A routing fake that answers like the routing-service for the small graph above. */
function fakeRouting(overrides = {}) {
  return {
    calls: [],
    async route(req) {
      this.calls.push(req);
      if (overrides.throws) throw new Error("engine down");
      if (overrides.response) return overrides.response;
      const order = ["lib", "j1", "msb"];
      const a = order.indexOf(req.fromNodeId);
      const b = order.indexOf(req.toNodeId);
      if (a < 0 || b < 0) return { ok: false, error: "no_route", message: "Failed to find the destination node." };
      const ids = a < b ? order.slice(a, b + 1) : order.slice(b, a + 1).reverse();
      return {
        ok: true,
        path: ids.map((id) => ({ node_id: id, name: graph.nodes.find((n) => n.nodeId === id).label || id, x: 1, y: 2 })),
        edgeIds: [],
        distanceM: 68.4,
        seconds: 110,
        minutes: 2,
        stepFree: true,
        steps: [{ phraseKey: "start_at", params: {}, text: "Start at X.", lang: "en", fallback: false, pointIndex: 0 }],
        directionsLang: "en",
        fallbackToEnglish: false,
      };
    },
  };
}

function memoryStore(initial = {}) {
  const sessions = new Map(Object.entries(initial.sessions || {}));
  const reports = [...(initial.reports || [])];
  const edgeStatus = new Map(graph.edges.map((e) => [e.edgeId, e.status]));
  const store = {
    reports,
    sessions,
    pathStatus: initial.pathStatus || {},
    flagged: [],
    async loadGraph() {
      return { ...graph, edges: graph.edges.map((e) => ({ ...e, status: edgeStatus.get(e.edgeId) })) };
    },
    async getSession(id) {
      return sessions.get(id) || null;
    },
    async saveSession(id, data) {
      sessions.set(id, data);
    },
    async getPathStatus(ids) {
      const out = {};
      for (const id of ids) if (store.pathStatus[id]) out[id] = store.pathStatus[id];
      return out;
    },
    async addReport(r) {
      reports.push({ ...r, timestamp: r.timestamp || Date.now() });
      return `r${reports.length}`;
    },
    async getRecentEdgeReports(edgeId, sinceMs) {
      return reports.filter((r) => r.edgeId === edgeId && r.timestamp >= sinceMs).map((r) => ({ userId: r.userId || null }));
    },
    async flagEdge(edgeId) {
      if (edgeStatus.get(edgeId) !== "ok") return false;
      edgeStatus.set(edgeId, "flagged");
      store.flagged.push(edgeId);
      return true;
    },
    async getPhrases() {
      return store.phrases || {};
    },
  };
  return store;
}

const text = (t) => ({ type: "text", text: t });
const tool = (id, name, input) => ({ type: "tool_use", id, name, input });

/** A fake Anthropic client that plays back scripted responses and records what it was sent. */
function scriptedClient(script) {
  const calls = [];
  let i = 0;
  return {
    calls,
    messages: {
      async create(params) {
        calls.push(JSON.parse(JSON.stringify(params)));
        const step = script[Math.min(i++, script.length - 1)];
        const r = typeof step === "function" ? step(params) : step;
        return { stop_reason: r.stop_reason || "end_turn", content: r.content };
      },
    },
  };
}

const say = (t) => ({ stop_reason: "end_turn", content: [text(t)] });
const call = (...tools) => ({ stop_reason: "tool_use", content: tools });

module.exports = { graph, fakeRouting, memoryStore, scriptedClient, say, call, tool, text };
