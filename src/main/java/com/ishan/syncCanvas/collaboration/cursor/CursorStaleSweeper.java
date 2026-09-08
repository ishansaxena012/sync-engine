package com.ishan.syncCanvas.collaboration.cursor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;

/**
 * Backstop cleanup for cursors whose owning session vanished without a clean STOMP
 * disconnect (e.g. a crashed tab). {@link CursorPresenceListener} handles the clean-exit
 * path; this catches whatever that misses, once the cursor's Redis TTL lapses.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CursorStaleSweeper {

    private final CursorPresenceListener presenceListener;
    private final CursorService cursorService;

    @Scheduled(fixedDelay = 10_000)
    public void sweep() {
        for (UUID boardId : presenceListener.getActiveBoardIds()) {
            Set<String> memberIds = cursorService.getCursorMembers(boardId);
            for (String memberId : memberIds) {
                UUID userId;
                try {
                    userId = UUID.fromString(memberId);
                } catch (IllegalArgumentException ex) {
                    continue;
                }
                if (!cursorService.isCursorLive(boardId, userId)) {
                    log.debug("Cursor for user {} on board {} expired; cleaning up", userId, boardId);
                    cursorService.removeCursor(boardId, userId);
                }
            }
        }
    }
}
