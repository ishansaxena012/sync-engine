package com.ishan.syncCanvas.collaboration.lifecycle;

import java.time.Instant;
import java.util.UUID;

/**
 * Broadcast on {@code /topic/boards/{boardId}/closed} when a board is deleted, so every
 * client still subscribed to it (potentially on a different instance than the one that
 * handled the delete) finds out immediately instead of only discovering it via a
 * rejected write the next time they try to edit.
 */
public record BoardClosedEvent(UUID boardId, Instant timestamp) {

    public static BoardClosedEvent of(UUID boardId) {
        return new BoardClosedEvent(boardId, Instant.now());
    }
}
