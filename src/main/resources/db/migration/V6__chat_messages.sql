-- Board chat.
--
-- Deliberately NOT part of board_event: chat carries no board sequence, takes part in
-- no snapshot, reconstruction, replay or undo/redo, and a chat message must never
-- consume an operation sequence number. It is an independent per-board channel that
-- happens to share the same authorization rule and the same Redis fan-out mechanism.
--
-- Postgres is the durable source of truth; Redis only distributes an already-committed
-- message to the other instances. Nothing chat-related is persisted in Redis.
CREATE TABLE IF NOT EXISTS chat_message (
    id         UUID PRIMARY KEY,
    board_id   UUID NOT NULL REFERENCES boards (id) ON DELETE CASCADE,
    -- No FK to users: that table is not Flyway-managed in this project (see
    -- FlywayConfig), and board_undo_stack_entry / board_event hold user_id the same way.
    user_id    UUID NOT NULL,
    -- Bounded at the same 2000 characters ChatService enforces, so an oversized
    -- message can never reach the table even if it bypassed the service. Postgres
    -- counts characters rather than bytes, and the service counts UTF-16 units, so
    -- anything the service accepts fits — including non-BMP emoji.
    message    VARCHAR(2000) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

-- Serves the only query the history endpoint makes: one board's messages in
-- created_at order. Postgres scans it backwards for the newest-first page, and the
-- trailing id keeps the ordering total when two messages share a timestamp — without
-- that tiebreak, paging could repeat or skip a row.
CREATE INDEX IF NOT EXISTS idx_chat_message_board_created
    ON chat_message (board_id, created_at, id);
