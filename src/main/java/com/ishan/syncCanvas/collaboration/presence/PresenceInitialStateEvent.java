package com.ishan.syncCanvas.collaboration.presence;

import java.util.List;
import java.util.UUID;

/** Sent privately to a client right after it JOINs a board's presence channel. */
public record PresenceInitialStateEvent(PresenceEventType type, UUID boardId, List<PresenceEvent> participants) {

    public PresenceInitialStateEvent(UUID boardId, List<PresenceEvent> participants) {
        this(PresenceEventType.INITIAL_STATE, boardId, participants);
    }
}
