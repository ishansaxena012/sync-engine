package com.ishan.syncCanvas.collaboration.dto;

import java.time.Instant;
import java.util.UUID;

public record OperationErrorResponse(

        UUID operationId,
        String type,
        String message,
        Instant timestamp

) {
}