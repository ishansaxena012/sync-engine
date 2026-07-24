package com.ishan.syncCanvas.collaboration.persistence;

import com.ishan.syncCanvas.canvas.entity.CanvasObject;
import com.ishan.syncCanvas.canvas.repository.CanvasObjectRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import com.ishan.syncCanvas.collaboration.session.BoardSession;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BoardPersistenceServiceImpl implements BoardPersistenceService {

        private final CanvasObjectRepository canvasObjectRepository;
        private final PersistenceMetrics metrics;

        @Override
        @Transactional
        public PersistenceResult persist(BoardSession session) {

                // Take deep copies of the live objects so JPA merge never touches the
                // in-memory references. This prevents StaleObjectStateException: the live
                // object keeps its current (pre-save) version, we save a copy, and then
                // we sync only the version/timestamps back into the live objects.
                List<CanvasObject> copies = session.getObjects().stream()
                                .map(CanvasObject::deepCopy)
                                .collect(Collectors.toList());

                log.info("Persisting {} objects for board {}", copies.size(), session.getBoardId());

                List<CanvasObject> saved = canvasObjectRepository.saveAllAndFlush(copies);

                log.info("Saved {} objects for board {}", saved.size(), session.getBoardId());

                // Build id -> saved copy map and sync version/timestamps back to live objects.
                Map<UUID, CanvasObject> savedById = saved.stream()
                                .collect(Collectors.toMap(CanvasObject::getId, o -> o));
                session.syncVersions(savedById);

                metrics.boardPersisted(saved.size());

                return new PersistenceResult(
                                session.getBoardId(),
                                saved.size(),
                                Instant.now());
        }

}