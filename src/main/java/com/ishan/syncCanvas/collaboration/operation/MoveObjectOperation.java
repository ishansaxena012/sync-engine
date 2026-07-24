package com.ishan.syncCanvas.collaboration.operation;

import java.time.Instant;
import java.util.UUID;

public record MoveObjectOperation(
        UUID operationId,
        UUID boardId,
        UUID userId,
        Instant timestamp,
        UUID objectId,
        double x,
        double y) implements Operation {

    @Override
    public OperationType type() {
        return OperationType.MOVE_OBJECT;
    }
}
