package com.ishan.syncCanvas.collaboration.sync;

import com.ishan.syncCanvas.collaboration.service.BoardAccessGuard;
import com.ishan.syncCanvas.security.user.UserPrincipal;
import com.ishan.syncCanvas.user.entity.User;
import com.ishan.syncCanvas.websocket.controller.SyncController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SyncControllerTest {

    @Mock
    private OperationSequenceService operationSequenceService;
    @Mock
    private BoardAccessGuard boardAccessGuard;
    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private SyncController controller;

    private final UUID boardId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UserPrincipal principal =
            UserPrincipal.create(User.builder().id(userId).name("Ishan").email("ishan@example.com").build());

    @BeforeEach
    void setUp() {
        controller = new SyncController(operationSequenceService, boardAccessGuard, messagingTemplate);
    }

    @Test
    void authorizedUserReceivesSyncResponseOnPrivateQueue() {
        SyncResponse response = new SyncResponse(SyncStatus.UP_TO_DATE, boardId, 5L, List.of());
        when(boardAccessGuard.isAccessible(boardId, userId)).thenReturn(true);
        when(operationSequenceService.buildSyncResponse(eq(boardId), any(SyncRequest.class))).thenReturn(response);

        controller.sync(boardId, new SyncRequest(5L), principal);

        verify(messagingTemplate).convertAndSendToUser(
                userId.toString(), "/queue/boards/" + boardId + "/sync", response);
    }

    @Test
    void unauthorizedUserGetsNothingAndNoReplayIsComputed() {
        when(boardAccessGuard.isAccessible(boardId, userId)).thenReturn(false);

        controller.sync(boardId, new SyncRequest(5L), principal);

        verifyNoInteractions(operationSequenceService, messagingTemplate);
    }

    @Test
    void identityComesFromPrincipalNotPayload() {
        // SyncRequest carries only lastSequenceReceived — there is no user field to spoof.
        when(boardAccessGuard.isAccessible(boardId, userId)).thenReturn(true);
        when(operationSequenceService.buildSyncResponse(eq(boardId), any(SyncRequest.class)))
                .thenReturn(new SyncResponse(SyncStatus.UP_TO_DATE, boardId, 0L, List.of()));

        controller.sync(boardId, new SyncRequest(0L), principal);

        verify(boardAccessGuard).isAccessible(boardId, userId);
    }
}
