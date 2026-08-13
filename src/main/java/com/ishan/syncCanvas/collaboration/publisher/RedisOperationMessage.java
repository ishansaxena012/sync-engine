package com.ishan.syncCanvas.collaboration.publisher;

import com.ishan.syncCanvas.collaboration.operation.Operation;

/**
 * Wire envelope used only on the internal Redis channel between SyncEngine instances.
 * Carries the originating instance's ID so that instance can ignore its own echo
 * without a second, redundant local broadcast.
 */
public record RedisOperationMessage(
        String originInstanceId,
        Operation operation) {
}
