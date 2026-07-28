package com.ishan.syncCanvas.collaboration.exception;

public class VersionMismatchException extends CollaborationException {

    public VersionMismatchException(Long expected, Long actual) {
        super("Expected version " + expected + " but found " + actual);
    }
}