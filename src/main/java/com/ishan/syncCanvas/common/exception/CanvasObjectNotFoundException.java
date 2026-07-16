package com.ishan.syncCanvas.common.exception;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class CanvasObjectNotFoundException extends RuntimeException {
    public CanvasObjectNotFoundException(String message) {
        super(message);
    }

    public CanvasObjectNotFoundException(UUID id) {
        super("Canvas object not found with id: " + id);
    }
}
