package org.dslul.openboard.inputmethod.latin.expansion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Map;

import org.junit.Test;

public class ShortcutFormatTest {

    @Test
    public void parsesSpacesAndTabs() {
        final Map<String, String> map = ShortcutFormat.parse(
                "dmq Dominique\n"
                + "krgds\tKind regards,\\nDominique\n"
                + "   sig    Best,   D.   \n");
        assertEquals(3, map.size());
        assertEquals("Dominique", map.get("dmq"));
        assertEquals("Kind regards,\nDominique", map.get("krgds"));
        assertEquals("Best,   D.", map.get("sig"));
    }

    @Test
    public void ignoresCommentsBlankAndIncompleteLines() {
        final Map<String, String> map = ShortcutFormat.parse(
                "# comment\n\n   \nlonely\nlonely2   \r\nok fine\r\n");
        assertEquals(1, map.size());
        assertEquals("fine", map.get("ok"));
    }

    @Test
    public void lastDefinitionWins() {
        final Map<String, String> map = ShortcutFormat.parse("a one\nb two\na three\n");
        assertEquals("three", map.get("a"));
        assertEquals("[b, a]", map.keySet().toString());
    }

    @Test
    public void escapes() {
        assertEquals("a\nb\tc\\d\\x", ShortcutFormat.unescape("a\\nb\\tc\\\\d\\x"));
        assertEquals("a\\nb", ShortcutFormat.unescape("a\\\\nb"));
        assertEquals("trailing\\", ShortcutFormat.unescape("trailing\\"));
    }

    @Test
    public void formatRoundTrips() {
        final String text = "dmq Dominique\nkrgds Kind regards,\\nDominique\npath C:\\\\temp\\tx\n";
        final Map<String, String> map = ShortcutFormat.parse(text);
        assertEquals("C:\\temp\tx", map.get("path"));
        assertEquals(text, ShortcutFormat.format(map));
        assertTrue(ShortcutFormat.parse(ShortcutFormat.format(map)).equals(map));
    }

    @Test
    public void nullIsEmpty() {
        assertTrue(ShortcutFormat.parse(null).isEmpty());
    }
}
