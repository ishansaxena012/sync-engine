package com.ishan.syncCanvas.collaboration.persistence;

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

        // log.info("Checking dirty boards...");

        for (UUID boardId : dirtySessionTracker.getDirtyBoards()) {
            sessionManager.getSession(boardId).ifPresent(session -> {
                // Clear the dirty flag only after a successful persist, and only if no
                // further edits arrived while persisting. Clearing it up front would let
                // a transient DB failure permanently drop every edit made since the last
                // successful persist, since nothing else would ever re-mark the board
                // dirty; clearing it unconditionally afterward could just as easily wipe
                // out the "dirty" marking for an edit that landed mid-persist.
                long versionBeforePersist = session.getVersion();
                try {
                    PersistenceResult result = persistenceService.persist(session);
                    if (session.getVersion() == versionBeforePersist) {
                        dirtySessionTracker.clearDirty(boardId);
                    } else {
                        log.debug("Board {} received new edits during persist; leaving dirty", boardId);
                    }
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


}