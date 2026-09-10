package com.ishan.syncCanvas.collaboration.sync;

public enum SyncStatus {
    /** Client's lastSequenceReceived already matches the board's current sequence. */
    UP_TO_DATE,
    /** Operations are included, in ascending sequence order, covering the full gap. */
    OK,
    /** The requested range is no longer in the replay buffer; client needs a full state refetch. */
    SYNC_REQUIRED
}
