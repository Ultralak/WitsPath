package com.example.witspath.util;

import android.app.Activity;
import android.content.Context;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

import com.example.witspath.R;

/**
 * Team Wavelets - WitsPath
 * One table for every language the UI supports: BCP-47 tag, display strings, and its
 * accent colour (see res/values/colors_languages.xml). Keep this table and
 * activity_language.xml in sync if you add a language.
 */
public final class Languages {

    public static final int SYSTEM = 0;
    public static final int ENGLISH = 1;
    public static final int ZULU = 2;
    public static final int SESOTHO = 3;
    public static final int SETSWANA = 4;
    public static final int XHOSA = 5;
    public static final int AFRIKAANS = 6;

    /** "" means "follow the phone's system locale". */
    public static final String[] TAGS = {"", "en", "zu", "st", "tn", "xh", "af"};

    public static final int[] COLORS = {
            R.color.colorLangSystem,
            R.color.colorLangEnglish,
            R.color.colorLangZulu,
            R.color.colorLangSesotho,
            R.color.colorLangSetswana,
            R.color.colorLangXhosa,
            R.color.colorLangAfrikaans
    };

    public static final int[] COLORS_DIM = {
            R.color.colorLangSystemDim,
            R.color.colorLangEnglishDim,
            R.color.colorLangZuluDim,
            R.color.colorLangSesothoDim,
            R.color.colorLangSetswanaDim,
            R.color.colorLangXhosaDim,
            R.color.colorLangAfrikaansDim
    };

    public static final int[] DISPLAY_NAMES = {
            R.string.language_system_default,
            R.string.language_english,
            R.string.language_zulu,
            R.string.language_sesotho,
            R.string.language_setswana,
            R.string.language_xhosa,
            R.string.language_afrikaans
    };

    private Languages() {
    }

    /** Index into TAGS/COLORS/DISPLAY_NAMES for a stored BCP-47 tag, defaulting to System. */
    public static int indexForTag(String tag) {
        if (tag == null) return SYSTEM;
        for (int i = 0; i < TAGS.length; i++) {
            if (TAGS[i].equals(tag)) return i;
        }
        return SYSTEM;
    }

    public static int colorForTag(Context context, String tag) {
        return context.getColor(COLORS[indexForTag(tag)]);
    }

    public static int dimColorForTag(Context context, String tag) {
        return context.getColor(COLORS_DIM[indexForTag(tag)]);
    }

    public static String displayNameForTag(Context context, String tag) {
        return context.getString(DISPLAY_NAMES[indexForTag(tag)]);
    }

    /**
     * Applies and persists a UI language, then recreates the activity so every string
     * on screen re-resolves immediately. Pass "" for "system default".
     */
    public static void apply(Activity activity, String tag) {
        new Prefs(activity).setString(Prefs.KEY_UI_LANGUAGE, tag);
        LocaleListCompat locales = tag.isEmpty()
                ? LocaleListCompat.getEmptyLocaleList()
                : LocaleListCompat.forLanguageTags(tag);
        AppCompatDelegate.setApplicationLocales(locales);
        // setApplicationLocales already recreates activities that opted into per-app
        // language config-change handling; recreate() is a safe no-op fallback otherwise.
        activity.recreate();
    }
}
