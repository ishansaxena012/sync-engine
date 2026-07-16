package com.ishan.syncCanvas.common.response;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

public final class ResponseUtil {

        private ResponseUtil() {
        }

        public static <T> ResponseEntity<ApiResponse<T>> success(
                        T data,
                        String message,
                        HttpStatus status) {
                return ResponseEntity.status(status)
                                .body(
                                                ApiResponse.<T>builder()
                                                                .success(true)
                                                                .message(message)
                                                                .data(data)
                                                                .build());
        }

        public static <T> ResponseEntity<ApiResponse<T>> created(
                        String message,
                        T data) {
                return ResponseEntity.status(HttpStatus.CREATED)
                                .body(
                                                ApiResponse.<T>builder()
                                                                .success(true)
                                                                .message(message)
                                                                .data(data)
                                                                .build());
        }

        public static ResponseEntity<ApiResponse<Void>> success(
                        String message) {
                return ResponseEntity.ok(
                                ApiResponse.<Void>builder()
                                                .success(true)
                                                .message(message)
                                                .build());
        }
}