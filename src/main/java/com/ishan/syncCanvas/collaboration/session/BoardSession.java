package com.ishan.syncCanvas.collaboration.session;

import com.ishan.syncCanvas.canvas.entity.CanvasObject;
import lombok.Getter;

import java.time.Instant;
import java.util.*;
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

    public void initialize(Collection<CanvasObject> canvasObjects) {
        canvasObjects.forEach(object -> objects.put(object.getId(), object));
    }

    /**
     * Syncs the JPA-managed version and timestamps from saved deep-copies back into
     * the live in-memory objects. This must be called instead of replacing the live
     * objects outright, to avoid stale-version conflicts on subsequent persist cycles.
     */
    public void syncVersions(Map<UUID, CanvasObject> savedById) {
        savedById.forEach((id, saved) -> {
            CanvasObject live = objects.get(id);
            if (live != null) {
                live.setVersion(saved.getVersion());
                live.setCreatedAt(saved.getCreatedAt());
                live.setUpdatedAt(saved.getUpdatedAt());
            }
        });
    }

    public void addObject(CanvasObject object) {
        objects.put(object.getId(), object);
        incrementVersion();
        touch();
    }

    public CanvasObject getObject(UUID objectId) {
        return objects.get(objectId);
    }

    public boolean removeObject(UUID objectId) {
        CanvasObject removed = objects.remove(objectId);

        if (removed != null) {
            incrementVersion();
            touch();
            return true;
        }
        return false;
    }

    public void connectUser(UUID userId) {
        connectedUsers.add(userId);
    }

    public void disconnectUser(UUID userId) {
        connectedUsers.remove(userId);
    }

    public Collection<CanvasObject> getObjects() {
        return Collections.unmodifiableCollection(objects.values());
    }
}