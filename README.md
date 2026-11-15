# Snap Explorer for Android

An offline Android viewer for locally imported Snapchat HTML and JSON exports.
Current Recovery build: **0.4.6-recovery** (version code 16).
Android 8.0 / API 26 or newer. Package: `local.snapexplorer.recovery`.

## Features

- Import and merge multiple ZIP parts or extracted folders.
- Browse conversations, local photos, videos, audio, stickers and export metadata.
- Search with result lists and previous/next match navigation.
- Optional compact view hides matching repeated messages in chats and search,
  without group badges or expand controls. Standard view restores original entries.
- Pin and group conversations, export and restore local portable backups.
- Preserve raw sender names; select your sender explicitly to control alignment.

Compact matching uses sender, text, displayed minute, attachments and reply
context. Genuine repeated sends may also be hidden. Original records are retained.
Media is available only when the export includes usable local files or references.

## Privacy

The app requests no Android permissions, including no Internet permission.
There is no upload service, telemetry, or account login. Imported data stays in
private app storage. Screenshots are allowed. App storage and exported backups
have no extra application-level encryption. A document provider selected by the
user can separately sync files; choose local storage for offline use.

This repository includes only synthetic test fixtures and attributed public
synthetic examples. Personal exports, signing keys, APKs, source-backup archives,
local development logs and downloaded build tools are excluded.

## Build and test

The build scripts currently target an Apple Silicon Mac and use Python 3,
a JDK supporting `javac --release 8`, Android 35 platform/build tools, and pinned
Java libraries. Set `SNAP_JAVA_HOME` if the JDK is not at the default Homebrew path.

```sh
python3 restore_toolchain.py
python3 restore_fixtures.py
python3 android-mobile/test.py
python3 android-mobile/build.py
```

Toolchain setup downloads SDK tools and Java dependencies. The Android app
itself remains offline. `build.py` produces an **unsigned verification APK**,
which is not a distributable update.

For a signed Recovery update, `python3 android-mobile/build_recovery.py` requires
the established private key and password under `android-mobile/.keys-recovery/`.
Those secrets are intentionally absent. The script refuses to generate a new
identity. Contributors building an independent app need their own signing
identity; it cannot update an existing Recovery installation signed by another key.
Never uninstall an existing app to resolve a signature mismatch without first
making and verifying an appropriate local backup.

Device instrumentation tests are in `android-mobile/tests/device/` and use
synthetic fixtures. Run only on a disposable emulator: several tests replace
its test app data. Do not run them against a phone containing private imports.

Current verification: 236 host checks, 18 compact/search Android checks,
69 general UI checks and 335 paged-search checks passed on synthetic fixtures.
No CI workflow is configured in this initial repository.

## Project layout

- `android-mobile/app/`: Android source, resources and synthetic sample.
- `android-mobile/tests/`: host and device tests, synthetic fixtures.
- `android-mobile/offline-inspector/`: optional local export inspection utility.
- `restore_toolchain.py`, `restore_fixtures.py`: development setup helpers.

## Licensing and attribution

This is an unofficial Android adaptation associated with work on
[Snap Data Explorer](https://github.com/KodyDennon/SnapDataExplorer), not an
application from Snap Inc. Preserve the upstream source-available licence in
[LICENSE](LICENSE) and the notices in [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md).
The upstream licence has restricted-use provisions; this is not presented as
unrestricted open-source software.

Older notes in `android-mobile/README.md` and `FEATURE-PARITY.md` are historical;
this README describes the current package and Recovery build.
