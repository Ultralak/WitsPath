package com.example.witspath;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.example.witspath.companion.MarkdownLite;

import org.junit.Test;

public class MarkdownLiteTest {

    private static String boldParts(MarkdownLite.Result r) {
        StringBuilder sb = new StringBuilder();
        for (int[] b : r.bold) sb.append('[').append(r.text, b[0], b[1]).append(']');
        return sb.toString();
    }

    @Test
    public void boldMarkersAreRemovedAndRecorded() {
        MarkdownLite.Result r = MarkdownLite.parse("The **Commerce Library** is on campus.");
        assertEquals("The Commerce Library is on campus.", r.text);
        assertEquals("[Commerce Library]", boldParts(r));
    }

    @Test
    public void severalBoldPartsInOneLine() {
        MarkdownLite.Result r = MarkdownLite.parse("**Distance:** 68.4 metres, **step-free**");
        assertEquals("Distance: 68.4 metres, step-free", r.text);
        assertEquals("[Distance:][step-free]", boldParts(r));
    }

    @Test
    public void boldOnTheWholeReplyLine() {
        MarkdownLite.Result r = MarkdownLite.parse("**where are you now, and which entrance?**");
        assertEquals("where are you now, and which entrance?", r.text);
        assertEquals(1, r.bold.size());
    }

    @Test
    public void bulletsBecomeDots() {
        MarkdownLite.Result r = MarkdownLite.parse("Options:\n- First\n* Second\n+ Third");
        assertEquals("Options:\n• First\n• Second\n• Third", r.text);
    }

    @Test
    public void aBoldLineIsNotABullet() {
        MarkdownLite.Result r = MarkdownLite.parse("**Note** this");
        assertEquals("Note this", r.text);
        assertEquals("[Note]", boldParts(r));
    }

    @Test
    public void numberedListsAreKept() {
        assertEquals("1. Where are you now?\n2. Where do you need to go?",
                MarkdownLite.plain("1. **Where are you now?**\n2. **Where do you need to go?**"));
    }

    @Test
    public void headingsBecomeBoldLines() {
        MarkdownLite.Result r = MarkdownLite.parse("## Your route\nGo straight.");
        assertEquals("Your route\nGo straight.", r.text);
        assertEquals("[Your route]", boldParts(r));
    }

    @Test
    public void strayMarkersAreDropped() {
        assertEquals("unfinished bold", MarkdownLite.plain("**unfinished bold"));
        assertEquals("italic and code", MarkdownLite.plain("*italic* and `code`"));
    }

    @Test
    public void plainTextAndEdgeCasesPassThrough() {
        assertEquals("Hello there.", MarkdownLite.plain("Hello there."));
        assertEquals("", MarkdownLite.plain(null));
        assertEquals("", MarkdownLite.plain(""));
        assertTrue(MarkdownLite.parse("Hello").bold.isEmpty());
    }

    @Test
    public void lineBreaksAreKept() {
        assertEquals("a\n\nb", MarkdownLite.plain("a\n\nb"));
    }
}
