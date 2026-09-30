package com.example.witspath.companion;

import java.util.ArrayList;
import java.util.List;

/**
 * The companion's replies can contain a little markdown (**bold**, bullet lists, headings).
 * Chat bubbles are plain text, so this turns that into readable text plus the bold ranges to style,
 * instead of showing stray asterisks. Anything it does not understand is shown as plain text.
 */
public final class MarkdownLite {

    public static final class Result {
        /** The text without markdown markers. */
        public final String text;
        /** {start, end} pairs (end exclusive) to show in bold. */
        public final List<int[]> bold;

        Result(String text, List<int[]> bold) {
            this.text = text;
            this.bold = bold;
        }
    }

    private MarkdownLite() {}

    public static Result parse(String source) {
        if (source == null) return new Result("", new ArrayList<int[]>());
        StringBuilder out = new StringBuilder();
        List<int[]> bold = new ArrayList<>();

        String[] lines = source.replace("\r\n", "\n").split("\n", -1);
        for (int n = 0; n < lines.length; n++) {
            String line = lines[n];
            boolean heading = false;

            int h = 0;
            while (h < line.length() && h < 6 && line.charAt(h) == '#') h++;
            if (h > 0 && h < line.length() && line.charAt(h) == ' ') {
                heading = true;
                line = line.substring(h + 1);
            }

            // "- item", "* item" and "+ item" become a bullet. "**bold**" is not a bullet.
            int k = 0;
            while (k < line.length() && line.charAt(k) == ' ') k++;
            if (k + 1 < line.length() && "-*+".indexOf(line.charAt(k)) >= 0 && line.charAt(k + 1) == ' ') {
                line = line.substring(0, k) + "• " + line.substring(k + 2);
            }

            int lineStart = out.length();
            inline(line, out, bold);
            if (heading && out.length() > lineStart) bold.add(new int[]{lineStart, out.length()});
            if (n < lines.length - 1) out.append('\n');
        }
        return new Result(out.toString(), bold);
    }

    /** The text with the markers removed, for speech and for sharing. */
    public static String plain(String source) {
        return parse(source).text;
    }

    private static void inline(String line, StringBuilder out, List<int[]> bold) {
        int i = 0;
        while (i < line.length()) {
            char c = line.charAt(i);
            boolean double_ = i + 1 < line.length() && line.charAt(i + 1) == c && (c == '*' || c == '_');
            if (double_) {
                String marker = "" + c + c;
                int close = line.indexOf(marker, i + 2);
                if (close > i + 2) {
                    int start = out.length();
                    out.append(stripSingles(line.substring(i + 2, close)));
                    if (out.length() > start) bold.add(new int[]{start, out.length()});
                    i = close + 2;
                } else {
                    i += 2; // a marker with no partner is dropped
                }
            } else if (c == '*' || c == '`') {
                i++; // single asterisks (italics) and code ticks are dropped
            } else {
                out.append(c);
                i++;
            }
        }
    }

    private static String stripSingles(String s) {
        return s.replace("*", "").replace("`", "");
    }
}
