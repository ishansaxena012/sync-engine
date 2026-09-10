package com.ishan.syncCanvas.collaboration.presence;

import java.util.UUID;

/**
 * Minimal state actually persisted in Redis per participant — just enough to
 * reconstruct a {@link PresenceEvent} on read. Status is never stored directly; it's
 * always derived from {@code lastSeen} at read time (see {@code PresenceService}), so a
 * heartbeat only ever needs to rewrite this one timestamp.
 */
record PresenceRecord(UUID userId, String displayName, long lastSeen) {
}
