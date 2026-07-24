package com.ishan.syncCanvas.collaboration.persistence;

import java.time.Instant;
import java.util.UUID;

public record PersistenceResult(

        UUID boardId,

        int persistedObjects,

        Instant persistedAt

) {
}