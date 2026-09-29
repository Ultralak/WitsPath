package com.example.witspath.util;

import com.example.witspath.routing.PhraseBook;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

/**
 * Loads phrase_templates/{lang}/phrases/{key} = { text, verifiedBy, verifiedAt } from Firestore.
 * PhraseBook itself decides whether a phrase may be shown (text and verifiedBy both non-empty).
 */
public final class PhraseBookLoader {
    private PhraseBookLoader() {}

    public interface Callback {
        void onLoaded(PhraseBook book);
    }

    private static final Map<String, PhraseBook> cache = new HashMap<>();

    /** Language tags like "zu" or "zu-ZA" are reduced to the base language. Failure gives English. */
    public static void load(String languageTag, Callback callback) {
        String lang = baseLanguage(languageTag);
        if (lang.isEmpty() || "en".equals(lang)) {
            callback.onLoaded(PhraseBook.english());
            return;
        }
        synchronized (cache) {
            PhraseBook cached = cache.get(lang);
            if (cached != null) {
                callback.onLoaded(cached);
                return;
            }
        }
        FirebaseFirestore.getInstance()
                .collection("phrase_templates").document(lang).collection("phrases").get()
                .addOnSuccessListener(snap -> {
                    Map<String, PhraseBook.Translation> t = new HashMap<>();
                    for (DocumentSnapshot d : snap.getDocuments()) {
                        String text = d.getString("text");
                        String by = d.getString("verifiedBy");
                        if (text != null) t.put(d.getId(), new PhraseBook.Translation(text, by));
                    }
                    PhraseBook book = PhraseBook.forLanguage(lang, t);
                    synchronized (cache) {
                        cache.put(lang, book);
                    }
                    callback.onLoaded(book);
                })
                .addOnFailureListener(e -> callback.onLoaded(PhraseBook.english()));
    }

    static String baseLanguage(String tag) {
        if (tag == null) return "";
        int dash = tag.indexOf('-');
        return (dash < 0 ? tag : tag.substring(0, dash)).toLowerCase();
    }
}
