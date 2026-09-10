package com.ishan.syncCanvas.collaboration.processor;

/**
 * How an operation is being applied to a {@code BoardSession}.
 */
public enum ApplyMode {
    /**
     * A client-submitted operation: enforce {@code expectedVersion} optimistic checks and
     * never trust client-supplied object ids (fresh ones are generated server-side).
     */
    LIVE,
    /**
     * Replaying an already-committed durable event during reconstruction: the history has
     * already happened, so {@code expectedVersion} is not re-validated (cross-instance
     * ordering can legitimately make it stale), and the server-assigned ids stored in the
     * event are trusted so replayed objects keep their original identity.
     */
    REPLAY
}
