package com.ishan.syncCanvas.collaboration.exception;

import java.util.UUID;

public class ObjectNotFoundException extends CollaborationException {

    public ObjectNotFoundException(UUID objectId) {
        super("Canvas object not found: " + objectId);
    }

}