package com.ishan.syncCanvas.collaboration.persistence;

import com.ishan.syncCanvas.collaboration.session.BoardSession;
import com.ishan.syncCanvas.collaboration.session.BoardSessionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Evicts in-memory {@link BoardSession}s that have had no activity for a while.
 *
 * <p>Nothing else in the collaboration engine ever removes a session from
 * {@link BoardSessionManager} — every board that is ever edited would otherwise stay
 * fully materialized in memory for the life of the JVM. A session is only evicted once
 * it is both idle and clean (already persisted), so an idle-but-unsaved board is left
 * for {@link PersistenceScheduler} to flush first; it becomes eligible for eviction on
 * a later sweep once that flush clears its dirty flag.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IdleSessionSweeper {

    private final BoardSessionManager sessionManager;
    private final DirtySessionTracker dirtySessionTracker;

    @Value("${app.collaboration.session-idle-timeout-minutes:30}")
    private long idleTimeoutMinutes;

    @Scheduled(fixedDelay = 300_000)
    public void evictIdleSessions() {
        Instant cutoff = Instant.now().minus(Duration.ofMinutes(idleTimeoutMinutes));

        for (BoardSession session : sessionManager.getSessions()) {
            if (session.getLastActivity().isAfter(cutoff)) {
                continue;
            }
            if (dirtySessionTracker.isDirty(session.getBoardId())) {
                continue;
            }

            sessionManager.remove(session.getBoardId());
            log.info("Evicted idle board session {} (idle since {})", session.getBoardId(), session.getLastActivity());
        }
    }
}
