# Zpand

An Android keyboard with built-in **text expansion**, for both the on-screen keyboard and
**physical keyboards**. It is a fork of [OpenBoard](https://github.com/openboard-team/openboard)
1.4.5 (itself based on the AOSP keyboard); see [README.openboard.md](README.openboard.md) for
the original project.

```
dmq    → Dominique
krgds  → Kind regards,
         Dominique
```

## How it works

- Type an abbreviation, then a **space, Enter or punctuation**: the abbreviation is replaced by its
  expansion and your delimiter is kept.
- It only matches whole words: `dmq` expands, `admq` does not.
- Case: `Dmq` → `Dominique`, `DMQ` → `DOMINIQUE`. An entry defined with exact case wins.
- **Backspace right after an expansion** puts the abbreviation back.
- Never expands in password fields.

### Physical keyboards

OpenBoard does not process physical keys itself: they go straight to the app, so keyboard
layouts, dead keys (`^` + `e` = `ê`) and Ctrl shortcuts work as usual. Zpand only steps in when a
delimiter key completes an abbreviation. With Enter, the expansion is inserted and then Enter is
passed to the app as normal (new line, send, next field…).

The keyboard must be the selected input method, even if the on-screen keyboard is hidden.

## Managing shortcuts

*Zpand Settings → Text shortcuts* opens a plain-text editor, one shortcut per line:

```
# comment
dmq Dominique
krgds Kind regards,\nDominique
```

The abbreviation is the first word; everything after the first space is the expansion. Use `\n`
for a line break, `\t` for a tab and `\\` for a backslash. Changes apply as soon as you leave the
screen or press *Save*.

**Import / Export** read and write the same format as a `.txt` file, so you can keep your list in a
file and edit it on a computer. Importing lets you add to or replace the current list.

## Installing

Every push builds an APK with GitHub Actions (*Actions → Build → zpand-apk* artifact). Unzip it
and install `app-release.apk` (allow installs from unknown sources). Then enable it in
*Settings → System → Languages & input → On-screen keyboard → Manage keyboards*, and select it.

Zpand has its own app id (`io.github.pythdom.zpand`), so it can be installed next to OpenBoard.

APKs are signed with `app/zpand-debug.keystore`, a key committed to this repository so that each
new build installs as an update. It is public: fine for personal use, not for a store release.

## Code

The text expansion lives in `app/src/main/java/org/dslul/openboard/inputmethod/latin/expansion/`:

| File | Role |
|---|---|
| `TextExpander.java` | Matching engine (pure Java, unit tested) |
| `ShortcutFormat.java` | Parses and writes the text format |
| `ShortcutRepository.java` | Stores the list and the on/off setting |
| `HardwareKeyExpander.java` | Physical keyboard handling |
| `ShortcutExpansionPolicy.java` | Where expansion is allowed |
| `ShortcutsActivity.java` | Settings screen |

On-screen typing is hooked into `InputLogic.handleSeparatorEvent()`, before auto-correction, and
reuses OpenBoard's "revert auto-correction" mechanism for Backspace-undo. Physical keys are hooked
in `LatinIME.onKeyDown()/onKeyUp()`. Search for `Zpand` to find every change to OpenBoard code.

Unit tests: `./gradlew testReleaseUnitTest`.

## License

GPL-3.0, like OpenBoard. See [LICENSE](LICENSE).
