"use strict";

// Only English is "full" until a native speaker has reviewed real replies in the others.
const LANGUAGES = {
  en: { name: "English", tier: "full" },
  af: { name: "Afrikaans", tier: "limited" },
  zu: { name: "isiZulu", tier: "limited" },
  st: { name: "Sesotho", tier: "limited" },
  xh: { name: "isiXhosa", tier: "limited" },
  tn: { name: "Setswana", tier: "limited" },
  nso: { name: "Sepedi", tier: "limited" },
  ss: { name: "siSwati", tier: "limited" },
  ve: { name: "Tshivenda", tier: "limited" },
  ts: { name: "Xitsonga", tier: "limited" },
  nr: { name: "isiNdebele", tier: "limited" },
};

const GUIDANCE = {
  full: "Supported language. Reply normally in this language.",
  limited:
    "Limited, unverified support. Reply in this language if you can, keep it very simple, and add one short sentence in English saying support for this language is limited.",
  unsupported:
    "Reply in English, briefly acknowledging you cannot yet reply reliably in their language.",
};

/** Base code such as "zu" from "zu-ZA". Returns null for anything not supported. */
function normaliseCode(code) {
  if (typeof code !== "string") return null;
  const base = code.trim().toLowerCase().split(/[-_]/)[0];
  return LANGUAGES[base] ? base : null;
}

function tierOf(code) {
  return LANGUAGES[code] ? LANGUAGES[code].tier : "unsupported";
}

/** Result of the declare_language tool. */
function declare(lang) {
  const code = normaliseCode(lang);
  if (!code) {
    return { lang: "other", name: "Other", tier: "unsupported", guidance: GUIDANCE.unsupported };
  }
  const { name, tier } = LANGUAGES[code];
  return { lang: code, name, tier, guidance: GUIDANCE[tier] };
}

/**
 * Which language to report to the app. The user's own choice beats the model's declaration,
 * which beats the speech recogniser.
 */
function resolveLanguage({ preferredLang, declaredLang, inputLang }) {
  const chosen = normaliseCode(preferredLang);
  if (chosen) return { code: chosen, source: "user", tier: tierOf(chosen) };
  if (declaredLang === "other") return { code: "other", source: "model", tier: "unsupported" };
  const declared = normaliseCode(declaredLang);
  if (declared) return { code: declared, source: "model", tier: tierOf(declared) };
  const heard = normaliseCode(inputLang);
  if (heard) return { code: heard, source: "speech", tier: tierOf(heard) };
  return { code: null, source: "unconfirmed", tier: "unconfirmed" };
}

module.exports = { LANGUAGES, GUIDANCE, normaliseCode, tierOf, declare, resolveLanguage };
