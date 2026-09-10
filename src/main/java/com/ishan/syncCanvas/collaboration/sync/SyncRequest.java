package com.ishan.syncCanvas.collaboration.sync;

/**
 * Inbound reconnect/sync request. {@code lastSequenceReceived} is the highest sequence
 * the client has already applied for this board; null/absent is treated as 0 (replay
 * everything the buffer still has).
 */
public record SyncRequest(Long lastSequenceReceived) {
}
