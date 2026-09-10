package com.ishan.syncCanvas.collaboration.presence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ishan.syncCanvas.collaboration.exception.BoardAccessDeniedException;
import com.ishan.syncCanvas.collaboration.service.BoardAccessGuard;
import com.ishan.syncCanvas.security.user.UserPrincipal;
import com.ishan.syncCanvas.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PresenceServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private SetOperations<String, String> setOperations;
    @Mock
    private SimpMessagingTemplate messagingTemplate;
    @Mock
    private PresenceEventBroadcaster presenceEventBroadcaster;
    @Mock
    private BoardAccessGuard boardAccessGuard;

    private PresenceService presenceService;
    private ObjectMapper objectMapper;

    private final UUID boardId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(redisTemplate.opsForSet()).thenReturn(setOperations);

        presenceService = new PresenceService(
                redisTemplate, objectMapper, messagingTemplate, presenceEventBroadcaster, boardAccessGuard);
        ReflectionTestUtils.setField(presenceService, "presenceTtlSeconds", 90L);
        ReflectionTestUtils.setField(presenceService, "idleThresholdSeconds", 30L);
    }

    private UserPrincipal principal() {
        User user = User.builder().id(userId).name("Ishan").email("ishan@example.com").build();
        return UserPrincipal.create(user);
    }

    @Test
    void unauthorizedAccessIsRejectedWithoutThrowing() {
        doThrow(new BoardAccessDeniedException("denied")).when(boardAccessGuard).assertAccessible(boardId, userId);

        boolean accessible = presenceService.checkAccessible(boardId, userId);

        assertThat(accessible).isFalse();
    }

    @Test
    void authorizedAccessIsAccepted() {
        boolean accessible = presenceService.checkAccessible(boardId, userId);

        assertThat(accessible).isTrue();
    }

    @Test
    void firstConnectionIsReportedAsGenuineJoin() {
        when(valueOperations.increment(anyString())).thenReturn(1L);

        boolean firstConnection = presenceService.recordConnection(boardId, userId);

        assertThat(firstConnection).isTrue();
        verify(redisTemplate).expire(eq("presence:board:" + boardId + ":" + userId + ":connections"), eq(Duration.ofSeconds(90)));
    }

    @Test
    void secondConnectionIsNotReportedAsGenuineJoin() {
        when(valueOperations.increment(anyString())).thenReturn(2L);

        boolean firstConnection = presenceService.recordConnection(boardId, userId);

        assertThat(firstConnection).isFalse();
    }

    @Test
    void releaseConnectionRemovesPresenceOnlyWhenCountReachesZero() {
        when(valueOperations.decrement(anyString())).thenReturn(0L);

        presenceService.releaseConnection(boardId, userId);

        verify(redisTemplate).delete("presence:board:" + boardId + ":" + userId);
        verify(setOperations).remove("presence:board:" + boardId + ":members", userId.toString());
        verify(messagingTemplate).convertAndSend(
                eq("/topic/boards/" + boardId + "/presence"),
                argThat((PresenceEvent e) -> e.type() == PresenceEventType.USER_LEFT && e.userId().equals(userId)));
        verify(presenceEventBroadcaster).broadcast(eq(boardId), argThat(e -> e.type() == PresenceEventType.USER_LEFT));
    }

    @Test
    void releaseConnectionLeavesPresenceIntactWhenOtherConnectionsRemain() {
        when(valueOperations.decrement(anyString())).thenReturn(1L);

        presenceService.releaseConnection(boardId, userId);

        verify(redisTemplate, never()).delete("presence:board:" + boardId + ":" + userId);
        verifyNoInteractions(messagingTemplate, presenceEventBroadcaster);
    }

    @Test
    void completeJoinBroadcastsAndSendsInitialStateWhenGenuineJoin() {
        presenceService.completeJoin(boardId, principal(), true);

        verify(valueOperations).set(eq("presence:board:" + boardId + ":" + userId), anyString(), eq(Duration.ofSeconds(90)));
        verify(setOperations).add("presence:board:" + boardId + ":members", userId.toString());
        verify(messagingTemplate).convertAndSend(
                eq("/topic/boards/" + boardId + "/presence"),
                argThat((PresenceEvent e) -> e.type() == PresenceEventType.USER_JOINED && e.userId().equals(userId)));
        verify(presenceEventBroadcaster).broadcast(eq(boardId), argThat(e -> e.type() == PresenceEventType.USER_JOINED));
        verify(messagingTemplate).convertAndSendToUser(eq(userId.toString()), anyString(), any(PresenceInitialStateEvent.class));
    }

    @Test
    void completeJoinSkipsBroadcastButStillSendsInitialStateForAnotherTab() {
        presenceService.completeJoin(boardId, principal(), false);

        verify(presenceEventBroadcaster, never()).broadcast(any(), any());
        verify(messagingTemplate, never()).convertAndSend(anyString(), any(PresenceEvent.class));
        verify(messagingTemplate).convertAndSendToUser(eq(userId.toString()), anyString(), any(PresenceInitialStateEvent.class));
    }

    @Test
    void heartbeatRecoveringFromIdleBroadcastsPresenceUpdate() throws Exception {
        String staleJson = objectMapper.writeValueAsString(
                new PresenceRecord(userId, "Ishan", Instant.now().minusSeconds(120).toEpochMilli()));
        when(valueOperations.get("presence:board:" + boardId + ":" + userId)).thenReturn(staleJson);

        presenceService.heartbeat(boardId, principal());

        verify(messagingTemplate).convertAndSend(
                eq("/topic/boards/" + boardId + "/presence"),
                argThat((PresenceEvent e) -> e.type() == PresenceEventType.PRESENCE_UPDATE && e.status() == PresenceStatus.ONLINE));
    }

    @Test
    void heartbeatWhileAlreadyOnlineDoesNotRebroadcast() throws Exception {
        String freshJson = objectMapper.writeValueAsString(
                new PresenceRecord(userId, "Ishan", Instant.now().toEpochMilli()));
        when(valueOperations.get("presence:board:" + boardId + ":" + userId)).thenReturn(freshJson);

        presenceService.heartbeat(boardId, principal());

        verifyNoInteractions(messagingTemplate, presenceEventBroadcaster);
    }

    @Test
    void currentParticipantStateIsNullWhenDataHasExpired() {
        when(valueOperations.get(anyString())).thenReturn(null);

        assertThat(presenceService.getCurrentParticipantState(boardId, userId)).isNull();
    }

    @Test
    void activeParticipantsSkipsMembersWhoseDataAlreadyExpired() {
        when(setOperations.members("presence:board:" + boardId + ":members")).thenReturn(Set.of(userId.toString()));
        when(valueOperations.get(anyString())).thenReturn(null);

        assertThat(presenceService.getActiveParticipants(boardId)).isEmpty();
    }
}
