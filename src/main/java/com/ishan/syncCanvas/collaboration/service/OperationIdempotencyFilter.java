package com.ishan.syncCanvas.collaboration.service;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Detects duplicate (echoed) operations by tracking recently seen operationIds.
 *
 * <p>When the server broadcasts an operation to {@code /topic/boards/{id}}, every
 * subscriber — including the original sender — receives it. If the client naively
 * re-submits what it receives from the topic, the server would process the same
 * operation twice, inserting duplicate canvas objects and dirtying the board again.
 *
 * <p>This filter maintains a bounded LRU cache of the last {@value #MAX_SIZE}
 * operation IDs. On each incoming operation we check whether the ID has already
 * been seen; if so the operation is a duplicate and should be silently dropped.
 */
@Component
public class OperationIdempotencyFilter {

    private static final int MAX_SIZE = 10_000;

    /** Thread-safe bounded LRU set of recently processed operation IDs. */
    private final Map<UUID, Boolean> seen = Collections.synchronizedMap(
            new LinkedHashMap<>(MAX_SIZE, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<UUID, Boolean> eldest) {
                    return size() > MAX_SIZE;
                }
            });

    /**
     * Records the operation ID and returns {@code true} if it is a <em>new</em>
     * (non-duplicate) operation, or {@code false} if it has already been processed.
     */
    public boolean registerIfNew(UUID operationId) {
        if (operationId == null) {
            // No ID supplied — allow through (backwards-compat with old clients)
            return true;
        }
        return seen.putIfAbsent(operationId, Boolean.TRUE) == null;
    }
}
