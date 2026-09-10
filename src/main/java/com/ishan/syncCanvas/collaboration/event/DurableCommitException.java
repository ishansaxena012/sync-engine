package com.ishan.syncCanvas.collaboration.event;

import com.ishan.syncCanvas.collaboration.exception.CollaborationException;

/**
 * The PostgreSQL transaction that assigns an operation's sequence and records its event
 * did not commit. The operation was therefore not accepted: nothing was published and
 * the client may retry.
 */
public class DurableCommitException extends CollaborationException {

    public DurableCommitException(Throwable cause) {
        super("Operation could not be durably committed: " + cause.getMessage());
        initCause(cause);
    }
}
