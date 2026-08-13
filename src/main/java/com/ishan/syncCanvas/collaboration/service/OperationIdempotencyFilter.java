package com.ishan.syncCanvas.collaboration.service;

import java.time.Duration;
import java.util.UUID;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Detects duplicate (echoed or retried) operations by tracking recently seen
 * operationIds in Redis, so the same operationId is never applied twice even if a
 * retry lands on a different SyncEngine instance.
 *
 * <p>When the server broadcasts an operation to {@code /topic/boards/{id}}, every
 * subscriber — including the original sender — receives it. If the client naively
 * re-submits what it receives from the topic, the server would process the same
 * operation twice, inserting duplicate canvas objects and dirtying the board again.
 *
 * <p>Each operationId is recorded via an atomic {@code SET ... NX EX} so the "seen"
 * set is shared cluster-wide and self-bounded by the TTL.
 *
 * <p>This check runs synchronously on the STOMP message-handling path for every
 * operation, so a Redis outage or slow response must never block collaboration: on
 * any Redis error the operation is treated as new (fail-open). Worst case during an
 * outage is a rare duplicate a user can undo; the alternative — blocking all editing
 * on a Redis blip — is worse.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OperationIdempotencyFilter {

    private static final String KEY_PREFIX = "syncCanvas:op:";
    private static final Duration TTL = Duration.ofMinutes(10);

    private final StringRedisTemplate redisTemplate;

    /**
     * Records the operation ID and returns {@code true} if it is a <em>new</em>
     * (non-duplicate) operation, or {@code false} if it has already been processed
     * by this or another instance within the TTL window.
     */
    public boolean registerIfNew(UUID operationId) {
        if (operationId == null) {
            // No ID supplied — allow through (backwards-compat with old clients)
            return true;
        }
        try {
            Boolean isNew = redisTemplate.opsForValue()
                    .setIfAbsent(KEY_PREFIX + operationId, "1", TTL);
            return Boolean.TRUE.equals(isNew);
        } catch (Exception ex) {
            log.error("Idempotency check failed for operation {}; allowing through (fail-open)", operationId, ex);
            return true;
        }
    }
}
