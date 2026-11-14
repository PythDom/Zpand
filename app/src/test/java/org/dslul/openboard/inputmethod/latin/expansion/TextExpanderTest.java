package org.dslul.openboard.inputmethod.latin.expansion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Test;

public class TextExpanderTest {

    private static final TextExpander EXPANDER = TextExpander.fromText(
            "dmq=Dominique\n"
            + "krgds=Kind regards,{enter}Dominique\n"
            + "km=kilometre\n"
            + "kmh=km/h\n"
            + "!addr=1 rue de la Paix\n"
            + "Ok=Okay exactly\n"
            + "tjs=toujours\n"
            + "Tjs=Toujours !\n"
            + "CSS=CSERIES\n"
            + "a-m=après-midi\n"
            + "màj=mise à jour\n");

    private static String expand(final String textBeforeCursor) {
        final TextExpander.Match match = EXPANDER.findMatch(textBeforeCursor);
        return match == null ? null : match.expansion;
    }

    @Test
    public void simpleExpansion() {
        assertEquals("Dominique", expand("dmq"));
        assertEquals("Dominique", expand("Hello dmq"));
        assertEquals("Kind regards,\nDominique", expand("Thanks.\nkrgds"));
    }

    @Test
    public void matchReportsTypedText() {
        final TextExpander.Match match = EXPANDER.findMatch("Hi Dmq");
        assertEquals("Dmq", match.typed);
        assertEquals("Dominique", match.expansion);
    }

    @Test
    public void onlyWholeWordsExpand() {
        assertNull(expand("admq"));
        assertNull(expand("dmqx"));
        assertNull(expand("2dmq"));
        assertEquals("Dominique", expand("(dmq"));
        assertEquals("Dominique", expand("l'dmq"));
    }

    @Test
    public void longestMatchWins() {
        assertEquals("km/h", expand("50 kmh"));
        assertEquals("kilometre", expand("50 km"));
    }

    @Test
    public void prefixCharacterAbbreviation() {
        assertEquals("1 rue de la Paix", expand("at !addr"));
        assertEquals("1 rue de la Paix", expand("!addr"));
        // "addr" alone is not defined.
        assertNull(expand("addr"));
    }

    @Test
    public void caseVariants() {
        assertEquals("Dominique", expand("Dmq"));
        assertEquals("Kind regards,\nDominique", expand("Krgds"));
        // No ALL-CAPS variant: acronyms must not expand by accident.
        assertNull(expand("DMQ"));
        // Mixed case is not a recognized variant.
        assertNull(expand("dMq"));
    }

    @Test
    public void explicitCasePairsAndUpperCaseTriggers() {
        assertEquals("toujours", expand("tjs"));
        assertEquals("Toujours !", expand("Tjs"));
        assertEquals("CSERIES", expand("CSS"));
        assertNull(expand("css"));
        assertNull(expand("Css"));
    }

    @Test
    public void triggersWithHyphenAndAccents() {
        assertEquals("après-midi", expand("cet a-m"));
        assertEquals("mise à jour", expand("la màj"));
        assertEquals("Mise à jour", expand("Màj"));
        assertNull(expand("xa-m"));
    }

    @Test
    public void capitalizeLowercaseExpansion() {
        assertEquals("Kilometre", expand("Km"));
    }

    @Test
    public void exactCaseEntryWins() {
        assertEquals("Okay exactly", expand("Ok"));
        assertNull(expand("ok"));
    }

    @Test
    public void truncatedContextStillChecksBoundary() {
        // The IME passes at most getContextLength() characters. "xkrgds" truncated to the
        // last 6 characters is still "xkrgds", so the 'x' blocks the match.
        final int n = EXPANDER.getContextLength();
        assertEquals(6, n);
        assertNull(expand("xkrgds"));
        // A longer text truncated so that only the abbreviation and a space remain.
        final String full = "something krgds";
        assertEquals("Kind regards,\nDominique", expand(full.substring(full.length() - n)));
    }

    @Test
    public void noMatchCases() {
        assertNull(expand(""));
        assertNull(expand(null));
        assertNull(expand("dmq "));
        assertNull(TextExpander.EMPTY.findMatch("dmq"));
    }

    @Test
    public void contextLengthIsLongestPlusOne() {
        final Map<String, String> map = new LinkedHashMap<>();
        map.put("a", "x");
        map.put("abcd", "y");
        assertEquals(5, new TextExpander(map).getContextLength());
        assertEquals(1, TextExpander.EMPTY.getContextLength());
    }

    @Test
    public void caseHelpers() {
        assertTrue(TextExpander.isCapitalized("Dmq"));
        assertTrue(TextExpander.isCapitalized("É"));
        assertFalse(TextExpander.isCapitalized("dmq"));
        assertEquals("Élodie", TextExpander.capitalizeFirst("élodie"));
        assertEquals("1 Rue", TextExpander.capitalizeFirst("1 rue"));
    }
}
