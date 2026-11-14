/*
 * Copyright (C) 2026 Zpand contributors
 *
 * Licensed under the GNU General Public License, version 3 or later.
 */

package org.dslul.openboard.inputmethod.latin.expansion;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Pure-Java text expansion engine (no Android dependencies, so it can be unit tested on the JVM).
 *
 * Given the text just before the cursor, it finds an abbreviation that ends at the cursor and
 * returns its expansion. An abbreviation only matches as a whole "word": the character before it
 * must be the start of the text, whitespace, or any character that is not a letter or digit.
 * So with "dmq" defined, "dmq" and "(dmq" expand, but "admq" does not.
 *
 * Case handling: an exact (case-sensitive) entry always wins. Otherwise, for an entry stored in
 * lower case, "Dmq" expands to the capitalized expansion and "DMQ" to the upper-case expansion.
 */
public final class TextExpander {

    public static final TextExpander EMPTY = new TextExpander(Collections.<String, String>emptyMap());

    /** Result of a successful lookup. */
    public static final class Match {
        /** The text that was actually typed and will be replaced (e.g. "Dmq"). */
        public final String typed;
        /** The text to insert instead (e.g. "Dominique"). */
        public final String expansion;

        Match(final String typed, final String expansion) {
            this.typed = typed;
            this.expansion = expansion;
        }

        @Override
        public String toString() {
            return "Match{" + typed + " -> " + expansion + "}";
        }
    }

    private final Map<String, String> mShortcuts;
    private final int mMaxAbbreviationLength;

    public TextExpander(final Map<String, String> shortcuts) {
        mShortcuts = Collections.unmodifiableMap(new LinkedHashMap<>(shortcuts));
        int max = 0;
        for (final String abbreviation : mShortcuts.keySet()) {
            max = Math.max(max, abbreviation.length());
        }
        mMaxAbbreviationLength = max;
    }

    public static TextExpander fromText(final String text) {
        return new TextExpander(ShortcutFormat.parse(text));
    }

    public boolean isEmpty() {
        return mShortcuts.isEmpty();
    }

    public Map<String, String> getShortcuts() {
        return mShortcuts;
    }

    /**
     * How many characters before the cursor the caller should pass to {@link #findMatch}.
     * It is one more than the longest abbreviation, so that the word boundary can be checked.
     */
    public int getContextLength() {
        return mMaxAbbreviationLength + 1;
    }

    /**
     * @param textBeforeCursor up to {@link #getContextLength()} characters before the cursor.
     *        If fewer characters are passed, the text is assumed to start at the beginning of the
     *        field.
     * @return the longest matching abbreviation, or null.
     */
    public Match findMatch(final CharSequence textBeforeCursor) {
        if (textBeforeCursor == null || mShortcuts.isEmpty()) return null;
        final String text = textBeforeCursor.toString();
        final int textLength = text.length();
        final int longest = Math.min(mMaxAbbreviationLength, textLength);
        for (int length = longest; length >= 1; length--) {
            final int start = textLength - length;
            if (start > 0 && Character.isLetterOrDigit(text.charAt(start - 1))) continue;
            // Never start in the middle of a surrogate pair.
            if (Character.isLowSurrogate(text.charAt(start))) continue;
            final String candidate = text.substring(start);
            if (containsWhitespace(candidate)) continue;
            final String expansion = lookup(candidate);
            if (expansion != null) return new Match(candidate, expansion);
        }
        return null;
    }

    private String lookup(final String typed) {
        final String exact = mShortcuts.get(typed);
        if (exact != null) return exact;
        final String lower = typed.toLowerCase(Locale.ROOT);
        if (lower.equals(typed)) return null;
        final String expansion = mShortcuts.get(lower);
        if (expansion == null) return null;
        if (isAllUpperCase(typed)) {
            return expansion.toUpperCase(Locale.getDefault());
        }
        if (isCapitalized(typed)) {
            return capitalizeFirst(expansion);
        }
        return null;
    }

    private static boolean containsWhitespace(final String s) {
        for (int i = 0; i < s.length(); i++) {
            if (Character.isWhitespace(s.charAt(i))) return true;
        }
        return false;
    }

    /** True for "DMQ" (at least two letters, none lower case); false for "D" or "D1". */
    static boolean isAllUpperCase(final String s) {
        int letters = 0;
        for (int i = 0; i < s.length(); ) {
            final int cp = s.codePointAt(i);
            if (Character.isLetter(cp)) {
                if (!Character.isUpperCase(cp)) return false;
                letters++;
            }
            i += Character.charCount(cp);
        }
        return letters >= 2;
    }

    /** True for "Dmq": the first letter is upper case and every other letter is lower case. */
    static boolean isCapitalized(final String s) {
        boolean firstLetterSeen = false;
        for (int i = 0; i < s.length(); ) {
            final int cp = s.codePointAt(i);
            if (Character.isLetter(cp)) {
                if (!firstLetterSeen) {
                    if (!Character.isUpperCase(cp)) return false;
                    firstLetterSeen = true;
                } else if (Character.isUpperCase(cp)) {
                    return false;
                }
            }
            i += Character.charCount(cp);
        }
        return firstLetterSeen;
    }

    static String capitalizeFirst(final String s) {
        for (int i = 0; i < s.length(); ) {
            final int cp = s.codePointAt(i);
            if (Character.isLetter(cp)) {
                return s.substring(0, i) + new String(Character.toChars(Character.toTitleCase(cp)))
                        + s.substring(i + Character.charCount(cp));
            }
            i += Character.charCount(cp);
        }
        return s;
    }
}
