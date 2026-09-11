-- Phase 9: server-authoritative undo/redo.
--
-- board_event grows two columns distinguishing what produced each event and, for an
-- undo/redo event, which earlier event it reverses/re-applies. Both are nullable-free —
-- every existing row backfills to NORMAL_OPERATION / NULL, which is exactly what those
-- rows are.
ALTER TABLE board_event ADD COLUMN IF NOT EXISTS event_kind VARCHAR(20) NOT NULL DEFAULT 'NORMAL_OPERATION';
ALTER TABLE board_event ADD COLUMN IF NOT EXISTS source_event_id UUID;
ALTER TABLE board_event ALTER COLUMN event_kind DROP DEFAULT;

-- One row per undoable action in one user's per-board history, in creation order
-- (stack_position). expected_versions is the per-object version (or -1 for "must not
-- exist") the next toggle of this entry requires, updated after every undo/redo of it —
-- see BoardUndoStackEntry / UndoRedoTransactionService for how it's used to detect a
-- conflicting change since this entry last settled.
CREATE TABLE IF NOT EXISTS board_undo_stack_entry (
    id                  UUID PRIMARY KEY,
    board_id            UUID NOT NULL REFERENCES boards (id) ON DELETE CASCADE,
    user_id             UUID NOT NULL,
    stack_position      BIGINT NOT NULL,
    last_event_id       UUID NOT NULL,
    last_event_sequence BIGINT NOT NULL,
    -- `change`'s own JSON carries a `changeType` discriminator (Jackson @JsonTypeInfo);
    -- no separate column duplicates it since nothing queries by type today.
    change              JSONB NOT NULL,
    expected_versions   JSONB NOT NULL,
    status              VARCHAR(10) NOT NULL,
    created_at          TIMESTAMP NOT NULL,
    updated_at          TIMESTAMP NOT NULL,
    CONSTRAINT uk_board_undo_stack_entry_position UNIQUE (board_id, user_id, stack_position)
);

-- One row per (board, user): how many of that user's stack entries, from position 1, are
-- currently applied. Locked FOR UPDATE before every undo/redo so two concurrent requests
-- for the same user+board — on any instance — serialize on this row instead of both
-- toggling the same entry.
CREATE TABLE IF NOT EXISTS board_undo_cursor (
    board_id     UUID NOT NULL REFERENCES boards (id) ON DELETE CASCADE,
    user_id      UUID NOT NULL,
    top_position BIGINT NOT NULL DEFAULT 0,
    max_position BIGINT NOT NULL DEFAULT 0,
    updated_at   TIMESTAMP NOT NULL,
    PRIMARY KEY (board_id, user_id)
);
