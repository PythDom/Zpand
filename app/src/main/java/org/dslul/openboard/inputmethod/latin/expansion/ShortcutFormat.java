/*
 * Copyright (C) 2026 Zpand contributors
 *
 * Licensed under the GNU General Public License, version 3 or later.
 */

package org.dslul.openboard.inputmethod.latin.expansion;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Plain-text format for the shortcut list, compatible with TyperTask files:
 *
 * <pre>
 * ; Lines starting with ; (or #) are comments; blank lines are ignored.
 * dmq=Dominique
 * cdlt=Cordialement,{enter}Dominique
 * </pre>
 *
 * The trigger is everything before the first "=" (it cannot contain spaces); the text is everything
 * after it, so the text itself may contain "=". In the text, {enter} is a line break and {tab} a
 * tab (case-insensitive). If a trigger appears twice, the last line wins.
 *
 * TyperTask entries that this keyboard cannot perform are skipped: hotkeys such as
 * "{Control}{Shift}{Alt}G=..." and commands such as "x=RUN: notepad.exe". Lines without "=" are
 * ignored too (e.g. the page numbers left behind when copying from a PDF).
 */
public final class ShortcutFormat {

    private static final String ENTER = "{enter}";
    private static final String TAB = "{tab}";
    private static final Charset UTF_8 = Charset.forName("UTF-8");

    private ShortcutFormat() {}

    public static Map<String, String> parse(final String text) {
        final Map<String, String> result = new LinkedHashMap<>();
        if (text == null) return result;
        for (final String rawLine : text.split("\r\n|\r|\n", -1)) {
            final String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith(";") || line.startsWith("#")) continue;
            final int equals = line.indexOf('=');
            if (equals <= 0) continue;
            final String trigger = line.substring(0, equals).trim();
            if (trigger.isEmpty() || trigger.startsWith("{") || containsWhitespace(trigger)) {
                continue;
            }
            final String rawValue = line.substring(equals + 1).trim();
            if (rawValue.regionMatches(true, 0, "RUN:", 0, 4)) continue;
            final String expansion = unescape(rawValue);
            if (expansion.isEmpty()) continue;
            result.remove(trigger); // keep the position of the last definition
            result.put(trigger, expansion);
        }
        return result;
    }

    public static String format(final Map<String, String> shortcuts) {
        final StringBuilder sb = new StringBuilder();
        for (final Map.Entry<String, String> entry : shortcuts.entrySet()) {
            sb.append(entry.getKey()).append('=').append(escape(entry.getValue())).append('\n');
        }
        return sb.toString();
    }

    static String unescape(final String s) {
        final StringBuilder sb = new StringBuilder(s.length());
        final String lower = s.toLowerCase(Locale.ROOT);
        int i = 0;
        while (i < s.length()) {
            if (lower.startsWith(ENTER, i)) {
                sb.append('\n');
                i += ENTER.length();
            } else if (lower.startsWith(TAB, i)) {
                sb.append('\t');
                i += TAB.length();
            } else {
                sb.append(s.charAt(i));
                i++;
            }
        }
        return sb.toString();
    }

    static String escape(final String s) {
        return s.replace("\r\n", "\n").replace("\n", ENTER).replace("\t", TAB);
    }

    private static boolean containsWhitespace(final String s) {
        for (int i = 0; i < s.length(); i++) {
            if (Character.isWhitespace(s.charAt(i))) return true;
        }
        return false;
    }

    /**
     * Decodes a text file whatever its origin: UTF-8 (with or without BOM), UTF-16 with BOM
     * (Windows "Unicode"), or else Windows-1252 ("ANSI", typical of older Windows tools such as
     * TyperTask).
     */
    public static String decode(final byte[] bytes) {
        if (bytes.length >= 2 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xFE) {
            return new String(bytes, 2, bytes.length - 2, Charset.forName("UTF-16LE"));
        }
        if (bytes.length >= 2 && (bytes[0] & 0xFF) == 0xFE && (bytes[1] & 0xFF) == 0xFF) {
            return new String(bytes, 2, bytes.length - 2, Charset.forName("UTF-16BE"));
        }
        int offset = 0;
        if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB
                && (bytes[2] & 0xFF) == 0xBF) {
            offset = 3;
        }
        try {
            return UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes, offset, bytes.length - offset))
                    .toString();
        } catch (final CharacterCodingException e) {
            return new String(bytes, Charset.forName("windows-1252"));
        }
    }
}
