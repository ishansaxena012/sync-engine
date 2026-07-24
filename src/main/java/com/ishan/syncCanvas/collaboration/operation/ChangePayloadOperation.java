package com.ishan.syncCanvas.collaboration.operation;

import java.time.Instant;
import java.util.UUID;

import com.ishan.syncCanvas.canvas.domain.CanvasPayload;

public record ChangePayloadOperation(
        UUID operationId,
        UUID boardId,
        UUID userId,
        Instant timestamp,
        UUID objectId,
        CanvasPayload payload) implements Operation {

    @Override
    public OperationType type() {
        return OperationType.CHANGE_PAYLOAD;
    }
}