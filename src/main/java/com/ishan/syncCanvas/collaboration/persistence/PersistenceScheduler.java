package com.ishan.syncCanvas.collaboration.persistence;

import com.ishan.syncCanvas.collaboration.session.BoardSession;
import com.ishan.syncCanvas.collaboration.session.BoardSessionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class PersistenceScheduler {

    private final DirtySessionTracker dirtySessionTracker;
    private final BoardSessionManager sessionManager;
    private final BoardPersistenceService persistenceService;
    private final PersistenceMetrics metrics;

    @Scheduled(fixedDelay = 5000)
    public void flushDirtyBoards() {

        log.info("Checking dirty boards...");

        for (UUID boardId : dirtySessionTracker.getDirtyBoards()) {
            sessionManager.getSession(boardId).ifPresent(session -> {
                try {
                    PersistenceResult result = persistenceService.persist(session);
                    dirtySessionTracker.clearDirty(boardId);
                    log.info("Board {} persisted successfully ({} objects)", result.boardId(),
                            result.persistedObjects());

                } catch (Exception ex) {
                    metrics.persistenceFailed();
                    log.error(
                            "Failed to persist board {}",
                            boardId,
                            ex);
                }
            });
        }
    }

    private void persistBoard(BoardSession session) {
        PersistenceResult result = persistenceService.persist(session);
        log.info("Board {} persisted ({} objects)", result.boardId(), result.persistedObjects());
    }
}