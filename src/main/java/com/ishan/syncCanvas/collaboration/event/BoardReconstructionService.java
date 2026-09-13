package com.ishan.syncCanvas.collaboration.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ishan.syncCanvas.canvas.entity.CanvasObject;
import com.ishan.syncCanvas.collaboration.exception.ObjectNotFoundException;
import com.ishan.syncCanvas.collaboration.operation.Operation;
import com.ishan.syncCanvas.collaboration.processor.ApplyMode;
import com.ishan.syncCanvas.collaboration.processor.OperationProcessor;
import com.ishan.syncCanvas.collaboration.session.BoardSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Rebuilds a board's exact state as of a target sequence from durable inputs only:
 * the newest snapshot at or before the target, then every committed event after it up
 * to the target, applied in sequence order through the same handlers the live path
 * uses. Never reads Redis or the live {@code BoardSession}, so it is deterministic and
 * identical on every instance.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BoardReconstructionService {

    private final BoardSnapshotService snapshotService;
    private final BoardEventRepository eventRepository;
    private final OperationProcessor operationProcessor;
    private final ObjectMapper objectMapper;

    /**
     * @return the board's objects exactly after {@code targetSequence}, or empty if the
     *         durable history cannot prove that state (no snapshot and the event log
     *         doesn't start at 1, a gap, or the target is beyond the last event) — in
     *         which case a caller must not fabricate a snapshot from it.
     */
    public Optional<List<CanvasObject>> reconstruct(UUID boardId, long targetSequence) {
        BoardSession replay = new BoardSession(boardId);
        long from;

        Optional<BoardSnapshot> snapshot = snapshotService.findLatestAtOrBefore(boardId, targetSequence);
        if (snapshot.isPresent()) {
            replay.initialize(snapshotService.deserialize(snapshot.get()));
            from = snapshot.get().getSequence();
        } else {
            Optional<BoardEvent> earliest = eventRepository.findTopByBoardIdOrderBySequenceAsc(boardId);
            if (earliest.isPresent() && earliest.get().getSequence() > 1) {
                log.warn("Board {} has no snapshot and its event log starts at {}; cannot reconstruct",
                        boardId, earliest.get().getSequence());
                return Optional.empty();
            }
            from = 0;
        }

        if (from == targetSequence) {
            return Optional.of(canonical(replay));
        }

        List<BoardEvent> events = eventRepository
                .findByBoardIdAndSequenceGreaterThanAndSequenceLessThanEqualOrderBySequenceAsc(
                        boardId, from, targetSequence);

        long expected = from + 1;
        for (BoardEvent event : events) {
            if (event.getSequence() != expected) {
                log.warn("Board {} event log has a gap at {} (found {}); cannot reconstruct",
                        boardId, expected, event.getSequence());
                return Optional.empty();
            }
            try {
                operationProcessor.apply(deserialize(event), replay, ApplyMode.REPLAY);
            } catch (ObjectNotFoundException ex) {
                // A mutation on an object that no longer exists by this point in history.
                // This can legitimately happen when a board session is reloaded from
                // current-state (canvas_objects) after an abrupt shutdown lost an
                // already-event-logged delete's effect on that table -- a later session
                // then "resurrects" the object and lets further live operations on it
                // commit, even though the event log already recorded its deletion. The
                // operation's effect is moot for an object that is gone either way, so it
                // is skipped rather than permanently blocking every future snapshot of
                // this board on one orphaned historical event.
                log.warn("Board {} event {} (sequence {}) targets an object no longer present during replay; skipping",
                        boardId, event.getId(), event.getSequence(), ex);
            }
            expected++;
        }

        if (expected - 1 != targetSequence) {
            log.warn("Board {} event log ends at {} but {} was requested; cannot reconstruct",
                    boardId, expected - 1, targetSequence);
            return Optional.empty();
        }

        return Optional.of(canonical(replay));
    }

    private Operation deserialize(BoardEvent event) {
        try {
            return objectMapper.readValue(event.getPayload(), Operation.class);
        } catch (Exception ex) {
            throw new IllegalStateException(
                    "Corrupt event " + event.getId() + " (sequence " + event.getSequence() + ")", ex);
        }
    }

    private static List<CanvasObject> canonical(BoardSession session) {
        return session.getObjects().stream()
                .sorted(Comparator.comparing(o -> o.getId().toString()))
                .toList();
    }
}
