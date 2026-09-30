"use strict";

const { findPlaces } = require("./places");
const { declare } = require("./languages");
const { maybeFlagEdge } = require("./flagging");

const ISSUE_TYPES = ["broken_lift", "blocked_or_broken_ramp", "path_obstructed", "other"];
const CANNOT_CONFIRM = "Tell the user you can't confirm a route right now. Do not describe one.";
const GROUNDED_ROUTES_KEEP = 10;
const DISTANCE_TOLERANCE = 0.05;
const RECOMPUTE_TOLERANCE = 0.15;

const fail = (error, message, instruction) => ({ error, message, ...(instruction ? { instruction } : {}) });

function indexGraph(graph) {
  const nodes = new Map(graph.nodes.map((n) => [n.nodeId, n]));
  const edges = new Map(graph.edges.map((e) => [e.edgeId, e]));
  return { nodes, edges };
}

function nameOf(node) {
  return String((node && node.label) || (node && node.nodeId) || "").trim();
}

function edgesBetween(graph, a, b) {
  return graph.edges.filter(
    (e) => (e.fromNodeId === a && e.toNodeId === b) || (e.fromNodeId === b && e.toNodeId === a)
  );
}

/**
 * The tool handlers for one turn. Every handler returns a result or { error, message, instruction }.
 * Nothing thrown here may crash the loop; the caller turns exceptions into { error: "tool_failed" }.
 *
 * ctx = { graph, places, store, routing, phrases, options, session, turn, userId, now }
 *   options: { mobilityProfile, preferLifts, avoidSteepRamps, speedMultiplier }
 *   session: { groundedRoutes: [{ distance_m, estimated_seconds }] }
 *   turn:    { declaredLang, lastRoute, travel, reportFiled }   (mutated)
 */
function createToolHandlers(ctx) {
  const { nodes, edges } = indexGraph(ctx.graph);

  async function find_place(input) {
    if (typeof input.query !== "string" || !input.query.trim()) {
      return fail("bad_input", "query is required");
    }
    return findPlaces(ctx.places, input.query, input.accessible_only !== false);
  }

  async function get_route(input) {
    const { from_node_id: from, to_node_id: to } = input;
    if (typeof from !== "string" || typeof to !== "string") return fail("bad_input", "from_node_id and to_node_id are required");
    if (from === to) return fail("same_place", "Start and destination are the same place.");
    if (!nodes.has(from) || !nodes.has(to)) {
      return fail("unknown_place", "Use node ids returned by find_place", CANNOT_CONFIRM);
    }
    const accessible = input.accessible !== false;

    let res;
    try {
      res = await ctx.routing.route({
        graph: ctx.graph,
        fromNodeId: from,
        toNodeId: to,
        options: {
          mobilityProfile: ctx.options.mobilityProfile,
          stepFree: accessible,
          preferLifts: !!ctx.options.preferLifts,
          avoidSteepRamps: !!ctx.options.avoidSteepRamps,
          speedMultiplier: ctx.options.speedMultiplier,
        },
        lang: ctx.phrases ? ctx.phrases.lang : "en",
        phrases: ctx.phrases ? ctx.phrases.phrases : undefined,
      });
    } catch (e) {
      return fail("route_unavailable", "The routing engine could not be reached.", CANNOT_CONFIRM);
    }
    if (!res.ok) {
      if (res.error === "no_route" && accessible) {
        return fail("no_accessible_route", "No step-free route was found between these places.", CANNOT_CONFIRM);
      }
      return fail(res.error === "same_place" ? "same_place" : "route_unavailable", res.message || "No route.", CANNOT_CONFIRM);
    }

    // Verify what the engine returned instead of trusting it
    const path = res.path || [];
    if (path.length < 2 || path[0].node_id !== from || path[path.length - 1].node_id !== to) {
      return fail("route_unverified", "The route did not start and end at the requested places.", CANNOT_CONFIRM);
    }
    let recomputed = 0;
    const edgeIds = [];
    for (let i = 0; i + 1 < path.length; i++) {
      const hop = edgesBetween(ctx.graph, path[i].node_id, path[i + 1].node_id);
      if (!hop.length) return fail("route_unverified", "The route used a segment that does not exist.", CANNOT_CONFIRM);
      const usable = hop.filter((e) => String(e.status || "ok").toLowerCase() === "ok");
      const best = (usable.length ? usable : hop).reduce((a, b) => (Number(b.distance) < Number(a.distance) ? b : a));
      recomputed += Number(best.distance);
      edgeIds.push(best.edgeId);
    }
    if (Math.abs(recomputed - res.distanceM) > RECOMPUTE_TOLERANCE) {
      return fail("route_unverified", "The route distance could not be verified.", CANNOT_CONFIRM);
    }
    const distance = Math.round(recomputed * 10) / 10;

    // Live overlay: never show a route as clear when part of it is reported blocked
    const bad = edgeIds.filter((id) => String((edges.get(id) || {}).status || "ok").toLowerCase() !== "ok");
    const status = await ctx.store.getPathStatus(path.map((p) => p.node_id));
    const blockedNodes = path.filter((p) => status[p.node_id] && status[p.node_id].blocked);
    if (bad.length || blockedNodes.length) {
      return {
        ...fail("route_blocked", "Part of this route is reported blocked.", CANNOT_CONFIRM),
        blocked_segments: [...bad, ...blockedNodes.map((p) => p.node_id)],
      };
    }
    if (accessible && !res.stepFree) {
      return fail("no_accessible_route", "The only route is not step-free.", CANNOT_CONFIRM);
    }

    ctx.session.groundedRoutes = [
      ...(ctx.session.groundedRoutes || []),
      { distance_m: distance, estimated_seconds: res.seconds },
    ].slice(-GROUNDED_ROUTES_KEEP);

    ctx.turn.lastRoute = {
      routeId: `${from}:${to}:${Date.now()}`,
      from: nameOf(nodes.get(from)),
      to: nameOf(nodes.get(to)),
      fromNodeId: from,
      toNodeId: to,
      distanceM: distance,
      accessible: !!res.stepFree,
      travelTime: null,
      path: path.map((p) => ({ node_id: p.node_id, name: p.name, x: p.x, y: p.y })),
      steps: res.steps || [],
      directionsLang: res.directionsLang || "en",
      fallbackToEnglish: !!res.fallbackToEnglish,
    };
    return {
      path: path.map((p) => ({ node_id: p.node_id, name: p.name })),
      distance_m: distance,
      accessible: !!res.stepFree,
      blocked_segments: [],
      note: "Turn-by-turn directions are shown in the app.",
    };
  }

  async function get_travel_time(input) {
    const d = Number(input.distance_m);
    const routes = [...(ctx.session.groundedRoutes || [])].reverse();
    const match = Number.isFinite(d) ? routes.find((r) => Math.abs(r.distance_m - d) <= DISTANCE_TOLERANCE) : null;
    if (!match) {
      return fail(
        "ungrounded_distance",
        "That distance did not come from get_route.",
        "Call get_route first and use its exact distance_m."
      );
    }
    const minutes = Math.max(1, Math.ceil(match.estimated_seconds / 60));
    ctx.turn.travel = { distance_m: match.distance_m, minutes };
    return { minutes, basis: "estimate", speed_multiplier: ctx.options.speedMultiplier || 1 };
  }

  async function check_path_status(input) {
    const ids = input.node_ids;
    if (!Array.isArray(ids) || ids.length < 1 || ids.length > 30 || ids.some((x) => typeof x !== "string")) {
      return fail("bad_input", "node_ids must be 1 to 30 strings");
    }
    const unknown = ids.filter((id) => !nodes.has(id));
    if (unknown.length) return fail("unknown_place", `Unknown ids: ${unknown.join(", ")}`, "Use node ids returned by find_place.");

    const status = await ctx.store.getPathStatus(ids);
    const issues = [];
    for (const id of ids) {
      const s = status[id];
      if (s && s.blocked) {
        issues.push({ node_id: id, name: nameOf(nodes.get(id)), reason: s.reason || "reported blocked", reported_at: s.reportedAt || null });
      }
    }
    const set = new Set(ids);
    for (const e of ctx.graph.edges) {
      const st = String(e.status || "ok").toLowerCase();
      if (st === "ok" || !(set.has(e.fromNodeId) || set.has(e.toNodeId))) continue;
      issues.push({
        edge_id: e.edgeId,
        name: `Path near ${nameOf(nodes.get(set.has(e.fromNodeId) ? e.fromNodeId : e.toNodeId))}`,
        reason: st === "flagged" ? "path flagged by several user reports" : "path reported blocked",
        reported_at: null,
      });
    }
    return {
      clear: issues.length === 0,
      issues,
      note: "clear means there are no active reports, not that the path was inspected.",
    };
  }

  async function report_issue(input) {
    const id = input.node_or_edge_id;
    const description = typeof input.description === "string" ? input.description.trim() : "";
    if (description.length < 3 || description.length > 500) return fail("bad_input", "description must be 3 to 500 characters");
    const issueType = ISSUE_TYPES.includes(input.issue_type) ? input.issue_type : "other";
    const isEdge = edges.has(id);
    if (!isEdge && !nodes.has(id)) return fail("unknown_place", "Unknown node or edge id.", "Use ids returned by find_place or get_route.");

    const reportId = await ctx.store.addReport({
      userId: ctx.userId || null,
      edgeId: isEdge ? id : null,
      nodeId: isEdge ? null : id,
      issueType,
      description,
      source: "android-companion",
    });
    ctx.turn.reportFiled = true;
    if (isEdge) await maybeFlagEdge(ctx.store, id, ctx.now());
    return {
      report_id: reportId,
      note: ctx.userId
        ? "Report saved. A path is flagged when several different people report it."
        : "Report saved. Anonymous reports do not count toward flagging a path; signing in makes them count.",
    };
  }

  async function declare_language(input) {
    const result = declare(input.lang);
    ctx.turn.declaredLang = result.lang;
    return result;
  }

  return { find_place, get_route, get_travel_time, check_path_status, report_issue, declare_language };
}

module.exports = { createToolHandlers };
