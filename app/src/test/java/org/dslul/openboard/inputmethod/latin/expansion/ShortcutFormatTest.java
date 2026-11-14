package org.dslul.openboard.inputmethod.latin.expansion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.Charset;
import java.util.Map;

import org.junit.Test;

public class ShortcutFormatTest {

    @Test
    public void parsesTyperTaskFile() {
        final Map<String, String> map = ShortcutFormat.parse(
                "; TyperTask will automate your keyboard when a \"signal\" is typed.\n"
                + ";    Trigger=Type This Text  (one per line)\n"
                + "; \n"
                + "\n"
                + "; Formules de politesse / Raccourcis\n"
                + "bjr=bonjour\n"
                + "a-m=après-midi\n"
                + "1 / 10\n"
                + "ajd=aujourd’hui\n"
                + "cdlt=Cordialement,{enter}Dominique\n"
                + "lkp=?actorCompanyId=123\n"
                + "màj=mise à jour\n"
                + "{Control}{Shift}{Alt}G=RUN: http://www.google.com\n"
                + "nb=RUN: notepad.exe{delay=1500}{home}\n"
                + "tjs=toujours\n"
                + "Tjs=Toujours\n");
        assertEquals("bonjour", map.get("bjr"));
        assertEquals("après-midi", map.get("a-m"));
        assertEquals("aujourd’hui", map.get("ajd"));
        assertEquals("Cordialement,\nDominique", map.get("cdlt"));
        assertEquals("?actorCompanyId=123", map.get("lkp"));
        assertEquals("mise à jour", map.get("màj"));
        assertEquals("toujours", map.get("tjs"));
        assertEquals("Toujours", map.get("Tjs"));
        assertFalse(map.containsKey("nb"));
        assertEquals(8, map.size());
    }

    @Test
    public void ignoresCommentsBlankAndInvalidLines() {
        final Map<String, String> map = ShortcutFormat.parse(
                "# comment\n; comment\n\n   \nno equals sign\n=no trigger\nempty=\n"
                + "two words=x\n  ok = fine  \r\n");
        assertEquals(1, map.size());
        assertEquals("fine", map.get("ok"));
    }

    @Test
    public void lastDefinitionWins() {
        final Map<String, String> map = ShortcutFormat.parse("a=one\nb=two\na=three\n");
        assertEquals("three", map.get("a"));
        assertEquals("[b, a]", map.keySet().toString());
    }

    @Test
    public void tokens() {
        assertEquals("a\nb\tc", ShortcutFormat.unescape("a{enter}b{tab}c"));
        assertEquals("a\nb", ShortcutFormat.unescape("a{ENTER}b"));
        assertEquals("{home}x", ShortcutFormat.unescape("{home}x"));
        assertEquals("a{enter}b{tab}c", ShortcutFormat.escape("a\nb\tc"));
    }

    @Test
    public void formatRoundTrips() {
        final String text = "dmq=Dominique\ncdlt=Cordialement,{enter}Dominique\nlkp=?id=1\n";
        final Map<String, String> map = ShortcutFormat.parse(text);
        assertEquals(text, ShortcutFormat.format(map));
    }

    @Test
    public void nullIsEmpty() {
        assertTrue(ShortcutFormat.parse(null).isEmpty());
    }

    @Test
    public void decodesCommonEncodings() {
        final String text = "ajd=aujourd’hui\nmàj=mise à jour\n";
        assertEquals(text, ShortcutFormat.decode(text.getBytes(Charset.forName("UTF-8"))));
        assertEquals(text, ShortcutFormat.decode(text.getBytes(Charset.forName("windows-1252"))));
        final byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        assertEquals(text, ShortcutFormat.decode(concat(bom, text.getBytes(Charset.forName("UTF-8")))));
        final byte[] le = {(byte) 0xFF, (byte) 0xFE};
        assertEquals(text, ShortcutFormat.decode(concat(le, text.getBytes(Charset.forName("UTF-16LE")))));
        final byte[] be = {(byte) 0xFE, (byte) 0xFF};
        assertEquals(text, ShortcutFormat.decode(concat(be, text.getBytes(Charset.forName("UTF-16BE")))));
    }

    private static byte[] concat(final byte[] a, final byte[] b) {
        final byte[] r = new byte[a.length + b.length];
        System.arraycopy(a, 0, r, 0, a.length);
        System.arraycopy(b, 0, r, a.length, b.length);
        return r;
    }
}
