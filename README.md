Production-grade real-time synchronization engine built with Java, Spring Boot, WebSockets, Redis, and PostgreSQL. Demonstrates room-based collaboration, presence, shared state synchronization, event ordering, recovery, and horizontal scalability.

## Undo / Redo

Server-authoritative: a client sends only `UNDO`/`REDO` over STOMP
(`/app/boards/{boardId}/undo`, `/redo`) and gets a reply on
`/user/queue/boards/{boardId}/undo`/`/redo` — it never submits an inverse
operation, prior state, or sequence number.

- History is durable (`board_undo_stack_entry`, `board_undo_cursor` — see
  `V5__undo_redo_history.sql`), per user and per board, and survives restarts.
- Undo/redo events reuse the Phase 8 `board_event` log (`event_kind` +
  `source_event_id` distinguish `NORMAL_OPERATION`/`UNDO_OPERATION`/
  `REDO_OPERATION`) rather than a second event store.
- Concurrent undo/redo for the same user+board — across instances — is
  serialized by a `SELECT ... FOR UPDATE` lock on that user's cursor row
  (`BoardUndoCursorRepository`), not JVM-local locking.
- A conflicting concurrent edit is detected by comparing the live object
  version(s) against what the stack entry expects, and reported back as
  `UNDO_CONFLICT`/`REDO_CONFLICT` rather than silently overwriting it.

See `com.ishan.syncCanvas.collaboration.undo` for the implementation.
