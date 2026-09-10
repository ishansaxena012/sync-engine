package com.ishan.syncCanvas.collaboration.event;

import com.ishan.syncCanvas.canvas.entity.CanvasObject;
import com.ishan.syncCanvas.collaboration.session.BoardSession;
import com.ishan.syncCanvas.collaboration.session.BoardSessionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SnapshotSchedulerTest {

    @Mock
    private BoardSessionManager sessionManager;
    @Mock
    private BoardEventRepository eventRepository;
    @Mock
    private BoardSnapshotService snapshotService;
    @Mock
    private BoardReconstructionService reconstructionService;

    private SnapshotScheduler scheduler;

    private final UUID boardId = UUID.randomUUID();
    private final List<CanvasObject> state = List.of();

    @BeforeEach
    void setUp() {
        scheduler = new SnapshotScheduler(sessionManager, eventRepository, snapshotService, reconstructionService);
        ReflectionTestUtils.setField(scheduler, "snapshotInterval", 100L);
        lenient().when(sessionManager.getSessions()).thenReturn(List.of(new BoardSession(boardId)));
        lenient().when(reconstructionService.reconstruct(eq(boardId), anyLong())).thenReturn(Optional.of(state));
        lenient().when(snapshotService.createSnapshotIfAbsent(eq(boardId), anyLong(), anyList())).thenReturn(true);
    }

    private void latestSequence(long latest) {
        BoardEvent top = BoardEvent.of(boardId, latest, UUID.randomUUID(), UUID.randomUUID(), "MOVE_OBJECT", "{}");
        when(eventRepository.findTopByBoardIdOrderBySequenceDesc(boardId)).thenReturn(Optional.of(top));
    }

    private void latestSnapshot(long sequence) {
        when(snapshotService.findLatestAtOrBefore(eq(boardId), anyLong()))
                .thenReturn(Optional.of(BoardSnapshot.of(boardId, sequence, "[]")));
    }

    @Test
    void noSnapshotBeforeTheFirstIntervalBoundary() {
        latestSequence(50);

        scheduler.createDueSnapshots();

        verify(reconstructionService, never()).reconstruct(any(), anyLong());
        verify(snapshotService, never()).createSnapshotIfAbsent(any(), anyLong(), anyList());
    }

    @Test
    void snapshotsEveryDueBoundaryInOrderAndNeverPastTheLatestEvent() {
        latestSequence(250);
        when(snapshotService.findLatestAtOrBefore(eq(boardId), anyLong())).thenReturn(Optional.empty());

        scheduler.createDueSnapshots();

        InOrder inOrder = inOrder(reconstructionService, snapshotService);
        inOrder.verify(reconstructionService).reconstruct(boardId, 100L);
        inOrder.verify(snapshotService).createSnapshotIfAbsent(boardId, 100L, state);
        inOrder.verify(reconstructionService).reconstruct(boardId, 200L);
        inOrder.verify(snapshotService).createSnapshotIfAbsent(boardId, 200L, state);
        verify(reconstructionService, never()).reconstruct(boardId, 300L);
        verify(reconstructionService, never()).reconstruct(boardId, 250L);
    }

    @Test
    void nothingToDoWhenTheLatestBoundaryIsAlreadySnapshotted() {
        latestSequence(250);
        latestSnapshot(200);

        scheduler.createDueSnapshots();

        verify(snapshotService, never()).createSnapshotIfAbsent(any(), anyLong(), anyList());
    }

    @Test
    void resumesFromTheLastSnapshotRatherThanRestartingAtTheFirstBoundary() {
        latestSequence(250);
        latestSnapshot(100);

        scheduler.createDueSnapshots();

        verify(reconstructionService, never()).reconstruct(boardId, 100L);
        verify(snapshotService).createSnapshotIfAbsent(boardId, 200L, state);
    }

    @Test
    void unprovableStateIsNeverSnapshotted() {
        latestSequence(250);
        when(snapshotService.findLatestAtOrBefore(eq(boardId), anyLong())).thenReturn(Optional.empty());
        when(reconstructionService.reconstruct(boardId, 100L)).thenReturn(Optional.empty());

        scheduler.createDueSnapshots();

        verify(snapshotService, never()).createSnapshotIfAbsent(any(), anyLong(), anyList());
        verify(reconstructionService, never()).reconstruct(boardId, 200L);
    }

    @Test
    void losingTheCrossInstanceRaceIsHarmlessAndDoesNotStopLaterBoundaries() {
        latestSequence(250);
        when(snapshotService.findLatestAtOrBefore(eq(boardId), anyLong())).thenReturn(Optional.empty());
        when(snapshotService.createSnapshotIfAbsent(boardId, 100L, state))
                .thenThrow(new DataIntegrityViolationException("uk_board_snapshot_board_sequence"));

        scheduler.createDueSnapshots();

        verify(snapshotService).createSnapshotIfAbsent(boardId, 200L, state);
    }

    @Test
    void aFailingBoardDoesNotStopOtherBoardsAndBoardsAreIsolated() {
        UUID otherBoardId = UUID.randomUUID();
        when(sessionManager.getSessions()).thenReturn(List.of(new BoardSession(boardId), new BoardSession(otherBoardId)));
        when(eventRepository.findTopByBoardIdOrderBySequenceDesc(boardId)).thenThrow(new RuntimeException("db hiccup"));
        when(eventRepository.findTopByBoardIdOrderBySequenceDesc(otherBoardId))
                .thenReturn(Optional.of(BoardEvent.of(otherBoardId, 100L, UUID.randomUUID(), UUID.randomUUID(), "MOVE_OBJECT", "{}")));
        when(snapshotService.findLatestAtOrBefore(eq(otherBoardId), anyLong())).thenReturn(Optional.empty());
        when(reconstructionService.reconstruct(otherBoardId, 100L)).thenReturn(Optional.of(state));

        scheduler.createDueSnapshots();

        verify(snapshotService).createSnapshotIfAbsent(otherBoardId, 100L, state);
        verify(snapshotService, never()).createSnapshotIfAbsent(eq(boardId), anyLong(), anyList());
    }

    @Test
    void intervalOfZeroDisablesSnapshotting() {
        ReflectionTestUtils.setField(scheduler, "snapshotInterval", 0L);

        scheduler.createDueSnapshots();

        verify(eventRepository, never()).findTopByBoardIdOrderBySequenceDesc(any());
    }
}
