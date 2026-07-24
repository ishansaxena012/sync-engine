package com.ishan.syncCanvas.collaboration.operation;

import com.ishan.syncCanvas.canvas.dto.CreateCanvasObjectRequest;
// import com.ishan.syncCanvas.canvas.entity.CanvasObject;

import java.time.Instant;
import java.util.UUID;

public record CreateObjectOperation(

        UUID operationId,

        UUID boardId,

        UUID userId,

        CreateCanvasObjectRequest canvasObject,

        Instant timestamp

) implements Operation {

    @Override
    public OperationType type() {
        return OperationType.CREATE_OBJECT;
    }
}