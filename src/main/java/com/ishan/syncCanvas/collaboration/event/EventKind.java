package com.ishan.syncCanvas.collaboration.event;

/**
 * What produced a {@link BoardEvent}. All three kinds are ordinary, immutable events in
 * the same durable log — undo/redo never rewrites or removes an earlier event, it only
 * ever appends a new one that happens to carry the inverse (or re-applied) operation.
 */
public enum EventKind {
    /** A client-submitted operation, applied and committed through the normal pipeline. */
    NORMAL_OPERATION,
    /** The inverse of an earlier event, committed by an UNDO request. */
    UNDO_OPERATION,
    /** A previously-undone event's operation, re-applied by a REDO request. */
    REDO_OPERATION
}
