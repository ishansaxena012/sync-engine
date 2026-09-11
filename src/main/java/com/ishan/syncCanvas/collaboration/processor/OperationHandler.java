package com.ishan.syncCanvas.collaboration.processor;

import com.ishan.syncCanvas.collaboration.operation.Operation;
import com.ishan.syncCanvas.collaboration.session.BoardSession;
import com.ishan.syncCanvas.collaboration.undo.UndoableChange;

public interface OperationHandler<T extends Operation> {

    Class<T> supports();

    /** Live path: resolves the board's active session, locks it, applies, and marks the board dirty. */
    UndoableChange handle(T operation);

    /**
     * The single source of this operation's mutation semantics, applied to the given
     * session. Used by {@link #handle} (LIVE), by durable-event reconstruction (REPLAY),
     * and by undo/redo (whichever mode is appropriate for the constructed operation) so
     * none of them can drift apart. Does not lock or mark dirty — the caller owns that.
     *
     * @return what this application changed, captured from the pre-mutation state, so a
     *         caller building undo/redo history can record it. Reconstruction ignores it.
     */
    UndoableChange apply(T operation, BoardSession session, ApplyMode mode);
}
