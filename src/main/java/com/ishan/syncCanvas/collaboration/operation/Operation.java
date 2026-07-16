package com.ishan.syncCanvas.collaboration.operation;

import java.time.Instant;
import java.util.UUID;

public sealed interface Operation
        permits
        CreateObjectOperation,
        MoveObjectOperation,
        DeleteObjectOperation,
        ChangePayloadOperation,
        RotateObjectOperation {

    UUID boardId();

    UUID userId();

    Instant timestamp();

    OperationType type();
}