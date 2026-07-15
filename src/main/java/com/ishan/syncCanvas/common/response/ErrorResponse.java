package com.ishan.syncCanvas.common.response;


import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;

@Getter
@Builder
public class ErrorResponse {
    private boolean success;
    private String message;
    private List<ValidationError> errors;

    @Builder.Default
    private Instant timestamp = Instant.now();
}
