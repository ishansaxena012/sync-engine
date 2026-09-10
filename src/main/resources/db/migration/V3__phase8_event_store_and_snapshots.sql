-- Phase 8: durable event store, PostgreSQL-authoritative per-board sequence, board snapshots.
-- Types mirror V1/V2 conventions (UUID ids, TIMESTAMP, JSONB) so Hibernate ddl-auto=validate passes.
-- IF NOT EXISTS guards make this safe on dev databases where Hibernate ddl-auto=update may
-- already have created some of these objects from the entity mappings.

ALTER TABLE boards ADD COLUMN IF NOT EXISTS sequence BIGINT NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS board_event (
    id             UUID PRIMARY KEY,
    board_id       UUID NOT NULL REFERENCES boards (id) ON DELETE CASCADE,
    sequence       BIGINT NOT NULL,
    operation_id   UUID NOT NULL,
    user_id        UUID NOT NULL,
    operation_type VARCHAR(50) NOT NULL,
    payload        JSONB NOT NULL,
    created_at     TIMESTAMP NOT NULL,
    CONSTRAINT uk_board_event_board_sequence UNIQUE (board_id, sequence),
    CONSTRAINT uk_board_event_board_operation UNIQUE (board_id, operation_id)
);
-- uk_board_event_board_sequence is itself a btree on (board_id, sequence), which is exactly
-- the index the "WHERE board_id = ? AND sequence > ? ORDER BY sequence" query needs — a
-- second identical index would only add write cost.

CREATE TABLE IF NOT EXISTS board_snapshot (
    id         UUID PRIMARY KEY,
    board_id   UUID NOT NULL REFERENCES boards (id) ON DELETE CASCADE,
    sequence   BIGINT NOT NULL,
    state      JSONB NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_board_snapshot_board_sequence UNIQUE (board_id, sequence)
);
-- Same reasoning: the unique btree serves "WHERE board_id = ? AND sequence <= ? ORDER BY sequence DESC LIMIT 1".
