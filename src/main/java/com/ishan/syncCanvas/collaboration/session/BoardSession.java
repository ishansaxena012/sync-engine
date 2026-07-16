package com.ishan.syncCanvas.collaboration.session;

import com.ishan.syncCanvas.canvas.entity.CanvasObject;
import lombok.Getter;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Getter
public class BoardSession {

    private final UUID boardId;

    private final ConcurrentMap<UUID, CanvasObject> objects;

    private final Set<UUID> connectedUsers;

    private final ReadWriteLock lock;

    private SessionState state;

    private long version;

    private Instant lastActivity;

    public BoardSession(UUID boardId) {

        this.boardId = boardId;

        this.objects = new ConcurrentHashMap<>();

        this.connectedUsers = ConcurrentHashMap.newKeySet();

        this.lock = new ReentrantReadWriteLock();

        this.state = SessionState.LOADING;

        this.version = 0;

        this.lastActivity = Instant.now();
    }

    public void activate() {
        this.state = SessionState.ACTIVE;
    }

    public void markPersisting() {
        this.state = SessionState.PERSISTING;
    }

    public void close() {
        this.state = SessionState.CLOSED;
    }

    public void touch() {
        this.lastActivity = Instant.now();
    }

    public void incrementVersion() {
        version++;
    }

    public void addObject(CanvasObject object) {
        objects.put(object.getId(), object);
        incrementVersion();
        touch();
    }

    public CanvasObject getObject(UUID objectId) {
        return objects.get(objectId);
    }

    public void removeObject(UUID objectId) {
        objects.remove(objectId);
        incrementVersion();
        touch();
    }
}