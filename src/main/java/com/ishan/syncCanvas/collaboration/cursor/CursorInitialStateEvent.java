package com.ishan.syncCanvas.collaboration.cursor;

import java.util.List;
import java.util.UUID;

/** Sent privately to a client right after it subscribes to a board's cursor topic. */
public record CursorInitialStateEvent(CursorEventType type, UUID boardId, List<CursorEvent> cursors) {

    public CursorInitialStateEvent(UUID boardId, List<CursorEvent> cursors) {
        this(CursorEventType.CURSOR_INITIAL_STATE, boardId, cursors);
    }
}
