# Synthetic-only Android tests

Run only on the explicit disposable emulator-5556. These tests replace the
Recovery app's synthetic test data; never run them on a personal phone.
The instrumentation targets local.snapexplorer.recovery, uses its separate
signing key, and does not target the original installed package.
SearchGroupsSmoke covers complete-query grouping, copies crossing page boundaries,
counts/arrow positions, distinct context, cancellation and reversible display.
MigrationSmoke exports and reimports synthetic records and checks content hashes.
Smoke covers normal navigation, pins/groups, paging, previews and search UI.

CompactSearchSmoke checks that compact chat and search hide extra copies without
repeat badges or expand controls, preserve sender identity and source rows,
restore standard view, and retain correct highlighting across page boundaries.
Recovery 0.4.6 verification: 18 compact/search, 69 general UI and 335 paged-search
checks passed on synthetic data. These are not real-export stress tests.

DemoScreens captures fictional sample chats on a disposable emulator for the
repository screenshots. It requires `-e demo synthetic-only` and refuses a
non-emulator environment. See `docs/BUILDING.md` at the repository root.
