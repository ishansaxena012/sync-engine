package com.ishan.syncCanvas.collaboration.dto;

import java.util.UUID;

/**
 * Reply to a {@code /app/boards/{boardId}/undo} or {@code /redo} request, delivered to
 * {@code /user/queue/boards/{boardId}/undo} or {@code /redo}. {@code status} is one of
 * {@code OK}, {@code NOTHING_TO_UNDO}, {@code NOTHING_TO_REDO}, or {@code UNDO_CONFLICT}/
 * {@code REDO_CONFLICT} — all normal outcomes, not error responses.
 */
public record UndoRedoResponse(
        String status,
        UUID boardId,
        String action,
        Long sourceSequence,
        Long sequence) {
}
