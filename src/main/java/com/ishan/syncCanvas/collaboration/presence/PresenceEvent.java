package com.ishan.syncCanvas.collaboration.presence;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.UUID;

/**
 * Outgoing presence event for one participant. {@code displayName}/{@code color}/
 * {@code status} are boxed/nullable so a {@code USER_LEFT} event can omit them rather
 * than sending misleading values.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PresenceEvent(
        PresenceEventType type,
        UUID userId,
        String displayName,
        String color,
        PresenceStatus status,
        long timestamp) {
}
