package com.ishan.syncCanvas.collaboration.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ishan.syncCanvas.canvas.domain.CanvasObjectType;
import com.ishan.syncCanvas.canvas.dto.CreateCanvasObjectRequest;
import com.ishan.syncCanvas.canvas.entity.CanvasObject;
import com.ishan.syncCanvas.collaboration.operation.CreateObjectOperation;
import com.ishan.syncCanvas.collaboration.operation.DeleteObjectOperation;
import com.ishan.syncCanvas.collaboration.operation.MoveObjectOperation;
import com.ishan.syncCanvas.collaboration.operation.Operation;
import com.ishan.syncCanvas.collaboration.operation.RotateObjectOperation;
import com.ishan.syncCanvas.collaboration.persistence.DirtySessionTracker;
import com.ishan.syncCanvas.collaboration.processor.BulkMoveObjectHandler;
import com.ishan.syncCanvas.collaboration.processor.ChangePayloadHandler;
import com.ishan.syncCanvas.collaboration.processor.CreateObjectHandler;
import com.ishan.syncCanvas.collaboration.processor.DeleteObjectHandler;
import com.ishan.syncCanvas.collaboration.processor.MoveObjectHandler;
import com.ishan.syncCanvas.collaboration.processor.OperationProcessor;
import com.ishan.syncCanvas.collaboration.processor.RotateObjectHandler;
import com.ishan.syncCanvas.collaboration.session.BoardSessionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Drives reconstruction through the REAL operation handlers (only the session registry
 * and dirty tracker are mocked, and neither is touched in REPLAY mode), so this proves
 * the live apply semantics and the durable replay semantics are the same code.
 */
@ExtendWith(MockitoExtension.class)
class BoardReconstructionServiceTest {

    @Mock
    private BoardSessionManager sessionManager;
    @Mock
    private DirtySessionTracker dirtySessionTracker;
    @Mock
    private BoardSnapshotRepository snapshotRepository;
    @Mock
    private BoardEventRepository eventRepository;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private BoardSnapshotService snapshotService;
    private BoardReconstructionService service;

    private final UUID boardId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID objectId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        OperationProcessor processor = new OperationProcessor(List.of(
                new CreateObjectHandler(sessionManager, dirtySessionTracker),
                new MoveObjectHandler(sessionManager, dirtySessionTracker),
                new RotateObjectHandler(sessionManager, dirtySessionTracker),
                new DeleteObjectHandler(sessionManager, dirtySessionTracker),
                new ChangePayloadHandler(sessionManager, dirtySessionTracker),
                new BulkMoveObjectHandler(sessionManager, dirtySessionTracker)));
        processor.registerHandlers();

        snapshotService = new BoardSnapshotService(snapshotRepository, objectMapper);
        service = new BoardReconstructionService(snapshotService, eventRepository, processor, objectMapper);
    }

    // The stored CREATE payload carries the server-assigned id, exactly as the live path
    // leaves it after the handler mutates the request before serialization.
    private BoardEvent createEvent(long sequence) throws Exception {
        CreateCanvasObjectRequest request = CreateCanvasObjectRequest.builder()
                .id(objectId).boardId(boardId).type(CanvasObjectType.RECTANGLE)
                .x(1).y(2).rotation(0).zindex(0).createdBy(userId).build();
        return event(sequence, new CreateObjectOperation(UUID.randomUUID(), boardId, userId, request, Instant.now()));
    }

    private BoardEvent moveEvent(long sequence, double x, double y, Long expectedVersion) throws Exception {
        return event(sequence, new MoveObjectOperation(UUID.randomUUID(), boardId, userId, Instant.now(),
                objectId, expectedVersion, x, y));
    }

    private BoardEvent rotateEvent(long sequence, double rotation, Long expectedVersion) throws Exception {
        return event(sequence, new RotateObjectOperation(UUID.randomUUID(), boardId, userId, Instant.now(),
                objectId, expectedVersion, rotation));
    }

    private BoardEvent deleteEvent(long sequence, Long expectedVersion) throws Exception {
        return event(sequence, new DeleteObjectOperation(UUID.randomUUID(), boardId, userId,
                objectId, expectedVersion, Instant.now()));
    }

    private BoardEvent event(long sequence, Operation op) throws Exception {
        return BoardEvent.of(boardId, sequence, op.operationId(), userId, op.type().name(),
                objectMapper.writeValueAsString(op));
    }

    private void noSnapshotAndLogStartsAt(long firstSequence, BoardEvent first) {
        when(snapshotRepository.findTopByBoardIdAndSequenceLessThanEqualOrderBySequenceDesc(org.mockito.ArgumentMatchers.eq(boardId), org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn(Optional.empty());
        when(eventRepository.findTopByBoardIdOrderBySequenceAsc(boardId)).thenReturn(Optional.of(first));
    }

    @Test
    void rebuildsFromInitialStateThroughEventsInSequenceOrder() throws Exception {
        BoardEvent e1 = createEvent(1);
        // expectedVersion 99 on the rotate would be rejected LIVE; in REPLAY the history
        // already happened, so it must apply regardless.
        List<BoardEvent> events = List.of(e1, moveEvent(2, 10, 20, 1L), rotateEvent(3, 45, 99L));
        noSnapshotAndLogStartsAt(1, e1);
        when(eventRepository.findByBoardIdAndSequenceGreaterThanAndSequenceLessThanEqualOrderBySequenceAsc(boardId, 0L, 3L))
                .thenReturn(events);

        Optional<List<CanvasObject>> result = service.reconstruct(boardId, 3);

        assertThat(result).isPresent();
        assertThat(result.get()).hasSize(1);
        CanvasObject object = result.get().get(0);
        assertThat(object.getId()).isEqualTo(objectId);
        assertThat(object.getX()).isEqualTo(10);
        assertThat(object.getY()).isEqualTo(20);
        assertThat(object.getRotation()).isEqualTo(45);
        assertThat(object.getVersion()).isEqualTo(3L);
        assertThat(object.getCreatedBy()).isEqualTo(userId);
        // REPLAY never touches the live registry or dirty tracking.
        verifyNoInteractions(sessionManager, dirtySessionTracker);
    }

    @Test
    void rebuildsFromSnapshotPlusSubsequentEventsOnly() throws Exception {
        CanvasObject atTwo = CanvasObject.builder().id(objectId).boardId(boardId).type(CanvasObjectType.RECTANGLE)
                .x(10).y(20).rotation(0).zindex(0).createdBy(userId).version(2L).build();
        BoardSnapshot snapshot = BoardSnapshot.of(boardId, 2L, snapshotService.serialize(List.of(atTwo)));
        when(snapshotRepository.findTopByBoardIdAndSequenceLessThanEqualOrderBySequenceDesc(boardId, 4L))
                .thenReturn(Optional.of(snapshot));
        when(eventRepository.findByBoardIdAndSequenceGreaterThanAndSequenceLessThanEqualOrderBySequenceAsc(boardId, 2L, 4L))
                .thenReturn(List.of(rotateEvent(3, 45, null), moveEvent(4, 30, 40, null)));

        Optional<List<CanvasObject>> result = service.reconstruct(boardId, 4);

        assertThat(result).isPresent();
        CanvasObject object = result.get().get(0);
        assertThat(object.getX()).isEqualTo(30);
        assertThat(object.getY()).isEqualTo(40);
        assertThat(object.getRotation()).isEqualTo(45);
        assertThat(object.getVersion()).isEqualTo(4L);
    }

    @Test
    void snapshotExactlyAtTargetNeedsNoEvents() throws Exception {
        CanvasObject state = CanvasObject.builder().id(objectId).boardId(boardId).type(CanvasObjectType.RECTANGLE)
                .x(5).y(6).zindex(0).version(1L).build();
        when(snapshotRepository.findTopByBoardIdAndSequenceLessThanEqualOrderBySequenceDesc(boardId, 7L))
                .thenReturn(Optional.of(BoardSnapshot.of(boardId, 7L, snapshotService.serialize(List.of(state)))));

        Optional<List<CanvasObject>> result = service.reconstruct(boardId, 7);

        assertThat(result).isPresent();
        assertThat(result.get().get(0).getX()).isEqualTo(5);
        verifyNoInteractions(eventRepository);
    }

    @Test
    void refusesWhenNoSnapshotAndLogDoesNotStartAtOne() throws Exception {
        // A pre-Phase-8 board with no baseline: events begin at 5, earlier history unknown.
        noSnapshotAndLogStartsAt(5, moveEvent(5, 1, 1, null));

        assertThat(service.reconstruct(boardId, 6)).isEmpty();
    }

    @Test
    void refusesOnAGapInTheLog() throws Exception {
        BoardEvent e1 = createEvent(1);
        noSnapshotAndLogStartsAt(1, e1);
        when(eventRepository.findByBoardIdAndSequenceGreaterThanAndSequenceLessThanEqualOrderBySequenceAsc(boardId, 0L, 3L))
                .thenReturn(List.of(e1, rotateEvent(3, 45, null)));

        assertThat(service.reconstruct(boardId, 3)).isEmpty();
    }

    @Test
    void refusesWhenTargetIsBeyondTheLastEvent() throws Exception {
        BoardEvent e1 = createEvent(1);
        noSnapshotAndLogStartsAt(1, e1);
        when(eventRepository.findByBoardIdAndSequenceGreaterThanAndSequenceLessThanEqualOrderBySequenceAsc(boardId, 0L, 3L))
                .thenReturn(List.of(e1, moveEvent(2, 1, 1, null)));

        assertThat(service.reconstruct(boardId, 3)).isEmpty();
    }

    @Test
    void anEventTargetingAnAlreadyDeletedObjectIsSkippedRatherThanAbortingReconstruction() throws Exception {
        // Mirrors a real production scenario: a board session reloaded from
        // current-state (canvas_objects) after an abrupt shutdown resurrected an object
        // the event log had already recorded as deleted, letting a further live MOVE on
        // it commit durably. Reconstruction must tolerate that orphaned historical event
        // rather than permanently refusing to ever snapshot this board again.
        BoardEvent e1 = createEvent(1);
        BoardEvent deleted = deleteEvent(2, 1L);
        BoardEvent orphanedMove = moveEvent(3, 999, 999, null);
        noSnapshotAndLogStartsAt(1, e1);
        when(eventRepository.findByBoardIdAndSequenceGreaterThanAndSequenceLessThanEqualOrderBySequenceAsc(boardId, 0L, 3L))
                .thenReturn(List.of(e1, deleted, orphanedMove));

        Optional<List<CanvasObject>> result = service.reconstruct(boardId, 3);

        assertThat(result).isPresent();
        assertThat(result.get()).isEmpty();
    }

    @Test
    void reconstructionIsDeterministic() throws Exception {
        BoardEvent e1 = createEvent(1);
        List<BoardEvent> events = List.of(e1, moveEvent(2, 10, 20, null), rotateEvent(3, 45, null));
        noSnapshotAndLogStartsAt(1, e1);
        when(eventRepository.findByBoardIdAndSequenceGreaterThanAndSequenceLessThanEqualOrderBySequenceAsc(boardId, 0L, 3L))
                .thenReturn(events);

        String first = snapshotService.serialize(service.reconstruct(boardId, 3).orElseThrow());
        String second = snapshotService.serialize(service.reconstruct(boardId, 3).orElseThrow());

        assertThat(first).isEqualTo(second);
    }
}
