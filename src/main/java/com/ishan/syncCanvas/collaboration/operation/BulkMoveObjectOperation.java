package com.ishan.syncCanvas.collaboration.operation;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record BulkMoveObjectOperation(
        UUID operationId,
        UUID boardId,
        UUID userId,
        Instant timestamp,
        List<ObjectMove> moves
) implements Operation {
    
    public record ObjectMove(
            UUID objectId,
            Long expectedVersion,
            double x,
            double y
    ) {}

    @Override
    public OperationType type() {
        return OperationType.BULK_MOVE_OBJECT;
    }
}
