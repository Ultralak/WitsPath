package com.example.witspath.companion;

import java.util.Locale;

/** The 11 companion languages and how well each is supported (B.2). */
public enum CompanionLanguage {
    EN("en", "English", true, "en-ZA"),
    AF("af", "Afrikaans", true, "af-ZA"),
    ZU("zu", "isiZulu", true, "zu-ZA"),
    ST("st", "Sesotho", true, "st-ZA"),
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
    /** BCP-47 tag for speech recognition; only full-tier languages have one. */
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

    /** Voice input is offered only for full languages. */
    public boolean voiceInputSupported() {
        return full && speechTag != null;
    }

    public static CompanionLanguage fromCode(String code) {
        for (CompanionLanguage l : values()) {
            if (l.code.equalsIgnoreCase(code)) return l;
        }
        return null;
    }
}
