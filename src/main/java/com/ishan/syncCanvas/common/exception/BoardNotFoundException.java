package com.ishan.syncCanvas.common.exception;

public class BoardNotFoundException extends RuntimeException {
    public BoardNotFoundException(String message){
        super(message);
    }

    public BoardNotFoundException(java.util.UUID id) {
        super("Board with ID " + id + " not found");
    }
}
