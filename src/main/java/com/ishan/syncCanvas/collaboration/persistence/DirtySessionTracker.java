package com.ishan.syncCanvas.collaboration.persistence;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class DirtySessionTracker {

    private final Set<UUID> dirtyBoards = ConcurrentHashMap.newKeySet();

    public void clearDirty(UUID boardId) {
        dirtyBoards.remove(boardId);
    }

    public Set<UUID> getDirtyBoards() {
        return Set.copyOf(dirtyBoards);
    }

    public boolean isDirty(UUID boardId) {
        return dirtyBoards.contains(boardId);
    }

    public void markDirty(UUID boardId) {
        if (dirtyBoards.add(boardId)) {
            log.info("Board {} marked dirty", boardId);
        }
    }

}