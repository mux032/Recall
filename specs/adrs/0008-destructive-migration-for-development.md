---
status: accepted
date: 2024-06-01
---
# ADR-0008: `fallbackToDestructiveMigration()` During Development

## Context and Problem Statement

Room requires explicit `Migration` objects for every database schema version change. The Recall database has undergone 5 schema versions during development (v1 through v5), with changes including new columns, new indexes, new entities, and TypeConverter additions. Writing and testing complete migration paths for each incremental change during rapid feature development carries significant overhead when no production users exist yet.

## Decision

Use `fallbackToDestructiveMigration()` in the `Room.databaseBuilder()` call during development (all versions up to and including v5). This decision is explicitly **temporary** and constitutes a **pre-production blocker**.

## Rationale

- During early development, the schema changes frequently (5 versions in the first development cycle). Writing a migration for each change would add ~1–2 hours per schema change with no benefit to any user.
- Data loss on upgrade is acceptable during development: the app re-indexes screenshots from MediaStore on every cold launch, so no user data is permanently lost — it is recreated by `ScanExistingWorker → BackgroundOcrWorker`.
- The `RecallDatabase` KDoc explicitly documents this risk and calls out the pre-production requirement.
- `exportSchema = false` is set — schema snapshots are not being tracked. This must be changed to `exportSchema = true` when production migrations are written.

## Consequences

### Positive
- Zero migration overhead during iterative development; schema can evolve freely.
- Developer experience: no "migration needed" crashes when pulling new branches.

### Negative / Trade-offs
- **Critical risk:** If this configuration is shipped to production, any app update that changes the schema will silently wipe all user-indexed data.
- `exportSchema = false` means there is no Room-generated schema snapshot history to diff; migrations must be reconstructed from git history.
- All migration code must be written and tested before the first public release — this is a hard pre-production gate.
- `MigrationTestHelper` integration tests must be added to validate all v1→v2→v3→v4→v5 migration paths.
