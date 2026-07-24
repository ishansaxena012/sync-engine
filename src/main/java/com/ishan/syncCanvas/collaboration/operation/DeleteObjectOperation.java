package com.ishan.syncCanvas.collaboration.operation;

import java.time.Instant;
import java.util.UUID;

public record DeleteObjectOperation(

        UUID operationId,

        UUID boardId,

        UUID userId,

        UUID objectId,

        Instant timestamp

) implements Operation {

    @Override
    public OperationType type() {
        return OperationType.DELETE_OBJECT;
    }
}