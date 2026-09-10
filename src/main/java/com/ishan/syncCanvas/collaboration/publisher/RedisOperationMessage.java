package com.ishan.syncCanvas.collaboration.publisher;

import com.ishan.syncCanvas.collaboration.operation.Operation;

/**
 * Wire envelope used only on the internal Redis channel between SyncEngine instances.
 * Carries the originating instance's ID so that instance can ignore its own echo
 * without a second, redundant local broadcast, and the sequence number the
 * originating instance already assigned via Redis — other instances relay that same
 * value rather than assigning their own, since the sequence counter is shared.
 */
public record RedisOperationMessage(
        String originInstanceId,
        long sequence,
        Operation operation) {
}
