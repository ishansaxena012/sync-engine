package com.ishan.syncCanvas.collaboration.event;

import com.ishan.syncCanvas.canvas.entity.CanvasObject;
import com.ishan.syncCanvas.collaboration.session.BoardSession;
import com.ishan.syncCanvas.collaboration.session.BoardSessionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Takes a snapshot at every {@code snapshot-interval}-th committed sequence for boards
 * this instance has open. Runs off the WebSocket path and builds each snapshot purely
 * from durable inputs via {@link BoardReconstructionService} — never by serializing the
 * live session, which on a multi-instance deployment may still be missing lower-sequence
 * operations in flight over pub/sub. Multiple instances racing on the same board are
 * settled by the (board_id, sequence) unique constraint; since both produce identical
 * state, the loser just ignores the violation. Any failure here is logged and can never
 * affect operation processing.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SnapshotScheduler {

    private final BoardSessionManager sessionManager;
    private final BoardEventRepository eventRepository;
    private final BoardSnapshotService snapshotService;
    private final BoardReconstructionService reconstructionService;

    @Value("${app.collaboration.snapshot-interval:100}")
    private long snapshotInterval;

    @Scheduled(fixedDelay = 10_000)
    public void createDueSnapshots() {
        if (snapshotInterval <= 0) {
            return;
        }
        for (BoardSession session : sessionManager.getSessions()) {
            UUID boardId = session.getBoardId();
            try {
                snapshotBoard(boardId);
            } catch (Exception ex) {
                log.error("Snapshot pass failed for board {}", boardId, ex);
            }
        }
    }

    void snapshotBoard(UUID boardId) {
        long latest = eventRepository.findTopByBoardIdOrderBySequenceDesc(boardId)
                .map(BoardEvent::getSequence)
                .orElse(0L);

        long target = (latest / snapshotInterval) * snapshotInterval;
        if (target == 0) {
            return;
        }

        long lastSnapshot = snapshotService.findLatestAtOrBefore(boardId, target)
                .map(BoardSnapshot::getSequence)
                .orElse(0L);

        // Walk every due interval boundary since the last snapshot so the cadence is kept
        // even if a board raced far ahead between passes; each step reconstructs from the
        // previous snapshot, so the cost per step stays bounded by one interval.
        for (long sequence = ((lastSnapshot / snapshotInterval) + 1) * snapshotInterval;
             sequence <= target;
             sequence += snapshotInterval) {

            Optional<List<CanvasObject>> state = reconstructionService.reconstruct(boardId, sequence);
            if (state.isEmpty()) {
                log.warn("Skipping snapshot of board {} at {}: state not provable from durable history", boardId, sequence);
                return;
            }

            try {
                if (snapshotService.createSnapshotIfAbsent(boardId, sequence, state.get())) {
                    log.info("Snapshot of board {} taken at sequence {}", boardId, sequence);
                }
            } catch (DataIntegrityViolationException raced) {
                log.debug("Snapshot of board {} at {} was created by another instance", boardId, sequence);
            }
        }
    }
}
