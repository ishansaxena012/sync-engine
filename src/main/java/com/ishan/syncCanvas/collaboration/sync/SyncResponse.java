package com.ishan.syncCanvas.collaboration.sync;

import com.ishan.syncCanvas.collaboration.operation.SequencedOperation;

import java.util.List;
import java.util.UUID;

public record SyncResponse(
        SyncStatus status,
        UUID boardId,
        long currentSequence,
        List<SequencedOperation> operations) {
}
