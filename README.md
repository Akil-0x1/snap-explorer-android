# Snap Explorer for Android

An offline Android app for exploring your Snapchat HTML and JSON exports.
Browse chats, view local media, search your archive, and keep your data on your phone.

## Features

- **Chat reader:** conversation bubbles, sender names, timestamps, reply references
  when supplied, selectable text, starred messages, and jump to date.
- **Search:** message result lists, highlighted matches, and previous/next arrows.
- **Compact view:** hide matching repeated messages in chats and search while
  preserving original records.
- **Chat organisation:** pin conversations and arrange them into groups.
- **Media viewer:** local photos, videos, audio, stickers, Bitmoji assets, and
  Memories when included in the export. Browse by media type and year.
- **Shared media:** view files linked to a conversation when the export provides
  usable references.
- **Archive imports:** merge multiple ZIP parts or extracted folders containing
  HTML, JSON, and media. Large files are streamed during import.
- **Metadata and insights:** browse supplied account and activity datasets,
  conversation statistics, and offline location information.
- **Local exports and backups:** save conversations, media, reports, and portable
  archive backups that can be restored in the app.
- **Display options:** light/dark themes and timezone selection. Screenshots and
  screen recordings are allowed.

## Install

Requires **Android 8.0 or newer**.

Download the signed APK from [Releases](https://github.com/Akil-0x1/snap-explorer-android/releases/latest).
This repository is private; you must sign in with an account that has access.
A `SHA256SUMS.txt` file is included for download verification.

The app installs as **Snap Explorer Recovery**, package `local.snapexplorer.recovery`.
To keep existing imports and settings, install over the same app signed with the
same key. Do not uninstall it to resolve a signature mismatch. The original
`local.snapexplorer.mobile` app is a separate installation.

## Import your archive

1. Keep a backup of your original Snapchat export.
2. Copy the ZIP parts or extracted export folder to local phone storage.
3. Choose **Import / merge** and select related parts together, or choose the
   extracted folder.
4. Keep the app foregrounded until import finishes.

Allow extra free space for retained sources, extracted media, and backups.
Interrupted source copies can be retried; the existing archive is retained until
an import commits. See [Android usage notes](android-mobile/README.md) for details.

## Privacy

The app requests **no Android permissions**, including no Internet permission.
There is no upload service, telemetry, or account login. Imported chats, metadata,
and media remain in private app storage. Automatic Android cloud backup and
device transfer are excluded.

App storage and exported backups have no extra application-level encryption.
Choose an on-device destination for exports: a cloud document provider can sync
files using its own permissions.

The repository contains source and synthetic test fixtures. Personal exports,
signing keys, passwords, local backups, and development caches are excluded.

## Limitations

- Only information included in your export can be displayed. Missing or deleted
  messages, media, stickers, and reply relationships cannot be reconstructed.
- Online media links alone are insufficient; the app does not download files.
  Shared media can be empty when conversation links are missing or ambiguous.
- Compact matching uses sender, text, displayed minute, attachments, and reply
  context. It can hide genuine repeated sends too. **Use standard view** restores
  original chat entries; no records are deleted.
- Large imports depend on available storage and memory. Media playback and
  conversion depend on the phone's supported codecs.

## Build and test

The build scripts target an Apple Silicon Mac with Python 3, a JDK supporting
`javac --release 8`, Android 35 platform/build tools, and pinned Java libraries.
Set `SNAP_JAVA_HOME` if your JDK is not at the default Homebrew location.

From the repository root:

```sh
python3 restore_toolchain.py
python3 restore_fixtures.py
python3 android-mobile/test.py
python3 android-mobile/build.py
```

Toolchain setup downloads development dependencies; the Android app remains
offline. `build.py` produces an **unsigned verification APK**.

`python3 android-mobile/build_recovery.py` produces the signed Recovery APK only
when the established private signing files are available under
`android-mobile/.keys-recovery/`. Those files are not in this repository, and the
script refuses to generate a replacement identity. A build signed with another
key cannot replace an existing installation signed with the established key.

Device tests use synthetic data and replace the test app's data. Run them only
on a disposable emulator, never on a phone containing personal imports.

## Project structure

- `android-mobile/app/`: Android source, resources, and synthetic sample.
- `android-mobile/tests/`: host and device tests, with synthetic fixtures.
- `android-mobile/offline-inspector/`: optional local export inspection utility.
- `restore_toolchain.py`, `restore_fixtures.py`: development setup helpers.

See [feature coverage](android-mobile/FEATURE-PARITY.md) for supported formats
and platform-specific limits.

## Licensing and attribution

An unofficial Android adaptation associated with work on
[Snap Data Explorer](https://github.com/KodyDennon/SnapDataExplorer).
Not affiliated with Snap Inc.

Preserve the upstream source-available [licence](LICENSE) and
[third-party notices](THIRD-PARTY-NOTICES.md). The licence includes restricted-use
provisions and is not an unrestricted open-source licence.
