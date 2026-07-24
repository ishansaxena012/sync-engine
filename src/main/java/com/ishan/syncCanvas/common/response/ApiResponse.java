package com.ishan.syncCanvas.common.response;

import lombok.*;

import java.time.Instant;

@Getter
@Builder
public class ApiResponse<T> {
    private boolean success;
    private String message;
    private T data;
    @Builder.Default
    private Instant timestamp = Instant.now();
}
