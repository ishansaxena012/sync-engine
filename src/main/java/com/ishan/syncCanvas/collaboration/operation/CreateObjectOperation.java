package com.ishan.syncCanvas.collaboration.operation;

import com.ishan.syncCanvas.canvas.entity.CanvasObject;

import java.time.Instant;
import java.util.UUID;

public record CreateObjectOperation(

        UUID boardId,

        UUID userId,

        CanvasObject canvasObject,

        Instant timestamp

) implements Operation {

    @Override
    public OperationType type() {
        return OperationType.CREATE_OBJECT;
    }
}