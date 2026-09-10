package com.ishan.syncCanvas.collaboration.sync;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ishan.syncCanvas.collaboration.event.BoardEvent;
import com.ishan.syncCanvas.collaboration.event.BoardEventRepository;
import com.ishan.syncCanvas.collaboration.event.BoardEventService;
import com.ishan.syncCanvas.collaboration.operation.MoveObjectOperation;
import com.ishan.syncCanvas.collaboration.operation.SequencedOperation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BoardSyncServiceTest {

    @Mock
    private BoardEventService boardEventService;
    @Mock
    private OperationSequenceService operationSequenceService;
    @Mock
    private BoardEventRepository eventRepository;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private BoardSyncService service;

    private final UUID boardId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new BoardSyncService(boardEventService, operationSequenceService, eventRepository, objectMapper);
        ReflectionTestUtils.setField(service, "fallbackLimit", 1000);
    }

    private BoardEvent event(long sequence) throws Exception {
        MoveObjectOperation op = new MoveObjectOperation(UUID.randomUUID(), boardId, userId, Instant.now(),
                UUID.randomUUID(), null, sequence, sequence);
        return BoardEvent.of(boardId, sequence, op.operationId(), userId, "MOVE_OBJECT",
                objectMapper.writeValueAsString(op));
    }

    private SyncResponse redis(SyncStatus status, long current) {
        return new SyncResponse(status, boardId, current, List.of());
    }

    @Test
    void redisReplayIsReturnedAsIsWhenItCanSatisfyTheRange() {
        SyncRequest request = new SyncRequest(10L);
        when(boardEventService.getLatestSequence(boardId)).thenReturn(13L);
        SyncResponse ok = redis(SyncStatus.OK, 13L);
        when(operationSequenceService.buildSyncResponse(boardId, request, 13L)).thenReturn(ok);

        assertThat(service.sync(boardId, request)).isSameAs(ok);
        verifyNoInteractions(eventRepository);
    }

    @Test
    void upToDateIsReturnedWithoutTouchingTheDatabaseLog() {
        SyncRequest request = new SyncRequest(13L);
        when(boardEventService.getLatestSequence(boardId)).thenReturn(13L);
        when(operationSequenceService.buildSyncResponse(boardId, request, 13L))
                .thenReturn(redis(SyncStatus.UP_TO_DATE, 13L));

        assertThat(service.sync(boardId, request).status()).isEqualTo(SyncStatus.UP_TO_DATE);
        verifyNoInteractions(eventRepository);
    }

    @Test
    void authoritativeCurrentSequenceIsHandedToTheRedisLayerNotRedisOwnView() {
        SyncRequest request = new SyncRequest(5L);
        when(boardEventService.getLatestSequence(boardId)).thenReturn(300L);
        when(operationSequenceService.buildSyncResponse(eq(boardId), eq(request), anyLong()))
                .thenReturn(redis(SyncStatus.UP_TO_DATE, 300L));

        service.sync(boardId, request);

        verify(operationSequenceService).buildSyncResponse(boardId, request, 300L);
    }

    @Test
    void fallsBackToPostgresAndReplaysContiguousEventsInOrder() throws Exception {
        SyncRequest request = new SyncRequest(10L);
        when(boardEventService.getLatestSequence(boardId)).thenReturn(13L);
        when(operationSequenceService.buildSyncResponse(boardId, request, 13L))
                .thenReturn(redis(SyncStatus.SYNC_REQUIRED, 13L));
        when(eventRepository.findByBoardIdAndSequenceGreaterThanOrderBySequenceAsc(eq(boardId), eq(10L), any(Pageable.class)))
                .thenReturn(List.of(event(11), event(12), event(13)));

        SyncResponse response = service.sync(boardId, request);

        assertThat(response.status()).isEqualTo(SyncStatus.OK);
        assertThat(response.currentSequence()).isEqualTo(13L);
        assertThat(response.operations()).extracting(SequencedOperation::sequence).containsExactly(11L, 12L, 13L);
        assertThat(response.operations().get(0).operation()).isInstanceOf(MoveObjectOperation.class);
    }

    @Test
    void emptyDurableHistoryIsSyncRequired() {
        SyncRequest request = new SyncRequest(10L);
        when(boardEventService.getLatestSequence(boardId)).thenReturn(50L);
        when(operationSequenceService.buildSyncResponse(boardId, request, 50L))
                .thenReturn(redis(SyncStatus.SYNC_REQUIRED, 50L));
        when(eventRepository.findByBoardIdAndSequenceGreaterThanOrderBySequenceAsc(eq(boardId), eq(10L), any(Pageable.class)))
                .thenReturn(List.of());

        SyncResponse response = service.sync(boardId, request);

        assertThat(response.status()).isEqualTo(SyncStatus.SYNC_REQUIRED);
        assertThat(response.currentSequence()).isEqualTo(50L);
    }

    @Test
    void durableHistoryStartingAfterWhatClientNeedsIsSyncRequired() throws Exception {
        // Client has 10; the log's first surviving event is 15 (e.g. a board whose events
        // only began when Phase 8 was deployed). Replaying 15+ would silently skip 11..14.
        SyncRequest request = new SyncRequest(10L);
        when(boardEventService.getLatestSequence(boardId)).thenReturn(20L);
        when(operationSequenceService.buildSyncResponse(boardId, request, 20L))
                .thenReturn(redis(SyncStatus.SYNC_REQUIRED, 20L));
        when(eventRepository.findByBoardIdAndSequenceGreaterThanOrderBySequenceAsc(eq(boardId), eq(10L), any(Pageable.class)))
                .thenReturn(List.of(event(15), event(16)));

        assertThat(service.sync(boardId, request).status()).isEqualTo(SyncStatus.SYNC_REQUIRED);
    }

    @Test
    void holeInsideDurableHistoryIsSyncRequiredNeverAPartialReplay() throws Exception {
        SyncRequest request = new SyncRequest(10L);
        when(boardEventService.getLatestSequence(boardId)).thenReturn(13L);
        when(operationSequenceService.buildSyncResponse(boardId, request, 13L))
                .thenReturn(redis(SyncStatus.SYNC_REQUIRED, 13L));
        when(eventRepository.findByBoardIdAndSequenceGreaterThanOrderBySequenceAsc(eq(boardId), eq(10L), any(Pageable.class)))
                .thenReturn(List.of(event(11), event(13)));

        SyncResponse response = service.sync(boardId, request);

        assertThat(response.status()).isEqualTo(SyncStatus.SYNC_REQUIRED);
        assertThat(response.operations()).isEmpty();
    }

    @Test
    void corruptEventPayloadIsSyncRequiredRatherThanASilentHole() {
        SyncRequest request = new SyncRequest(10L);
        when(boardEventService.getLatestSequence(boardId)).thenReturn(11L);
        when(operationSequenceService.buildSyncResponse(boardId, request, 11L))
                .thenReturn(redis(SyncStatus.SYNC_REQUIRED, 11L));
        when(eventRepository.findByBoardIdAndSequenceGreaterThanOrderBySequenceAsc(eq(boardId), eq(10L), any(Pageable.class)))
                .thenReturn(List.of(BoardEvent.of(boardId, 11L, UUID.randomUUID(), userId, "MOVE_OBJECT", "{not json")));

        assertThat(service.sync(boardId, request).status()).isEqualTo(SyncStatus.SYNC_REQUIRED);
    }
}
