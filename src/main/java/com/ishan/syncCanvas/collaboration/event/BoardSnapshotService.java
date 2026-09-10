package com.ishan.syncCanvas.collaboration.event;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ishan.syncCanvas.canvas.entity.CanvasObject;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BoardSnapshotService {

    private final BoardSnapshotRepository snapshotRepository;
    private final ObjectMapper objectMapper;

    /** Persists a snapshot unconditionally. Joins the caller's transaction when there is one. */
    @Transactional
    public BoardSnapshot saveSnapshot(UUID boardId, long sequence, List<CanvasObject> objects) {
        return snapshotRepository.save(BoardSnapshot.of(boardId, sequence, serialize(objects)));
    }

    /**
     * Persists a snapshot only if none exists at this sequence. Two instances can still
     * race past the exists-check; the unique constraint settles it, and since both would
     * have produced byte-identical state the loser can simply ignore the violation.
     */
    @Transactional
    public boolean createSnapshotIfAbsent(UUID boardId, long sequence, List<CanvasObject> objects) {
        if (snapshotRepository.existsByBoardIdAndSequence(boardId, sequence)) {
            return false;
        }
        snapshotRepository.save(BoardSnapshot.of(boardId, sequence, serialize(objects)));
        return true;
    }

    public Optional<BoardSnapshot> findLatestAtOrBefore(UUID boardId, long sequence) {
        return snapshotRepository.findTopByBoardIdAndSequenceLessThanEqualOrderBySequenceDesc(boardId, sequence);
    }

    public List<CanvasObject> deserialize(BoardSnapshot snapshot) {
        try {
            return objectMapper.readValue(snapshot.getState(), new TypeReference<List<CanvasObject>>() {
            });
        } catch (Exception ex) {
            throw new IllegalStateException(
                    "Corrupt snapshot " + snapshot.getId() + " for board " + snapshot.getBoardId(), ex);
        }
    }

    /** Canonical form: objects sorted by id so identical states always serialize identically. */
    public String serialize(List<CanvasObject> objects) {
        try {
            List<CanvasObject> canonical = objects.stream()
                    .sorted(Comparator.comparing(o -> o.getId().toString()))
                    .toList();
            return objectMapper.writeValueAsString(canonical);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not serialize board state", ex);
        }
    }
}
