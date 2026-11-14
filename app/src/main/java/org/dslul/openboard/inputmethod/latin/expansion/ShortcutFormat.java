/*
 * Copyright (C) 2026 Zpand contributors
 *
 * Licensed under the GNU General Public License, version 3 or later.
 */

package org.dslul.openboard.inputmethod.latin.expansion;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Plain-text format for the shortcut list, designed to be easy to edit on a phone:
 *
 * <pre>
 * # Lines starting with # are comments; blank lines are ignored.
 * dmq Dominique
 * krgds Kind regards,\nDominique
 * </pre>
 *
 * The abbreviation is the first word of the line (it cannot contain spaces). Everything after the
 * first run of spaces or tabs is the expansion. In the expansion, {@code \n} means a line break,
 * {@code \t} a tab and {@code \\} a backslash. If an abbreviation appears twice, the last line wins.
 */
public final class ShortcutFormat {

    private ShortcutFormat() {}

    public static Map<String, String> parse(final String text) {
        final Map<String, String> result = new LinkedHashMap<>();
        if (text == null) return result;
        for (final String rawLine : text.split("\r\n|\r|\n", -1)) {
            final String line = stripLeading(rawLine);
            if (line.isEmpty() || line.startsWith("#")) continue;
            int split = 0;
            while (split < line.length() && !isBlank(line.charAt(split))) split++;
            final String abbreviation = line.substring(0, split);
            int valueStart = split;
            while (valueStart < line.length() && isBlank(line.charAt(valueStart))) valueStart++;
            final String expansion = unescape(stripTrailing(line.substring(valueStart)));
            if (expansion.isEmpty()) continue;
            result.remove(abbreviation); // keep the position of the last definition
            result.put(abbreviation, expansion);
        }
        return result;
    }

    public static String format(final Map<String, String> shortcuts) {
        final StringBuilder sb = new StringBuilder();
        for (final Map.Entry<String, String> entry : shortcuts.entrySet()) {
            sb.append(entry.getKey()).append(' ').append(escape(entry.getValue())).append('\n');
        }
        return sb.toString();
    }

    static String unescape(final String s) {
        final StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            final char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                final char next = s.charAt(i + 1);
                if (next == 'n') { sb.append('\n'); i++; continue; }
                if (next == 't') { sb.append('\t'); i++; continue; }
                if (next == '\\') { sb.append('\\'); i++; continue; }
            }
            sb.append(c);
        }
        return sb.toString();
    }

    static String escape(final String s) {
        final StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            final char c = s.charAt(i);
            switch (c) {
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\t': sb.append("\\t"); break;
                default: sb.append(c);
            }
        }
        return sb.toString();
    }

    private static boolean isBlank(final char c) {
        return c == ' ' || c == '\t' || c == ' ';
    }

    private static String stripLeading(final String s) {
        int i = 0;
        while (i < s.length() && isBlank(s.charAt(i))) i++;
        return s.substring(i);
    }

    private static String stripTrailing(final String s) {
        int i = s.length();
        while (i > 0 && isBlank(s.charAt(i - 1))) i--;
        return s.substring(0, i);
    }
}
