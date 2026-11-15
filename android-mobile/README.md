# Snap Explorer Recovery for Android

Offline Snapchat HTML/JSON archive viewer. Android 8.0 / API 26 or newer.
The app uses package `local.snapexplorer.recovery`.

## Install

Download the signed APK from [Releases](https://github.com/Akil-0x1/snap-explorer-android/releases/latest).
Install over **Snap Explorer Recovery** to retain existing imports and settings.
No uninstall or repeat import is needed. It does not replace the separate
original `local.snapexplorer.mobile` app, whose signing identity differs.
The release checksum verifies the download; it is not Play Store certification.

## Import locally

Keep original exports backed up under your control. Choose **Import / merge**
and select related ZIP parts together, or select an extracted export folder.
Folders can contain ZIP parts, loose files or nested export folders. Keep the
app foregrounded during large imports. Interrupted source copies can be retried;
the existing archive stays intact until the merge commits.

For several ZIPs totalling about 6 GB, allow roughly 12 GB of additional free
space, preferably more. Expansion, duplicate content, retained sources and
filesystem behaviour can change actual requirements. A real private 6 GB
archive has not been used for development stress testing.

JSON references alone cannot restore missing images, stickers, Bitmoji or
Memories. Local assets must be present. **Shared media** in a chat shows only
files that can be linked from the export; ambiguous or absent links may leave
it empty. The general Media gallery can show other imported files.

## Chat and search

Messages retain raw sender names and timestamps. Use **Set my sender name** to
choose your sender explicitly. Conflicting export flags remain neutral until
that choice; text similarity never assigns a message to another person.

Search shows a paged result list. Tap a result to jump to it, or use the
previous/next arrows. Repeat hiding is on by default for search; standard chat
view also offers a **Hide repeats** search toggle.

**Chat menu → Compact view → Use compact view** hides matching repeated
messages in both the chat and search. Each remaining message is a normal bubble,
with no group badge, copy count or expand button. Repeat hiding also applies to search while compact mode is enabled. **Use standard view**
restores all original chat entries.

Matching uses sender, text, displayed minute, attachments and reply context.
Genuine repeated sends may also be hidden. No original records are deleted.

## Privacy and storage

The APK requests zero Android permissions, including no Internet permission.
There is no server upload, telemetry or login. Imported sources, media, metadata
and the database stay in private app storage. Android automatic cloud backup
and device transfer are excluded. Screenshots and recordings are allowed.

There is no extra app-level encryption for the database or exported backups.
Choose an on-device document destination for offline exports: a cloud document
provider may sync files using its own permissions. This app cannot download
missing media or check for updates online.

## Build and test

Follow the [repository build instructions](../README.md#build-and-test) from
the repository root. Development downloads tools and dependencies separately;
the built Android app remains offline. `build.py` creates an unsigned verification
artifact. `build_recovery.py` signs with the established private Recovery key;
that key is deliberately absent from GitHub and is never generated silently.

Tests use synthetic fixtures rather than private chat exports.
Device tests replace synthetic app data and must run only on a disposable
emulator. They do not guarantee codec support or behaviour on every phone.

See [feature coverage](FEATURE-PARITY.md) and the [licence](../LICENSE).
Public-format synthetic fixtures retain their source and MIT licence under
`tests/fixtures/public-snapchat/`. No private chat export is included.
