"use strict";

const NUMBER_RE = /\d+(?:[.,]\d+)?/g;

/** Numbers as written in some text, e.g. "68.4 metres" gives [68.4]. */
function extractNumbers(text) {
  if (typeof text !== "string") return [];
  return (text.match(NUMBER_RE) || []).map((n) => Number(n.replace(",", ".")));
}

/** Every way a model might reasonably write the same quantity (metres to kilometres, rounding). */
function forms(v) {
  const out = [v, Math.round(v), Math.floor(v), Math.ceil(v), Math.round(v * 10) / 10, v / 1000];
  out.push(Math.round(v / 100) / 10);
  return out;
}

const key = (n) => String(Math.round(n * 1e6) / 1e6);

/** Adds every number found anywhere in a tool result (including digits inside strings such as "Ramp 2"). */
function collectNumbers(value, into, parentKey = "") {
  if (value === null || value === undefined) return into;
  if (typeof value === "number") {
    for (const f of forms(value)) into.add(key(f));
  } else if (typeof value === "string") {
    // Ids such as "nd_mu83zm0ga" contain digits that are not quantities
    if (/id$/i.test(parentKey) || /ids$/i.test(parentKey)) return into;
    for (const n of extractNumbers(value)) for (const f of forms(n)) into.add(key(f));
  } else if (Array.isArray(value)) {
    for (const v of value) collectNumbers(v, into, parentKey);
  } else if (typeof value === "object") {
    for (const [k, v] of Object.entries(value)) collectNumbers(v, into, k);
  }
  return into;
}

/** Adds the numbers in the user's own words. */
function collectTextNumbers(text, into) {
  for (const n of extractNumbers(text)) for (const f of forms(n)) into.add(key(f));
  return into;
}

/**
 * The last line of defence. Integers up to 10 are always allowed ("one of 2 matches", "floor 1").
 * Any other number must have come from a tool result or the user.
 */
function findUngrounded(reply, grounded) {
  const bad = [];
  for (const n of extractNumbers(reply)) {
    if (Number.isInteger(n) && n <= 10) continue;
    if (!grounded.has(key(n))) bad.push(n);
  }
  return bad;
}

module.exports = { extractNumbers, collectNumbers, collectTextNumbers, findUngrounded, key };
