package com.ishan.syncCanvas.collaboration.operation;

import java.time.Instant;
import java.util.UUID;

public record MoveObjectOperation(

        UUID boardId,

        UUID userId,

        UUID objectId,

        double x,

        double y,

        Instant timestamp

) implements Operation {

    @Override
    public OperationType type() {
        return OperationType.MOVE_OBJECT;
    }
}
