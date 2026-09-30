"use strict";

const STOPWORDS = new Set([
  "the", "a", "an", "of", "to", "at", "in", "on", "building", "bldg", "where", "is", "please", "take",
  "me", "find", "go", "how", "do", "i", "get", "can", "you", "nearest", "closest", "entrance", "and",
  "for", "my", "want", "need", "show", "which", "way", "route", "directions", "from",
]);

const MIN_SCORE = 0.6;
const MAX_MATCHES = 5;

/** Lower-case, no accents, only [a-z0-9 ]. */
function normalise(text) {
  return String(text || "")
    .normalize("NFD")
    .replace(/[̀-ͯ]/g, "")
    .toLowerCase()
    .replace(/[^a-z0-9 ]+/g, " ")
    .replace(/\s+/g, " ")
    .trim();
}

function tokens(text) {
  return normalise(text)
    .split(" ")
    .filter((t) => t && !STOPWORDS.has(t));
}

/** One typo apart (one substitution, insertion or deletion). */
function oneEditApart(a, b) {
  if (Math.abs(a.length - b.length) > 1) return false;
  let i = 0;
  let j = 0;
  let edits = 0;
  while (i < a.length && j < b.length) {
    if (a[i] === b[j]) {
      i++;
      j++;
    } else {
      if (++edits > 1) return false;
      if (a.length > b.length) i++;
      else if (b.length > a.length) j++;
      else {
        i++;
        j++;
      }
    }
  }
  return edits + (a.length - i) + (b.length - j) <= 1;
}

const isNumber = (t) => /^\d+$/.test(t);

function tokensMatch(q, n) {
  if (isNumber(q) || isNumber(n)) return q === n; // "CLM 2" must not match "CLM 3"
  if (q === n) return true;
  if (q.length >= 5 && n.length >= 5 && oneEditApart(q, n)) return true;
  if (q.length >= 4 && n.startsWith(q)) return true;
  if (n.length >= 4 && q.startsWith(n)) return true;
  return false;
}

/** 0.95 x F1 over matched tokens; an exact normalised match scores 1. */
function score(query, candidate) {
  if (normalise(query) === normalise(candidate)) return 1;
  const q = tokens(query);
  const c = tokens(candidate);
  if (!q.length || !c.length) return 0;
  const used = new Set();
  let matched = 0;
  for (const qt of q) {
    for (let i = 0; i < c.length; i++) {
      if (!used.has(i) && tokensMatch(qt, c[i])) {
        used.add(i);
        matched++;
        break;
      }
    }
  }
  if (!matched) return 0;
  const precision = matched / q.length;
  const recall = matched / c.length;
  return 0.95 * ((2 * precision * recall) / (precision + recall));
}

/** "School of Business Sciences (MSB)" gives "msb". */
function acronyms(name) {
  const out = [];
  const re = /\(([A-Za-z0-9]{2,8})\)/g;
  let m;
  while ((m = re.exec(name))) out.push(m[1]);
  return out;
}

/**
 * Places are labelled graph nodes that are not plain junctions or ramps.
 * @param nodes  documents from the nodes collection
 * @param aliases optional { nodeId: ["alias", ...] }
 */
function buildPlaces(nodes, aliases = {}) {
  const places = [];
  for (const n of nodes) {
    const label = String(n.label || "").trim();
    const type = String(n.type || "").toLowerCase();
    if (!label || type === "node" || type === "ramp") continue;
    places.push({
      id: n.nodeId,
      name: label,
      floor: n.floorId || null,
      building: n.building || "",
      names: [label, ...acronyms(label), ...(aliases[n.nodeId] || [])],
      accessibleEntrance: typeof n.accessibleEntrance === "boolean" ? n.accessibleEntrance : null,
    });
  }
  return places;
}

/** @returns {{matches: object[], entrance_accessibility_unverified: string[]}} */
function findPlaces(places, query, accessibleOnly) {
  const scored = [];
  for (const p of places) {
    if (accessibleOnly && p.accessibleEntrance === false) continue;
    const s = Math.max(...p.names.map((n) => score(query, n)));
    if (s >= MIN_SCORE) scored.push({ p, s });
  }
  scored.sort((a, b) => b.s - a.s || a.p.name.localeCompare(b.p.name));
  const top = scored.slice(0, MAX_MATCHES);
  return {
    matches: top.map(({ p, s }) => ({
      id: p.id,
      name: p.name,
      building: p.building,
      floor: p.floor,
      confidence: Math.round(s * 100) / 100,
    })),
    entrance_accessibility_unverified: top.filter(({ p }) => p.accessibleEntrance === null).map(({ p }) => p.id),
  };
}

module.exports = { normalise, tokens, score, buildPlaces, findPlaces, oneEditApart, MIN_SCORE };
