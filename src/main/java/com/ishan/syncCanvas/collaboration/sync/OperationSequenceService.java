package com.ishan.syncCanvas.collaboration.sync;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ishan.syncCanvas.collaboration.operation.SequencedOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Redis recent-operation replay buffer plus a Redis mirror of each board's current
 * sequence, for fast reconnect sync. Since Phase 8 neither is authoritative — the
 * PostgreSQL event store is (see {@code BoardEventService}); this is the cache in
 * front of it, bounded by both count and TTL. Holds no JVM-local state.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OperationSequenceService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.collaboration.replay-buffer-size:500}")
    private long replayBufferSize;

    @Value("${app.collaboration.replay-ttl-seconds:600}")
    private long replayTtlSeconds;

    /** Mirrors a just-committed durable sequence into Redis. Best-effort cache write, called after commit. */
    public void setCurrentSequence(UUID boardId, long sequence) {
        redisTemplate.opsForValue().set(sequenceKey(boardId), Long.toString(sequence));
    }

    /** Redis's view of the current sequence — 0 if unknown. Not authoritative; PostgreSQL is. */
    public long currentSequence(UUID boardId) {
        try {
            String raw = redisTemplate.opsForValue().get(sequenceKey(boardId));
            return raw == null ? 0L : Long.parseLong(raw);
        } catch (Exception ex) {
            log.error("Failed to read current sequence for board {}", boardId, ex);
            return 0L;
        }
    }

    /**
     * Records an already-committed, already-sequenced operation in the bounded replay
     * buffer. Call once, from the instance that committed it — relays via pub/sub must
     * not write it again.
     */
    public void recordForReplay(UUID boardId, SequencedOperation sequencedOperation) {
        try {
            String key = operationsKey(boardId);
            String json = objectMapper.writeValueAsString(sequencedOperation);
            redisTemplate.opsForZSet().add(key, json, sequencedOperation.sequence());
            // Keep only the most recent replayBufferSize entries — negative rank bounds
            // let Redis compute this without a separate ZCARD round-trip, and safely
            // no-op while the buffer hasn't grown that large yet.
            redisTemplate.opsForZSet().removeRange(key, 0, -(replayBufferSize + 1));
            redisTemplate.expire(key, Duration.ofSeconds(replayTtlSeconds));
        } catch (Exception ex) {
            log.error("Failed to record operation {} for replay on board {}",
                    sequencedOperation.operation().operationId(), boardId, ex);
        }
    }

    /** Redis-only sync using Redis's own view of the current sequence. Prefer the overload with an authoritative value. */
    public SyncResponse buildSyncResponse(UUID boardId, SyncRequest request) {
        return buildSyncResponse(boardId, request, currentSequence(boardId));
    }

    /**
     * Builds a reply from the Redis buffer alone. Never claims a gap-free replay unless
     * the buffer's oldest retained entry actually starts at {@code lastSequenceReceived + 1};
     * otherwise returns SYNC_REQUIRED so the caller can fall back to the durable log.
     */
    public SyncResponse buildSyncResponse(UUID boardId, SyncRequest request, long currentSequence) {
        long lastSequenceReceived = request == null || request.lastSequenceReceived() == null
                ? 0L
                : request.lastSequenceReceived();

        if (lastSequenceReceived >= currentSequence) {
            return new SyncResponse(SyncStatus.UP_TO_DATE, boardId, currentSequence, List.of());
        }

        String key = operationsKey(boardId);
        Set<ZSetOperations.TypedTuple<String>> oldest;
        try {
            oldest = redisTemplate.opsForZSet().rangeWithScores(key, 0, 0);
        } catch (Exception ex) {
            log.error("Failed to read replay buffer floor for board {}", boardId, ex);
            return syncRequired(boardId, currentSequence);
        }

        if (oldest == null || oldest.isEmpty()) {
            return syncRequired(boardId, currentSequence);
        }

        double oldestAvailableSequence = oldest.iterator().next().getScore();
        if (oldestAvailableSequence > lastSequenceReceived + 1) {
            return syncRequired(boardId, currentSequence);
        }

        Set<String> rawOperations;
        try {
            rawOperations = redisTemplate.opsForZSet().rangeByScore(key, lastSequenceReceived + 1, Double.MAX_VALUE);
        } catch (Exception ex) {
            log.error("Failed to read replay range for board {}", boardId, ex);
            return syncRequired(boardId, currentSequence);
        }

        List<SequencedOperation> operations = (rawOperations == null ? Set.<String>of() : rawOperations).stream()
                .map(this::deserialize)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingLong(SequencedOperation::sequence))
                .toList();

        return new SyncResponse(SyncStatus.OK, boardId, currentSequence, operations);
    }

    /** Removes this board's sequence mirror and replay buffer — call on board deletion. */
    public void clearBoardState(UUID boardId) {
        redisTemplate.delete(sequenceKey(boardId));
        redisTemplate.delete(operationsKey(boardId));
    }

    private static SyncResponse syncRequired(UUID boardId, long currentSequence) {
        return new SyncResponse(SyncStatus.SYNC_REQUIRED, boardId, currentSequence, List.of());
    }

    private SequencedOperation deserialize(String json) {
        try {
            return objectMapper.readValue(json, SequencedOperation.class);
        } catch (Exception ex) {
            log.error("Failed to deserialize a replay buffer entry", ex);
            return null;
        }
    }

    private static String sequenceKey(UUID boardId) {
        return "sequence:board:" + boardId;
    }

    private static String operationsKey(UUID boardId) {
        return "operations:board:" + boardId;
    }
}
