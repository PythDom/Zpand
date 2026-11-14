/*
 * Copyright (C) 2026 Zpand contributors
 *
 * Licensed under the GNU General Public License, version 3 or later.
 */

package org.dslul.openboard.inputmethod.latin.expansion;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.dslul.openboard.inputmethod.latin.R;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Settings screen for text shortcuts: a plain-text editor plus import/export to a file.
 * Changes are saved when leaving the screen (or with the Save button).
 */
public final class ShortcutsActivity extends Activity {
    private static final String TAG = ShortcutsActivity.class.getSimpleName();
    private static final int REQUEST_IMPORT = 1;
    private static final int REQUEST_EXPORT = 2;

    private ShortcutRepository mRepository;
    private EditText mEditor;
    private TextView mCount;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.shortcuts_activity);
        if (getActionBar() != null) {
            getActionBar().setDisplayHomeAsUpEnabled(true);
        }
        mRepository = ShortcutRepository.getInstance(this);

        final Switch enabled = findViewById(R.id.shortcuts_enabled);
        enabled.setChecked(mRepository.isEnabled());
        enabled.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(final CompoundButton button, final boolean isChecked) {
                mRepository.setEnabled(isChecked);
            }
        });

        mCount = findViewById(R.id.shortcuts_count);
        mEditor = findViewById(R.id.shortcuts_editor);
        if (savedInstanceState == null) {
            mEditor.setText(mRepository.getText());
        }
        mEditor.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(final Editable s) {
                updateCount();
            }
        });
        updateCount();

        findViewById(R.id.shortcuts_save).setOnClickListener(v -> {
            if (save()) {
                Toast.makeText(this, R.string.shortcuts_saved, Toast.LENGTH_SHORT).show();
            }
        });
        findViewById(R.id.shortcuts_import).setOnClickListener(v -> startImport());
        findViewById(R.id.shortcuts_export).setOnClickListener(v -> startExport());
    }

    @Override
    public boolean onNavigateUp() {
        finish();
        return true;
    }

    @Override
    protected void onPause() {
        super.onPause();
        save();
    }

    private void updateCount() {
        final int count = ShortcutFormat.parse(mEditor.getText().toString()).size();
        mCount.setText(getResources().getQuantityString(R.plurals.shortcuts_count, count, count));
    }

    private boolean save() {
        final String text = mEditor.getText().toString();
        if (text.equals(mRepository.getText())) return true;
        try {
            mRepository.save(text);
            return true;
        } catch (final IOException e) {
            Log.e(TAG, "Could not save shortcuts", e);
            Toast.makeText(this, R.string.shortcuts_save_failed, Toast.LENGTH_LONG).show();
            return false;
        }
    }

    private void startImport() {
        final Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("*/*")
                .putExtra(Intent.EXTRA_MIME_TYPES, new String[] {
                        "text/*", "application/octet-stream" });
        startActivityForResult(intent, REQUEST_IMPORT);
    }

    private void startExport() {
        save();
        final Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_TITLE, "zpand-shortcuts.txt");
        startActivityForResult(intent, REQUEST_EXPORT);
    }

    @Override
    protected void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        final Uri uri = data.getData();
        if (requestCode == REQUEST_IMPORT) {
            importFrom(uri);
        } else if (requestCode == REQUEST_EXPORT) {
            exportTo(uri);
        }
    }

    private void importFrom(final Uri uri) {
        final String imported;
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) throw new IOException("No input stream for " + uri);
            imported = ShortcutRepository.readStream(in);
        } catch (final IOException | SecurityException e) {
            Log.e(TAG, "Import failed", e);
            Toast.makeText(this, R.string.shortcuts_import_failed, Toast.LENGTH_LONG).show();
            return;
        }
        final int count = ShortcutFormat.parse(imported).size();
        new AlertDialog.Builder(this)
                .setTitle(R.string.shortcuts_import)
                .setMessage(getResources().getQuantityString(
                        R.plurals.shortcuts_import_found, count, count))
                .setPositiveButton(R.string.shortcuts_import_add, (d, w) -> {
                    final String current = mEditor.getText().toString();
                    final String separator =
                            current.isEmpty() || current.endsWith("\n") ? "" : "\n";
                    mEditor.setText(current + separator + imported);
                    save();
                })
                .setNegativeButton(R.string.shortcuts_import_replace, (d, w) -> {
                    mEditor.setText(imported);
                    save();
                })
                .setNeutralButton(android.R.string.cancel, null)
                .show();
    }

    private void exportTo(final Uri uri) {
        try (OutputStream out = getContentResolver().openOutputStream(uri)) {
            if (out == null) throw new IOException("No output stream for " + uri);
            out.write(ShortcutRepository.toBytes(mEditor.getText().toString()));
            Toast.makeText(this, R.string.shortcuts_exported, Toast.LENGTH_SHORT).show();
        } catch (final IOException | SecurityException e) {
            Log.e(TAG, "Export failed", e);
            Toast.makeText(this, R.string.shortcuts_export_failed, Toast.LENGTH_LONG).show();
        }
    }
}
