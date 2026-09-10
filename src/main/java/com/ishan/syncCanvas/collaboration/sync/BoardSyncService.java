package com.ishan.syncCanvas.collaboration.sync;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ishan.syncCanvas.collaboration.event.BoardEvent;
import com.ishan.syncCanvas.collaboration.event.BoardEventRepository;
import com.ishan.syncCanvas.collaboration.event.BoardEventService;
import com.ishan.syncCanvas.collaboration.operation.Operation;
import com.ishan.syncCanvas.collaboration.operation.SequencedOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Reconnect sync with a durable fallback: try the fast Redis replay buffer first; if it
 * can no longer cover the requested range, serve the gap from the PostgreSQL event log.
 * Either way the reply is strictly sequence-ascending and contiguous from
 * {@code lastSequenceReceived + 1}, or it is SYNC_REQUIRED — it never claims a gap-free
 * replay it can't prove.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BoardSyncService {

    private final BoardEventService boardEventService;
    private final OperationSequenceService operationSequenceService;
    private final BoardEventRepository eventRepository;
    private final ObjectMapper objectMapper;

    @Value("${app.collaboration.sync-fallback-limit:1000}")
    private int fallbackLimit;

    public SyncResponse sync(UUID boardId, SyncRequest request) {
        long lastSequenceReceived = request == null || request.lastSequenceReceived() == null
                ? 0L
                : request.lastSequenceReceived();

        long current = boardEventService.getLatestSequence(boardId);

        SyncResponse fromRedis = operationSequenceService.buildSyncResponse(boardId, request, current);
        if (fromRedis.status() != SyncStatus.SYNC_REQUIRED) {
            return fromRedis;
        }

        List<BoardEvent> events = eventRepository.findByBoardIdAndSequenceGreaterThanOrderBySequenceAsc(
                boardId, lastSequenceReceived, PageRequest.of(0, fallbackLimit));

        if (events.isEmpty()) {
            return syncRequired(boardId, current);
        }

        List<SequencedOperation> operations = new ArrayList<>(events.size());
        long expected = lastSequenceReceived + 1;
        for (BoardEvent event : events) {
            if (event.getSequence() != expected) {
                // The client needs history the durable log doesn't have from this point
                // (e.g. a board whose events only start after Phase 8 was deployed).
                return syncRequired(boardId, current);
            }
            Operation operation;
            try {
                operation = objectMapper.readValue(event.getPayload(), Operation.class);
            } catch (Exception ex) {
                log.error("Corrupt event {} on board {}; refusing to replay a hole", event.getId(), boardId, ex);
                return syncRequired(boardId, current);
            }
            operations.add(new SequencedOperation(event.getSequence(), operation));
            expected++;
        }

        // If the page was capped the client is still behind: its next operation's
        // sequence will exceed lastApplied + 1 and its gap logic will sync again from there.
        return new SyncResponse(SyncStatus.OK, boardId, current, operations);
    }

    private static SyncResponse syncRequired(UUID boardId, long current) {
        return new SyncResponse(SyncStatus.SYNC_REQUIRED, boardId, current, List.of());
    }
}
