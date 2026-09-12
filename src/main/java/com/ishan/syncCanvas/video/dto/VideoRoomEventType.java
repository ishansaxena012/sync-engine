package com.ishan.syncCanvas.video.dto;

public enum VideoRoomEventType {
    ROOM_STARTED,
    PARTICIPANT_JOINED,
    PARTICIPANT_LEFT,
    ROOM_ENDED,
    /** Current state with no membership change — reserved for a future explicit "refresh" request; not emitted in this phase. */
    ROOM_STATE
}
