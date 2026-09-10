package com.ishan.syncCanvas.collaboration.presence;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PresenceSessionTrackerTest {

    private final PresenceSessionTracker tracker = new PresenceSessionTracker();
    private final UUID boardId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @Test
    void registerReturnsTrueForNewSession() {
        assertThat(tracker.register("session-1", boardId, userId)).isTrue();
    }

    @Test
    void registerReturnsFalseForAlreadyRegisteredSession() {
        tracker.register("session-1", boardId, userId);

        assertThat(tracker.register("session-1", boardId, userId)).isFalse();
    }

    @Test
    void unregisterReturnsTrackedBoardUserThenEmptyOnSecondCall() {
        tracker.register("session-1", boardId, userId);

        Optional<PresenceSessionTracker.BoardUser> first = tracker.unregister("session-1");
        Optional<PresenceSessionTracker.BoardUser> second = tracker.unregister("session-1");

        assertThat(first).contains(new PresenceSessionTracker.BoardUser(boardId, userId));
        assertThat(second).isEmpty();
    }

    @Test
    void unregisterOfUnknownSessionIsEmpty() {
        assertThat(tracker.unregister("never-registered")).isEmpty();
    }

    @Test
    void activeBoardIdsReflectsCurrentlyRegisteredSessions() {
        tracker.register("session-1", boardId, userId);

        assertThat(tracker.getActiveBoardIds()).containsExactly(boardId);

        tracker.unregister("session-1");

        assertThat(tracker.getActiveBoardIds()).isEmpty();
    }
}
