package com.ishan.syncCanvas.collaboration.operation;

import java.time.Instant;
import java.util.UUID;

import com.ishan.syncCanvas.canvas.domain.CanvasPayload;

public record ChangePayloadOperation(

        UUID boardId,

        UUID userId,

        UUID objectId,

        CanvasPayload payload,

        Instant timestamp

) implements Operation {

    @Override
    public OperationType type() {
        return OperationType.CHANGE_PAYLOAD;
    }
}