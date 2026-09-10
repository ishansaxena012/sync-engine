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
 * Redis-authoritative per-board operation sequencing and a bounded recent-operation
 * replay buffer for reconnect sync. Deliberately holds no JVM-local state — the
 * sequence counter and replay buffer are both Redis structures shared across every
 * SyncEngine instance, by design (see the "STRICTLY DO NOT USE AtomicLong/local state"
 * requirement this was built against).
 *
 * <p>This is a temporary recent-replay layer, not a durable event store — bounded by
 * both count and TTL, meant to cover a brief disconnect, not full history recovery.
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

    /** Assigns the next monotonically increasing sequence for this board. Never call twice for one operation. */
    public long nextSequence(UUID boardId) {
        Long sequence = redisTemplate.opsForValue().increment(sequenceKey(boardId));
        return sequence == null ? 1L : sequence;
    }

    /** Current sequence without incrementing — 0 if the board has never had an operation. */
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
     * Records an already-sequenced operation in the bounded replay buffer. Call this
     * exactly once, from the instance that actually assigned the sequence — other
     * instances receiving the same operation via Redis pub/sub must not call this
     * again, since the buffer is shared and the entry is already there.
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

    /**
     * Builds a reconnect-sync response. Never claims a gap-free replay unless the
     * buffer's oldest retained entry actually starts at {@code lastSequenceReceived + 1}
     * — if older history has already been evicted (by count or TTL), returns
     * SYNC_REQUIRED instead of a silently-incomplete replay.
     */
    public SyncResponse buildSyncResponse(UUID boardId, SyncRequest request) {
        long lastSequenceReceived = request == null || request.lastSequenceReceived() == null
                ? 0L
                : request.lastSequenceReceived();

        long current = currentSequence(boardId);

        if (lastSequenceReceived >= current) {
            return new SyncResponse(SyncStatus.UP_TO_DATE, boardId, current, List.of());
        }

        String key = operationsKey(boardId);
        Set<ZSetOperations.TypedTuple<String>> oldest;
        try {
            oldest = redisTemplate.opsForZSet().rangeWithScores(key, 0, 0);
        } catch (Exception ex) {
            log.error("Failed to read replay buffer floor for board {}", boardId, ex);
            return new SyncResponse(SyncStatus.SYNC_REQUIRED, boardId, current, List.of());
        }

        if (oldest == null || oldest.isEmpty()) {
            return new SyncResponse(SyncStatus.SYNC_REQUIRED, boardId, current, List.of());
        }

        double oldestAvailableSequence = oldest.iterator().next().getScore();
        if (oldestAvailableSequence > lastSequenceReceived + 1) {
            // The client needs history that's already fallen out of the bounded buffer.
            return new SyncResponse(SyncStatus.SYNC_REQUIRED, boardId, current, List.of());
        }

        Set<String> rawOperations;
        try {
            rawOperations = redisTemplate.opsForZSet().rangeByScore(key, lastSequenceReceived + 1, Double.MAX_VALUE);
        } catch (Exception ex) {
            log.error("Failed to read replay range for board {}", boardId, ex);
            return new SyncResponse(SyncStatus.SYNC_REQUIRED, boardId, current, List.of());
        }

        List<SequencedOperation> operations = (rawOperations == null ? Set.<String>of() : rawOperations).stream()
                .map(this::deserialize)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingLong(SequencedOperation::sequence))
                .toList();

        return new SyncResponse(SyncStatus.OK, boardId, current, operations);
    }

    /** Removes this board's sequence counter and replay buffer — call on board deletion. */
    public void clearBoardState(UUID boardId) {
        redisTemplate.delete(sequenceKey(boardId));
        redisTemplate.delete(operationsKey(boardId));
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
