/*
 * Copyright (C) 2026 Zpand contributors
 *
 * Licensed under the GNU General Public License, version 3 or later.
 */

package org.dslul.openboard.inputmethod.latin.expansion;

import android.text.TextUtils;
import android.view.inputmethod.EditorInfo;

import org.dslul.openboard.inputmethod.latin.InputAttributes;
import org.dslul.openboard.inputmethod.latin.utils.InputTypeUtils;

/** Decides in which text fields shortcuts may be expanded. */
public final class ShortcutExpansionPolicy {

    private ShortcutExpansionPolicy() {}

    /**
     * Expansion is disabled in password fields, and in this app's own screens (otherwise the
     * shortcut editor would expand the abbreviations you are trying to define).
     */
    public static boolean isAllowed(final InputAttributes inputAttributes, final String ownPackage) {
        if (inputAttributes == null) return true;
        if (inputAttributes.mIsPasswordField) return false;
        return !TextUtils.equals(inputAttributes.mTargetApplicationPackageName, ownPackage);
    }

    /** Same as above, straight from the editor (used for physical keys, see HardwareKeyExpander). */
    public static boolean isAllowed(final EditorInfo editorInfo, final String ownPackage) {
        if (editorInfo == null) return false;
        if (InputTypeUtils.isPasswordInputType(editorInfo.inputType)
                || InputTypeUtils.isVisiblePasswordInputType(editorInfo.inputType)) {
            return false;
        }
        return !TextUtils.equals(editorInfo.packageName, ownPackage);
    }
}
