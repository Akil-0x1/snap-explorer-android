# Snap Explorer for Android — 0.2.0 offline upgrade

Historical0.2 setup notes follow; the0.2.2 changes above override older limits and screenshot defaults.

A native Android archive workspace for Snapchat HTML + JSON exports. Android 8.0
(API26) or newer. No Internet permission, account, telemetry or server.

## Import fixes and screenshots

Selected folders can contain ZIP parts, loose JSON/media files, or extracted
export folders; mixed folders are supported. Selecting a JSON directory directly
also works. Large JSON and HTML chat files are streamed instead of loaded whole.
Regression tests retain all 40,000 JSON messages and 35,000 HTML messages from
files over 32 MiB using a 96 MiB Java heap. Large raw JSON stays in private files;
metadata previews, insights, backup and restore stream it too.

Screenshots and screen recordings are allowed on all app screens, regardless of
an older saved protection setting. The app no longer sets a blocking window flag.
Android or a device-management policy can still impose restrictions separately.

## Install over your existing app

Use `artifacts/Snap-Explorer-Android-0.2.0.apk`. It has the same package ID and
local signing key as 0.1.0, with version code 2. **Install it as an update; do not
uninstall the old app**, because uninstalling deletes its private data.

The v0.1 database migration adds columns/tables and preserves existing chat rows
and the search index. Original v0.1 chats have no retained source ZIP or media;
import your original exports to enrich them and enable rebuilds. Export a local
backup from the app before trying new destructive actions.

The original signed 0.1.0 APK remains in artifacts for reference. Downgrading an
app/database is not an undo mechanism and Android can block it; keep a proper
export backup rather than relying on an old APK.

## First run

1. Open the updated app and confirm your old chats remain.
2. For a clean sample, the built-in sample has two chats/five messages, images,
   a video, a voice tone, an overlay, Memories and a missing-memory example,
   friends/location/story/snap JSON. It is entirely synthetic. Importing the
   sample merges it with existing data; don't clear real imports just to try it.
3. Use the navigation buttons for Chats, Overview, Media, Memories and Search.
   Options also opens locations, exports, verification, source archives and
   settings. View supplied JSON datasets in Overview > Export metadata.
4. In a chat, use Find, Jump to date, Earlier/Newer, Export, local attachments or
   explicit reply references. Long-press a message to copy or export its chat.
5. Gallery Tools contains local saves, JPEG/GPS copies, overlays, HEVC conversion,
   native audio conversion and device codec capabilities. Some codecs are
   unavailable on some phones; failure leaves imported originals unchanged.

## Import your several ZIPs totaling about 6 GB

- Keep the original ZIPs backed up under your control and put on-device copies
  in a local phone folder. No archive upload to this chat is needed.
- Allow roughly **12 GB additional free space**, preferably more, for a 6 GB
  export whose compressed media is already close to its original size. Raw source
  ZIP copies and extracted media are retained privately. Actual expansion,
  duplicate content, existing app data, portable backups and filesystem behavior
  change the requirement. Hard links avoid a second temporary source copy where
  supported; a filesystem fallback can need about another source-copy's space.
- Turn on airplane mode for a system-level offline boundary.
- Choose **Import / merge > Merge one or more ZIPs**, then select all related
  parts in Android's document picker (usually long-press to select multiple).
  Up to 32 ZIPs per selection. You can also choose one export folder explicitly.
- The importer reads both HTML and JSON, enriches matching messages, deduplicates
  identical media and links available media across the selected parts. Do not
  separately import HTML and JSON when both are in the same source ZIP.
- Keep the app foregrounded; large copies/imports can take a while. A pending
  import can be retried after interruption. Existing data stays intact until
  the complete merge commits. Do not clear app storage to interrupt an import.
- Review imported counts and notes; verify a few messages against original HTML.
  Choose Verify integrity when ready. A missing Memory is not downloadable here.

JSON may provide friends, senders, media IDs, account/search/story/snap/Bitmoji
metadata, and sometimes explicit reply references. It does not guarantee that
missing/deleted messages or images are present. Image URLs/IDs alone cannot be
turned into offline sticker/Bitmoji pictures; local assets must be included.

## Storage and privacy

Everything imported is under the Android app sandbox: private `chats.db`, retained
source ZIPs, hashed media blobs, raw metadata and interrupted-import copies.
Large raw JSON datasets are private files referenced by small database values,
which avoids Android cursor-window row-size failures. Raw metadata files are
checksum-checked when read. No message content is logged.

There is no extra application-level database/file encryption. Phone/account
security remains relevant. Automatic Android cloud backup and device transfer are
excluded. Screenshots and screen recordings are allowed; this app does not restrict them. Source document grants are released after private copies are complete
or an import is discarded; the app does not retain broad storage access.

Temporary source copies persist only for resumable imports, then are discarded.
Failed database transactions remove staged archive files. Unreferenced stage
directories from process kills are cleaned on the next launch. Derived media
copies expire from private cache if normal save/cancel cleanup was interrupted.

Exports save only to an explicit system document-picker destination. Choose an
on-device folder: some document providers can sync via their own permissions
although this app cannot access the network. Exported files are not encrypted.

## Current safety limits

- Each selected ZIP or zipped selected folder: 16 GiB.
- Streamed HTML/JSON file: 4 GiB; individual JSON record/token: 8 MiB; individual HTML message/section: 16 MiB. Smaller DOM inputs retain a 128 MiB aggregate guard.
- Expanded media: 32 GiB per ZIP, 2 GiB per media file, 100,000 ZIP entries.
- Bounded DOM enrichment: 200,000 messages per source format. Large files use record streaming rather than full in-memory lists.
- Search shows at most 200 matches; gallery pages contain 120 items.
- Location plot: up to 25,000 supplied points. PDF: up to 2,000 pages.

Limits bound work; they are not a guarantee on a low-memory or low-storage phone.
Media and large text files are streamed. Individual records and bounded small-file enrichment still use memory. Out-of-
memory import failures preserve the previous database. Multiple selected parts
are merged transactionally and can require a retry if the process is killed.

## Feature coverage

See **FEATURE-PARITY.md** for the complete comparison, adaptations and limits.
Highlights: local media/voice/video, Memories, merge/rebuild/integrity, dataset
charts, offline location timeline, themes/timezones, text/JSON/HTML/PDF exports,
CSV/XLSX/ODS, portable HTML archive ZIP and organized media output.

Desktop Docker hosting, automatic downloads/update checks and Intel/GPU-specific
flags are excluded. Codec operations use actual phone capabilities. HEVC/YUV and
MP3 encoding are conditional; AAC/M4A is the practical audio alternative on most
phones. No FFmpeg or native encoder binaries are bundled. Static overlays are
selected explicitly rather than automatically batch-matched.

## Verification completed

- Source compiled against Android35 with API26 minimum and Java8 target.
- Resource packaging, DEX compilation, ZIP alignment and APK Signature Schemes
  v2/v3 verification pass.
- Final APK manifest inspected: **zero requested Android permissions**.
- Same package ID / signing-certificate SHA256 as the original app.
- 115 host checks pass: legacy/full import, multi-part links, metadata variants,
  local stickers/Bitmoji, malformed archives, bounds, media/YUV/PCM rules,
  spreadsheet serialization, SQL migration/search/pagination and private large
  metadata storage.

These checks use synthetic data. **The 0.2 APK has not been tested on an Android
phone/emulator here, nor stress-tested with a real 6 GB archive.** Native playback,
encoders, PDFs, document providers and UI behavior need a phone trial. No private
archive was opened or uploaded. Start with the sample and a small export part.

Artifacts include the APK, SHA256SUMS.txt, BUILD-VERIFICATION.txt and focused-test
results. Checksum and local signature verify the produced file, not a verified
publisher identity, notarization or Play Store approval.

## Build/test locally

    python3 build.py
    python3 test.py

`build.py` makes no network calls. It reuses project-local Android35 platform /
Build Tools35.0.0 and jsoup1.24.1 under .tools, plus the existing Java installation.
The org.json JAR is JVM-test-only; Android provides its own JSON implementation.
No new runtime dependency, Android Studio, emulator, Gradle or NDK was installed
for this upgrade. Set SNAP_JAVA_HOME if your JDK is elsewhere.

Keep .keys private and preserve it for future updates; do not publish its files.
The custom upstream license and jsoup notice are included in APK assets. See
../LICENSE before redistribution. SnapXO was used as a feature reference, not
copied into an incompatible source combination. Not affiliated with Snap Inc.

## Public-format sample coverage

The test-only `tests/fixtures/public-snapchat/` sample adapts the synthetic
MIT-licensed records in [AxialForge/Unpacker](https://github.com/AxialForge/unpacker-v2/blob/75c1772af998f59570a1805fc7031ec2b6d10563/test/snapchat-account.test.js).
Its source and license are retained. It is not an official or complete personal
Snapchat export. No upstream test code runs and no remote media is fetched.
Public-format regression checks cover overlapping imports, sender direction,
pipe-separated media IDs, dated filenames containing tilde IDs, missing media,
ambiguous IDs and the exact contents of a chat's scoped gallery. Media bytes in
the Android integration test are locally generated coloured PNGs.
