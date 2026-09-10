package com.ishan.syncCanvas.collaboration.sync;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ishan.syncCanvas.collaboration.operation.MoveObjectOperation;
import com.ishan.syncCanvas.collaboration.operation.SequencedOperation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.DefaultTypedTuple;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OperationSequenceServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private ZSetOperations<String, String> zSetOperations;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private OperationSequenceService service;

    private final UUID boardId = UUID.randomUUID();
    private final UUID otherBoardId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);

        service = new OperationSequenceService(redisTemplate, objectMapper);
        ReflectionTestUtils.setField(service, "replayBufferSize", 500L);
        ReflectionTestUtils.setField(service, "replayTtlSeconds", 600L);
    }

    private MoveObjectOperation move(UUID board) {
        return new MoveObjectOperation(UUID.randomUUID(), board, UUID.randomUUID(), Instant.now(),
                UUID.randomUUID(), 1L, 10.0, 20.0);
    }

    private String json(SequencedOperation op) throws Exception {
        return objectMapper.writeValueAsString(op);
    }

    // ---- sequence mirror ----

    @Test
    void setCurrentSequenceMirrorsCommittedValueIntoRedis() {
        service.setCurrentSequence(boardId, 42L);

        verify(valueOperations).set("sequence:board:" + boardId, "42");
    }

    @Test
    void currentSequenceIsZeroForUnknownBoard() {
        when(valueOperations.get("sequence:board:" + boardId)).thenReturn(null);

        assertThat(service.currentSequence(boardId)).isZero();
    }

    // ---- replay recording ----

    @Test
    void recordForReplayStoresInSortedSetBoundsAndSetsTtl() {
        SequencedOperation op = new SequencedOperation(42L, move(boardId));

        service.recordForReplay(boardId, op);

        String key = "operations:board:" + boardId;
        verify(zSetOperations).add(eq(key), anyString(), eq(42.0));
        verify(zSetOperations).removeRange(key, 0, -501L);
        verify(redisTemplate).expire(key, Duration.ofSeconds(600));
    }

    // ---- sync response ----

    @Test
    void upToDateWhenClientAlreadyHasAuthoritativeCurrentSequence() {
        SyncResponse response = service.buildSyncResponse(boardId, new SyncRequest(10L), 10L);

        assertThat(response.status()).isEqualTo(SyncStatus.UP_TO_DATE);
        assertThat(response.currentSequence()).isEqualTo(10L);
        assertThat(response.operations()).isEmpty();
        verify(zSetOperations, never()).rangeByScore(anyString(), anyDouble(), anyDouble());
    }

    @Test
    void twoArgOverloadFallsBackToRedisViewOfCurrentSequence() {
        when(valueOperations.get("sequence:board:" + boardId)).thenReturn("10");

        SyncResponse response = service.buildSyncResponse(boardId, new SyncRequest(10L));

        assertThat(response.status()).isEqualTo(SyncStatus.UP_TO_DATE);
        assertThat(response.currentSequence()).isEqualTo(10L);
    }

    @Test
    void nullLastSequenceIsTreatedAsZero() {
        SyncResponse response = service.buildSyncResponse(boardId, new SyncRequest(null), 0L);

        assertThat(response.status()).isEqualTo(SyncStatus.UP_TO_DATE);
    }

    @Test
    void replaysContiguousRangeInAscendingOrder() throws Exception {
        String key = "operations:board:" + boardId;
        SequencedOperation s11 = new SequencedOperation(11L, move(boardId));
        SequencedOperation s12 = new SequencedOperation(12L, move(boardId));
        SequencedOperation s13 = new SequencedOperation(13L, move(boardId));

        when(zSetOperations.rangeWithScores(key, 0, 0))
                .thenReturn(Set.of(new DefaultTypedTuple<>(json(s11), 11.0)));
        // Deliberately out of order to prove the service re-sorts by sequence.
        Set<String> raw = new LinkedHashSet<>();
        raw.add(json(s13));
        raw.add(json(s11));
        raw.add(json(s12));
        when(zSetOperations.rangeByScore(key, 11.0, Double.MAX_VALUE)).thenReturn(raw);

        SyncResponse response = service.buildSyncResponse(boardId, new SyncRequest(10L), 13L);

        assertThat(response.status()).isEqualTo(SyncStatus.OK);
        assertThat(response.currentSequence()).isEqualTo(13L);
        assertThat(response.operations()).extracting(SequencedOperation::sequence)
                .containsExactly(11L, 12L, 13L);
    }

    @Test
    void syncRequiredWhenBufferIsEmptyButBoardHasHistory() {
        when(zSetOperations.rangeWithScores("operations:board:" + boardId, 0, 0)).thenReturn(Set.of());

        SyncResponse response = service.buildSyncResponse(boardId, new SyncRequest(10L), 50L);

        assertThat(response.status()).isEqualTo(SyncStatus.SYNC_REQUIRED);
        assertThat(response.currentSequence()).isEqualTo(50L);
        assertThat(response.operations()).isEmpty();
    }

    @Test
    void syncRequiredWhenOldestRetainedIsPastWhatClientNeeds() throws Exception {
        // Client has 10, needs 11+, but the buffer's oldest surviving entry is 30 —
        // 11..29 have been evicted, so a "replay" would silently skip them.
        String key = "operations:board:" + boardId;
        SequencedOperation s30 = new SequencedOperation(30L, move(boardId));
        when(zSetOperations.rangeWithScores(key, 0, 0))
                .thenReturn(Set.of(new DefaultTypedTuple<>(json(s30), 30.0)));

        SyncResponse response = service.buildSyncResponse(boardId, new SyncRequest(10L), 40L);

        assertThat(response.status()).isEqualTo(SyncStatus.SYNC_REQUIRED);
        verify(zSetOperations, never()).rangeByScore(anyString(), anyDouble(), anyDouble());
    }

    @Test
    void replayIsFineWhenOldestRetainedIsExactlyNextNeeded() throws Exception {
        String key = "operations:board:" + boardId;
        SequencedOperation s11 = new SequencedOperation(11L, move(boardId));
        when(zSetOperations.rangeWithScores(key, 0, 0))
                .thenReturn(Set.of(new DefaultTypedTuple<>(json(s11), 11.0)));
        when(zSetOperations.rangeByScore(key, 11.0, Double.MAX_VALUE)).thenReturn(Set.of(json(s11)));

        SyncResponse response = service.buildSyncResponse(boardId, new SyncRequest(10L), 11L);

        assertThat(response.status()).isEqualTo(SyncStatus.OK);
        assertThat(response.operations()).hasSize(1);
    }

    // ---- cleanup ----

    @Test
    void clearBoardStateDeletesBothKeysForThatBoardOnly() {
        service.clearBoardState(boardId);

        verify(redisTemplate).delete("sequence:board:" + boardId);
        verify(redisTemplate).delete("operations:board:" + boardId);
        verify(redisTemplate, never()).delete("sequence:board:" + otherBoardId);
        verify(redisTemplate, never()).delete("operations:board:" + otherBoardId);
    }
}
