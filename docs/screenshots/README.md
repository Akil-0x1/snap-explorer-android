# Synthetic app screenshots

These PNGs show the actual released Android app on a disposable Android 11
ARM64 emulator. All message text and contacts come from the bundled synthetic
sample or the fictional records in `DemoScreens.java`. No private export or
physical phone was used. Images are direct captures, not interface mockups.

- `chats.png`: conversation list.
- `archive-search.png`: message search across conversations.
- `chat-search.png`: a selected result and in-chat navigation.

Capture fixture: `android-mobile/tests/device/DemoScreens.java`. Run only on a
disposable emulator with explicit `-e demo synthetic-only`; it replaces test data.
