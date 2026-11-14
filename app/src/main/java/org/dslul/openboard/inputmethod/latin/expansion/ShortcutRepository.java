/*
 * Copyright (C) 2026 Zpand contributors
 *
 * Licensed under the GNU General Public License, version 3 or later.
 */

package org.dslul.openboard.inputmethod.latin.expansion;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.dslul.openboard.inputmethod.latin.utils.DeviceProtectedUtils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;

/**
 * Stores the shortcut list as a plain-text file in the app's private storage and keeps the parsed
 * {@link TextExpander} in memory. The keyboard and the settings screen run in the same process, so
 * saving from the settings screen takes effect immediately.
 */
public final class ShortcutRepository {
    private static final String TAG = ShortcutRepository.class.getSimpleName();
    private static final String FILE_NAME = "shortcuts.txt";
    private static final Charset UTF_8 = Charset.forName("UTF-8");

    public static final String PREF_TEXT_EXPANSION_ENABLED = "pref_text_expansion_enabled";

    static final String DEFAULT_CONTENT =
            "; Zpand text shortcuts (same format as TyperTask)\n"
            + ";    Trigger=Type This Text   (one per line)\n"
            + "; Use {enter} for a line break. Lines starting with ; are ignored.\n"
            + "; Type the trigger then a space, Enter or punctuation to expand it.\n"
            + "; Press Backspace right after an expansion to undo it.\n"
            + "dmq=Dominique\n"
            + "krgds=Kind regards,{enter}Dominique\n";

    /** The default list of version 0.1, in the old "abbreviation expansion" format. */
    private static final String OLD_DEFAULT_CONTENT =
            "# Zpand text shortcuts\n"
            + "# One per line: abbreviation, a space, then the expansion.\n"
            + "# Use \\n for a line break. Lines starting with # are ignored.\n"
            + "# Type the abbreviation then a space, Enter or punctuation to expand it.\n"
            + "# Press Backspace right after an expansion to undo it.\n"
            + "dmq Dominique\n"
            + "krgds Kind regards,\\nDominique\n";

    private static volatile ShortcutRepository sInstance;

    private final File mFile;
    private final SharedPreferences mPrefs;
    private volatile TextExpander mExpander = TextExpander.EMPTY;
    private volatile String mText = "";

    public static ShortcutRepository getInstance(final Context context) {
        ShortcutRepository instance = sInstance;
        if (instance == null) {
            synchronized (ShortcutRepository.class) {
                instance = sInstance;
                if (instance == null) {
                    instance = new ShortcutRepository(context.getApplicationContext());
                    sInstance = instance;
                }
            }
        }
        return instance;
    }

    private ShortcutRepository(final Context context) {
        mFile = new File(context.getFilesDir(), FILE_NAME);
        mPrefs = DeviceProtectedUtils.getSharedPreferences(context);
        load();
    }

    private void load() {
        String text;
        if (mFile.exists()) {
            try {
                text = readFile(mFile);
            } catch (final IOException e) {
                Log.e(TAG, "Could not read shortcuts", e);
                text = "";
            }
            if (OLD_DEFAULT_CONTENT.equals(text)) {
                // Untouched list from version 0.1: switch to the new format.
                text = DEFAULT_CONTENT;
                try {
                    writeFile(mFile, text);
                } catch (final IOException e) {
                    Log.e(TAG, "Could not update default shortcuts", e);
                }
            }
        } else {
            text = DEFAULT_CONTENT;
            try {
                writeFile(mFile, text);
            } catch (final IOException e) {
                Log.e(TAG, "Could not write default shortcuts", e);
            }
        }
        apply(text);
    }

    private void apply(final String text) {
        mText = text;
        mExpander = TextExpander.fromText(text);
    }

    public boolean isEnabled() {
        return mPrefs.getBoolean(PREF_TEXT_EXPANSION_ENABLED, true);
    }

    public void setEnabled(final boolean enabled) {
        mPrefs.edit().putBoolean(PREF_TEXT_EXPANSION_ENABLED, enabled).apply();
    }

    /** The expander to use, or {@link TextExpander#EMPTY} when expansion is turned off. */
    public TextExpander getActiveExpander() {
        return isEnabled() ? mExpander : TextExpander.EMPTY;
    }

    /** The raw text of the shortcut list, as the user wrote it (including comments). */
    public String getText() {
        return mText;
    }

    /** Saves and immediately activates a new shortcut list. */
    public void save(final String text) throws IOException {
        writeFile(mFile, text);
        apply(text);
    }

    public static String readStream(final InputStream in) throws IOException {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final byte[] buffer = new byte[8192];
        int n;
        while ((n = in.read(buffer)) != -1) {
            out.write(buffer, 0, n);
        }
        return ShortcutFormat.decode(out.toByteArray());
    }

    private static String readFile(final File file) throws IOException {
        try (InputStream in = new FileInputStream(file)) {
            return readStream(in);
        }
    }

    private static void writeFile(final File file, final String text) throws IOException {
        final File tmp = new File(file.getPath() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(text.getBytes(UTF_8));
            out.getFD().sync();
        }
        if (!tmp.renameTo(file)) {
            throw new IOException("Could not replace " + file);
        }
    }

    public static byte[] toBytes(final String text) {
        return text.getBytes(UTF_8);
    }
}
