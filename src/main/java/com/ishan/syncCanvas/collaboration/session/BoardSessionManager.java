package com.ishan.syncCanvas.collaboration.session;

import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;
import java.util.Optional;
import java.util.Collection;
import java.util.Collections;

@Component
public class BoardSessionManager {

    private final ConcurrentMap<UUID, BoardSession> sessions = new ConcurrentHashMap<>();

    public Optional<BoardSession> getSession(UUID boardId) {
        return Optional.ofNullable(sessions.get(boardId));
    }

    public BoardSession getOrCreate(
            UUID boardId,
            Supplier<BoardSession> supplier) {

        return sessions.computeIfAbsent(
                boardId,
                id -> supplier.get());
    }

    public void remove(UUID boardId) {
        sessions.remove(boardId);
    }

    public boolean exists(UUID boardId) {
        return sessions.containsKey(boardId);
    }

    public int activeSessions() {
        return sessions.size();
    }

    public Collection<BoardSession> getSessions() {
        return Collections.unmodifiableCollection(sessions.values());
    }

}