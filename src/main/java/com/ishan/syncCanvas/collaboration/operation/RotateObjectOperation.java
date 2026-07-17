package com.ishan.syncCanvas.collaboration.operation;

import java.time.Instant;
import java.util.UUID;

public record RotateObjectOperation(
        UUID boardId,
        UUID userId,
        Instant timestamp,
        UUID objectId,
        double rotation) implements Operation {

    @Override
    public OperationType type() {
        return OperationType.ROTATE_OBJECT;
    }

}