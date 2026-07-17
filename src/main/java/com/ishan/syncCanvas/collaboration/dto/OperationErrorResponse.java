package com.ishan.syncCanvas.collaboration.dto;

import java.time.Instant;

public record OperationErrorResponse(

        String type,
        String message,
        Instant timestamp

) {
}