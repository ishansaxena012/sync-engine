package com.ishan.syncCanvas.collaboration.exception;

public class BoardMismatchException extends CollaborationException {

    public BoardMismatchException() {
        super("Board ID mismatch between destination and operation payload.");
    }

}