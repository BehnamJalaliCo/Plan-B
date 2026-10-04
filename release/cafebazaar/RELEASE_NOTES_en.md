# Plan-B 1.0.1

A reliability update after a full code and screen review. Recommended for everyone.

## Fixed

- **Reminders**: one-time event reminders are no longer removed the moment they appear; habits
  repeating every 15+ days now get reminders; reminders survive restarts more reliably and stale
  alarms never fire for changed or restored items.
- **Language**: on Android 8–12 a chosen English language no longer switches back to Persian
  after restarting the app.
- **Recurring tasks**: marking a recurring task done from the editor keeps the series going;
  changing its date moves the series correctly; unchecking a completed occurrence no longer
  leaves a duplicate; subtasks of the next occurrence keep their relative dates and reminders.
- **Undo**: undoing one deleted task no longer affects others; deletes finish even if you leave
  the screen.
- **Projects** keep their tags when edited, and project notes never lose the last words typed.
- **Notes editor**: steadier typing (no lost characters or jumping cursor), Enter and multi-line
  paragraphs behave correctly, a new note is never duplicated, and a "join with the block above"
  action was added.
- **Search** finds Persian words written with or without the half-space (کتاب‌ها / کتابها) and
  with hamza variants.
- **Stability**: a damaged settings file no longer crashes the app; safer backup restore and
  export; focus minutes are recorded even when a session ends in the background.
- **Display**: in Persian, separators next to numbers no longer look like a zero; status-bar
  icons follow the app theme; larger touch targets; 12-hour time picker in English when the
  phone uses 12-hour time; Today greeting stays current.

## Privacy

No accounts, ads, analytics or tracking. Your data stays on your device.

## Files

| File | Use |
|---|---|
| `Plan-B-v1.0.1-release.apk` | Install directly on Android 8.0+ |
| `Plan-B-v1.0.1-release.aab` | Android App Bundle (store upload) |
| `Plan-B-v1.0.1-bazaar.bin` | Signed bundle digest for Cafe Bazaar (generated with the official bundle-signer) |
| `SHA256SUMS.txt` | Checksums — verify with `sha256sum -c SHA256SUMS.txt` |

Minimum Android version: 8.0 (API 26). Target: API 37.
