# Offline Android feature coverage — Recovery 0.4.6

This is a substantial native Android implementation of the useful offline
capabilities in Snap Data Explorer and SnapXO. It is not either desktop runtime
embedded in an APK, and it does not claim identical behavior for every desktop
flag. Implementations are independent; GPL SnapXO source was not combined with
the upstream custom-licensed code. No new runtime libraries were needed.

| Capability | Android implementation / practical difference |
|---|---|
| HTML and JSON chat imports | Known Snapchat subpage HTML + JSON, with conservative one-for-one enrichment and JSON-only messages retained. |
| ZIP / folder / multiple parts | Android-selected ZIPs (up to 32 per selection) or selected export tree. No scanning other folders. |
| Large exports | Streaming media and document copies, 16 GiB per ZIP, 2 GiB per media file, 32 GiB expanded media per ZIP. Multiple ZIPs totaling 6 GB fit these bounds. Storage and device-memory limits still apply. |
| Archive merging | Merge selected exports into the current archive, source SHA256 dedup, message occurrence/ID-aware dedup, media content dedup. Originals unchanged. |
| Sender and friend names | Friends JSON enriches known conversation names; supplied sender flags retained. No names inferred from an account login. |
| Reply relationships | Explicit reply references preserved and navigable if their target exists. No fabricated reply chain when the export omits it. |
| Stickers and Bitmoji | Raw JSON retained in Data; local asset paths, nested fields and explicit identifiers indexed into sticker/avatar categories. Native GIF/WebP animation on Android 9+, bounded static fallback. Missing local assets cannot be reconstructed from online URLs offline. |
| Message browsing | Native lists, older/newer batches, search-result navigation, jump to date, selectable/copyable text. |
| Full-text search | Local SQLite FTS4, global/in-chat search, dates and sender tokens, visible highlights. Mobile platform uses FTS4 rather than desktop FTS5. In-chat results are paged with previous/next navigation; repeat hiding is default in search and enforced in compact mode. Global result lists remain bounded. |
| Gallery | One general gallery with type/year filters, sticker/avatar/overlay tags, 120-item pages and bounded thumbnails. Chat Shared media shows only locally linked files; unavailable or ambiguous links may leave it empty. No Source switch. |
| Images and media links | Local ID/path matching, SHA references, pinch/drag/double-tap zoom, native video/audio playback and audio seek. Media across selected parts is relinked after import. |
| Memories | JSON metadata, local media matching, coordinates/dates, and explicit missing-file states. |
| Pro / chill modes | Native archive workspace plus immersive media viewer and slideshow; not a pixel-identical React desktop layout. |
| Overview and analytics | Counts, top conversations, kinds, daily/hour/weekday activity. Supplied metadata datasets get conservative record/date/category/contact charts when those fields exist. |
| Friends, snaps, search, stories, account datasets | Every retained JSON is paged in Data without preview truncation and can be saved in full, including unknown datasets and scalar arrays; dataset charts use supplied fields. Identical dataset snapshots are deduplicated, changed snapshots remain separate to avoid summing overlapping history. No inferred engagement/time-spent metrics. |
| Snap Map / location visualization | Native offline coordinate plot, coarse outlines, pinch/pan, chronological animation and scrubber. Up to 25,000 supplied coordinates; no online street tiles or geocoding. |
| Timezones | Display and statistics timezone selection, retained original timestamp strings, timezone-based organized filenames. |
| Conversation exports | TXT, JSON, escaped standalone searchable HTML, and PDF with local attachment previews where supported. |
| PDF export | Chat PDFs, media-gallery PDFs, Memories PDFs, stats PDFs/charts/tables. Device fonts/codecs determine preview support. 2,000-page safety bound. No Chromium download. |
| Spreadsheets | Formula-safe CSV, Excel XLSX with editable overview bar chart, and ODS tables. All generated stats series included as data; not an embedded chart for every series. |
| Portable archive | ZIP with index, searchable chats, media gallery, Memories, statistics, offline coordinate page, original sources, raw metadata variants and SHA256 manifest. Reimportable canonical JSON preserves known media/Memory links. |
| Media organization | Save matching filtered media as year/month ZIP with date/content-based filenames and manifest, including unloaded gallery pages. |
| Duplicate removal | SHA256 media content dedup rather than MD5. Equal bytes are one local media file even if multiple names/sources exist. Memories retain distinct supplied records. |
| H.265 / HEVC | Native Android decoder/YUV/HEVC encoder path, max 1920x1080, with supported source audio. Requires compatible device codecs; original files remain unchanged. No bundled x265, QSV or NVENC fallback. |
| Overlays | Explicit selected local overlay image can be burned into a JPEG or HEVC video copy. Static alpha compositing; HDR requiring tone mapping is rejected. Automatic/batch matching is not implemented. |
| GPS / EXIF | Explicit JPEG copies preserve available source EXIF, apply orientation and add supplied GPS/date. Processing bounded to 4096 pixels per side; raw originals are never modified. |
| Voice messages | Native playback, lossless AAC-to-M4A remux, native decode/reencode to AAC. Genuine MP3 conversion is offered only if the phone has a compatible MP3 encoder; AAC is the usual alternative. No fake MP3 filename conversion. |
| Integrity | SQLite/search-link checks, retained source and media SHA256, raw-metadata checksums, retained import warnings. Not proof of completeness of Snapchat's export. |
| Rebuild | Rebuild from retained local source ZIPs. Any missing/changed source blocks rebuild instead of silently losing that source's data. |
| Interrupted import | Durable selected-source copy checkpoints and retry. Entire database merge is atomic; this is not a foreground-service/background-job importer. Keep the app foregrounded; process kill requires retry, not automatic continuation. |
| Diagnostics / doctor | Local free-space details, real device codec listing, integrity and import-validation reports. No desktop ffmpeg/Chromium dependency checks. |
| Theme / accessibility | System/light/dark theme, native controls, touch navigation, selectable message text, chart descriptions and screenshots allowed on all screens. Phone UI still needs device QA. |
| Auto-updater / media downloader | Excluded by your offline-only requirement. No Internet permission, remote media retrieval or online checks. |
| Docker / LAN hosting | Excluded: desktop/server deployment is not an offline phone capability. Export the portable archive instead. |
| Desktop installers / hardware flags | Not applicable to an Android APK. Recovery package and signing identity retained for Recovery updates; separate from the original app. |

## Boundaries that remain

Recovery 0.4.6 was exercised on a disposable Android 11 ARM64 emulator with
synthetic fixtures: 236 host, 18 compact/search, 69 general UI and 335
paged-search checks passed. Codec support, document providers and performance
still vary by phone. This is not a real private 6 GB stress test. No private
export was opened or uploaded during development verification.

Compact view hides matching repeats in chat and search without badges or expand
controls. Matching can also hide genuine repeated sends; standard view restores
original chat entries. Raw sender identities and flags are retained.

JSON/HTML parsing streams large inputs, with a 4 GiB per-file limit and bounded
individual records/sections. Large metadata previews and backup/restore also
stream. Screenshots are allowed by the app. A successful APK build and signature
do not establish full feature parity or correctness on every Android device.
