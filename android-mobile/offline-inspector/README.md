# Offline Snap Export Inspector

This user-run Mac tool inventories the full selected export—not just duplicate
messages. It helps identify what can be brought into the Android app while keeping
personal exports out of the AI conversation.

## Run it yourself

1. Extract the complete inspector ZIP, keeping its `lib` folder beside the launcher.
2. Double-click **Inspect Export Offline.command** in Finder.
3. Choose **Choose export**, then select original ZIP parts or an export folder.
   Select files already downloaded to your Mac. Multiple ZIP parts are supported.
4. When finished, Finder reveals the local report. Open `report.html` to see readable
   tables, or `summary.json` for aggregate counts. Nothing is automatically shared.

The existing Java installation on this Mac is reused. No Android Studio, new
runtime, server, account or package installation is required. If the runtime or
macOS network blocker is unavailable, the launcher stops before scanning.

## What it checks

- Photos, videos, audio and voice files.
- Custom stickers, ordinary stickers, Bitmoji, avatars and overlays.
- GIF/WebP files that may contain animation, and vector/animation asset formats.
- Asset files with missing or unfamiliar extensions inside media/sticker folders.
- Documents and other common attachment formats.
- Chat JSON/HTML, Memories, friends, locations, stories, Snap history, account/device
  information, searches, purchases, and other JSON metadata.
- String/numeric media and sticker IDs, paths, nested reference objects, and URLs.
- Local file candidates across ZIP parts; ambiguous matches and remote-only links
  are reported separately. Download URLs are never fetched.

This is an inventory, not a claim that every record or animation format is already
supported by the APK. It does not change the APK, export new media copies, rewrite
messages, or assign files to contacts by guesses. Unknown JSON remains in your
original export; it is counted rather than silently discarded. Candidate matches
are based on names/IDs, not media checksums or verified conversation ownership.
JSON/HTML can contain overlapping events, so record counts are not unique-message
counts. Formats and directory-based classifications are provisional.

## Privacy boundaries

- Run this launcher yourself, outside the Codex tool session. Do not ask the AI to
  scan your original ZIP under a strict local-only requirement.
- The launcher starts the Java checker with macOS network access denied. Tests
  explicitly verify that the Java process cannot connect to the network.
- It does not execute export HTML/scripts, render media, or download remote assets.
- Reports contain counts/categories only: no chat text, names, original IDs, source
  filenames/paths, URLs, media contents, or sample values. Error details are not
  printed. Reports are still your data; sharing any report is optional.
- Reports are written outside the project workspace, under:
  `~/Library/Application Support/Snap Export Inspector/`
- Finder reveals the report without automatically opening a networked browser.
  HTML reports have no scripts or external resources. For whole-device offline use,
  disconnect your network and use export files already stored locally.
- This tool makes no uploads. Operating-system, cloud-provider and backup/sync/cache
  behavior is outside its control. It cannot delete server-side chat records or
  control OpenAI training settings.

## DELETEPERSONAL

Run the launcher again and choose **DELETEPERSONAL**. It deletes this tool's
identified generated reports and report pointers only. Original exports and
unrelated files are retained. This action cannot erase data previously shared
with a service. To stop an active scan, press Control-C in its Terminal first.

## Size and format limits

The checker indexes file names without decoding/copying media. JSON/HTML parsing
streams records, with a 256 MB Java heap. Nested ZIPs require sequential passes and
can take longer; choose the original ZIP parts directly when possible. Partial or
unsupported metadata is flagged in the report. This is not a media-integrity check.
The input ZIPs/folders are never extracted into report storage or modified.

## Review, rebuild and tests

`src/OfflineInspector.java` contains the readable checker source. The launcher
contains the network-denial command. `build_inspector.py` reuses the project-local
libraries to rebuild the JAR. Third-party license/notice files remain inside the
bundled JARs. `JsonRecords.java` is included as source for the streaming reader.

`test_inspector.py` uses only synthetic exports. It checks full inventory categories,
custom/numeric sticker references, unknown asset formats, ambiguous/missing links,
privacy redaction, malformed input, nested ZIP/folder handling, unchanged originals,
scoped report deletion, and enforced network denial. No personal export was used.
