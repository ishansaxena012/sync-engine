package com.ishan.syncCanvas.collaboration.persistence;

import lombok.Getter;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Getter
@Component
public class PersistenceMetrics {

    private final AtomicLong persistedBoards = new AtomicLong();
    private final AtomicLong persistedObjects = new AtomicLong();
    private final AtomicInteger failedPersistAttempts = new AtomicInteger();
    private final AtomicLong staleLockSkips = new AtomicLong();

    public void boardPersisted(int objects) {
        persistedBoards.incrementAndGet();
        persistedObjects.addAndGet(objects);
    }

    public void persistenceFailed() {
        failedPersistAttempts.incrementAndGet();
    }

    public void staleLockSkipped() {
        staleLockSkips.incrementAndGet();
    }
}