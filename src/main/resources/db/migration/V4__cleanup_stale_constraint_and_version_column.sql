-- V4 cleanup, folding two pieces of drift left over from when Hibernate ddl-auto=update
-- (not Flyway) owned this schema, now that Flyway is authoritative and prod runs
-- ddl-auto=validate.
--
-- 1. Hibernate's schema-update tooling had generated a CHECK constraint enumerating
--    CanvasObjectType's values at the time the column was first created. It was never
--    widened when STICKY_NOTE/ELLIPSE/TRIANGLE/DIAMOND were added later, so inserting one
--    of those newer shapes would violate it at the database level even though the Java
--    enum and application logic fully support them. This was previously worked around by
--    a CommandLineRunner dropping it on every application boot (SyncEngineApplication) —
--    that startup-time schema mutation is now removed in favor of this migration, which
--    is idempotent (IF EXISTS) and runs on a fresh database too, where the constraint
--    never existed in the first place (V2 declares `type` as plain VARCHAR, no CHECK).
ALTER TABLE canvas_objects DROP CONSTRAINT IF EXISTS canvas_objects_type_check;

-- 2. `canvas_objects.version` is used by every operation handler for optimistic
--    concurrency (CanvasObject.version) but was never declared in a migration — it only
--    ever existed because dev's ddl-auto=update created it from the entity mapping. On a
--    genuinely fresh database (Flyway-only, ddl-auto=validate) the column would be
--    missing and Hibernate validation would fail at startup.
ALTER TABLE canvas_objects ADD COLUMN IF NOT EXISTS version BIGINT;
