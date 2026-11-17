/*
 * Copyright (C) 2026 Zpand contributors
 *
 * Licensed under the GNU General Public License, version 3 or later.
 */

package org.dslul.openboard.inputmethod.latin.expansion;

import android.content.Context;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;

import org.dslul.openboard.inputmethod.latin.settings.SettingsValues;

/**
 * Text expansion for physical keyboards.
 *
 * OpenBoard does not process hardware keys itself (they go straight to the app, which keeps
 * layouts, dead keys and Ctrl shortcuts working). This class only steps in when a delimiter key
 * (space, Enter or punctuation) is pressed right after an abbreviation: it then replaces the
 * abbreviation with its expansion and inserts the delimiter itself. Every other key is left alone.
 *
 * Pressing Backspace immediately after an expansion restores the abbreviation.
 */
public final class HardwareKeyExpander {

    private static final class Undo {
        final String typed;
        final String separator;
        final String inserted; // expansion + separator, as found before the cursor
        final int cursorAfter; // absolute cursor position right after the expansion

        Undo(final String typed, final String expansion, final String separator,
                final int cursorAfter) {
            this.typed = typed;
            this.separator = separator;
            this.inserted = expansion + separator;
            this.cursorAfter = cursorAfter;
        }
    }

    private final Context mContext;
    private int mConsumedKeyCode = KeyEvent.KEYCODE_UNKNOWN;
    private boolean mLastKeyWasDeadKey = false;
    private Undo mUndo = null;

    public HardwareKeyExpander(final Context context) {
        mContext = context;
    }

    /** Forget any pending state, e.g. when the input field changes. */
    public void reset() {
        mUndo = null;
        mLastKeyWasDeadKey = false;
        mConsumedKeyCode = KeyEvent.KEYCODE_UNKNOWN;
    }

    /**
     * @param editorInfo the current editor. It is passed explicitly because, with a physical
     *        keyboard and no on-screen keyboard, the settings' input attributes may be stale.
     * @param settingsValues current settings, used for the list of word separators (may be null).
     * @return true if the event was consumed and must not be passed on.
     */
    public boolean onKeyDown(final KeyEvent event, final InputConnection ic,
            final EditorInfo editorInfo, final SettingsValues settingsValues) {
        final int keyCode = event.getKeyCode();
        if (KeyEvent.isModifierKey(keyCode)) {
            return false; // Shift, Alt... don't change any state.
        }
        final int unicodeChar = event.getUnicodeChar();
        final boolean afterDeadKey = mLastKeyWasDeadKey;
        mLastKeyWasDeadKey = (unicodeChar & KeyCharacterMap.COMBINING_ACCENT) != 0;
        final Undo undo = mUndo;
        mUndo = null;

        if (ic == null || event.getRepeatCount() > 0
                || event.isCtrlPressed() || event.isMetaPressed()
                || !ShortcutExpansionPolicy.isAllowed(editorInfo, mContext.getPackageName())) {
            return false;
        }

        if (keyCode == KeyEvent.KEYCODE_DEL) {
            if (undo != null && tryUndo(ic, undo)) {
                mConsumedKeyCode = keyCode;
                return true;
            }
            return false;
        }

        // Space after a dead key produces the accent itself: don't interfere.
        if (afterDeadKey) return false;

        final boolean isEnter = keyCode == KeyEvent.KEYCODE_ENTER
                || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER;
        final String separator;
        if (isEnter) {
            separator = "\n";
        } else if (unicodeChar > 0 && unicodeChar != '\t'
                && (unicodeChar == ' '
                        || (settingsValues != null && settingsValues.isWordSeparator(unicodeChar)))) {
            separator = new String(Character.toChars(unicodeChar));
        } else {
            return false;
        }

        final TextExpander expander = ShortcutRepository.getInstance(mContext).getActiveExpander();
        if (expander.isEmpty()) return false;
        final CharSequence textBeforeCursor =
                ic.getTextBeforeCursor(expander.getContextLength(), 0);
        final TextExpander.Match match = expander.findMatch(textBeforeCursor);
        if (match == null) return false;
        if (!TextUtils.isEmpty(ic.getSelectedText(0))) return false;

        ic.beginBatchEdit();
        ic.deleteSurroundingText(match.typed.length(), 0);
        if (isEnter) {
            // Let the app handle Enter as it normally would (new line, send, next field...).
            ic.commitText(match.expansion, 1);
            sendKey(ic, event, KeyEvent.ACTION_DOWN);
            sendKey(ic, event, KeyEvent.ACTION_UP);
        } else {
            ic.commitText(match.expansion + separator, 1);
        }
        ic.endBatchEdit();
        if (!isEnter) {
            // Backspace-undo is only offered if we know exactly where the expansion ends.
            final int cursor = getCursorPosition(ic);
            if (cursor >= 0) {
                mUndo = new Undo(match.typed, match.expansion, separator, cursor);
            }
        }
        mConsumedKeyCode = keyCode;
        return true;
    }

    /** @return true if the event was consumed and must not be passed on. */
    public boolean onKeyUp(final KeyEvent event) {
        if (mConsumedKeyCode != KeyEvent.KEYCODE_UNKNOWN
                && event.getKeyCode() == mConsumedKeyCode) {
            mConsumedKeyCode = KeyEvent.KEYCODE_UNKNOWN;
            return true;
        }
        return false;
    }

    /**
     * Undo only if nothing happened since the expansion: Backspace is the very next key, the
     * cursor is exactly where the expansion left it, nothing is selected, and the expansion is
     * still right before the cursor. In every other case Backspace works normally.
     */
    private static boolean tryUndo(final InputConnection ic, final Undo undo) {
        if (getCursorPosition(ic) != undo.cursorAfter) return false;
        final CharSequence before = ic.getTextBeforeCursor(undo.inserted.length(), 0);
        if (before == null || !undo.inserted.contentEquals(before)) return false;
        if (!TextUtils.isEmpty(ic.getSelectedText(0))) return false;
        // Keep the separator: "Dominique " becomes "dmq ".
        ic.beginBatchEdit();
        ic.deleteSurroundingText(undo.inserted.length(), 0);
        ic.commitText(undo.typed + undo.separator, 1);
        ic.endBatchEdit();
        return true;
    }

    /** @return the absolute cursor position, or -1 if unknown or if there is a selection. */
    private static int getCursorPosition(final InputConnection ic) {
        final ExtractedText extracted = ic.getExtractedText(new ExtractedTextRequest(), 0);
        if (extracted == null || extracted.selectionStart < 0
                || extracted.selectionStart != extracted.selectionEnd) {
            return -1;
        }
        return extracted.startOffset + extracted.selectionStart;
    }

    private static void sendKey(final InputConnection ic, final KeyEvent original, final int action) {
        final long now = SystemClock.uptimeMillis();
        ic.sendKeyEvent(new KeyEvent(now, now, action, original.getKeyCode(), 0 /* repeat */,
                original.getMetaState(), original.getDeviceId(), original.getScanCode(),
                original.getFlags(), original.getSource()));
    }
}
