package com.ishan.syncCanvas.collaboration.operation;

import java.time.Instant;
import java.util.UUID;

public record RotateObjectOperation(

        UUID boardId,

        UUID userId,

        UUID objectId,

        double rotation,

        Instant timestamp

) implements Operation {

    @Override
    public OperationType type() {
        return OperationType.ROTATE_OBJECT;
    }
}