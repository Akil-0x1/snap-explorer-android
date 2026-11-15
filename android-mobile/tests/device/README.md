# Synthetic-only Android tests

Run only on the explicit disposable emulator-5556. These tests replace the
Recovery app's synthetic test data; never run them on a personal phone.
The instrumentation targets local.snapexplorer.recovery, uses its separate
signing key, and does not target the original installed package.
SearchGroupsSmoke covers complete-query grouping, copies crossing page boundaries,
counts/arrow positions, distinct context, cancellation and reversible display.
MigrationSmoke exports and reimports synthetic records and checks content hashes.
Smoke covers normal navigation, pins/groups, paging, previews and search UI.
