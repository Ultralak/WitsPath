package com.example.witspath.companion;

import java.util.Locale;

/**
 * The 11 companion languages and how well each is supported (B.2). Only English is "full" until a native
 * speaker has reviewed real replies in the others.
 */
public enum CompanionLanguage {
    EN("en", "English", true, "en-ZA"),
    AF("af", "Afrikaans", false, "af-ZA"),
    ZU("zu", "isiZulu", false, "zu-ZA"),
    ST("st", "Sesotho", false, "st-ZA"),
    XH("xh", "isiXhosa", false, null),
    TN("tn", "Setswana", false, null),
    NSO("nso", "Sepedi", false, null),
    SS("ss", "siSwati", false, null),
    VE("ve", "Tshivenda", false, null),
    TS("ts", "Xitsonga", false, null),
    NR("nr", "isiNdebele", false, null);

    public final String code;
    public final String displayName;
    public final boolean full;
    /** BCP-47 tag for speech recognition; only the four recogniser-supported languages have one. */
    public final String speechTag;

    CompanionLanguage(String code, String displayName, boolean full, String speechTag) {
        this.code = code;
        this.displayName = displayName;
        this.full = full;
        this.speechTag = speechTag;
    }

    public Locale locale() {
        return speechTag != null ? Locale.forLanguageTag(speechTag) : new Locale(code);
    }

    /** Speech recognition is separate from reply quality: offered wherever a recogniser tag exists. */
    public boolean voiceInputSupported() {
        return speechTag != null;
    }

    public static CompanionLanguage fromCode(String code) {
        for (CompanionLanguage l : values()) {
            if (l.code.equalsIgnoreCase(code)) return l;
        }
        return null;
    }
}
