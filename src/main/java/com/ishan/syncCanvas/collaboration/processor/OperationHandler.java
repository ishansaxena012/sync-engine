package com.ishan.syncCanvas.collaboration.processor;

import com.ishan.syncCanvas.collaboration.operation.Operation;
import com.ishan.syncCanvas.collaboration.session.BoardSession;

public interface OperationHandler<T extends Operation> {

    Class<T> supports();

    /** Live path: resolves the board's active session, locks it, applies, and marks the board dirty. */
    void handle(T operation);

    /**
     * The single source of this operation's mutation semantics, applied to the given
     * session. Used both by {@link #handle} (LIVE) and by durable-event reconstruction
     * (REPLAY) so the two can never drift apart. Does not lock or mark dirty — the caller
     * owns that.
     */
    void apply(T operation, BoardSession session, ApplyMode mode);
}
