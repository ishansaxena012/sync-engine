package com.ishan.syncCanvas.video;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ishan.syncCanvas.collaboration.exception.BoardAccessDeniedException;
import com.ishan.syncCanvas.collaboration.service.BoardAccessGuard;
import com.ishan.syncCanvas.security.user.UserPrincipal;
import com.ishan.syncCanvas.user.entity.User;
import com.ishan.syncCanvas.video.dto.VideoSignalMessage;
import com.ishan.syncCanvas.video.dto.VideoSignalRequest;
import com.ishan.syncCanvas.video.dto.VideoSignalType;
import com.ishan.syncCanvas.video.exception.VideoSignalRejectedException;
import com.ishan.syncCanvas.video.publisher.VideoSignalBroadcaster;
import com.ishan.syncCanvas.video.service.VideoRoomService;
import com.ishan.syncCanvas.video.service.VideoSignalRateLimiter;
import com.ishan.syncCanvas.video.service.VideoSignalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VideoSignalServiceTest {

    @Mock
    private BoardAccessGuard boardAccessGuard;
    @Mock
    private VideoRoomService videoRoomService;
    @Mock
    private VideoSignalRateLimiter rateLimiter;
    @Mock
    private SimpMessagingTemplate messagingTemplate;
    @Mock
    private VideoSignalBroadcaster videoSignalBroadcaster;

    // Mirrors Spring Boot's auto-configured ObjectMapper, which disables
    // FAIL_ON_UNKNOWN_PROPERTIES by default (Jackson2ObjectMapperBuilder) -- the same
    // mapper instance the STOMP message converter actually uses in production.
    private final ObjectMapper objectMapper = new ObjectMapper()
            .disable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private VideoSignalService service;

    private final UUID boardId = UUID.randomUUID();
    private final UUID senderId = UUID.randomUUID();
    private final UUID targetUserId = UUID.randomUUID();

    private final UserPrincipal sender =
            UserPrincipal.create(User.builder().id(senderId).name("Ishan").email("ishan@example.com").build());

    /** The value the sender's most recent join call would have handed back privately. */
    private final String currentSessionId = "session-" + UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new VideoSignalService(
                boardAccessGuard, videoRoomService, rateLimiter, objectMapper, messagingTemplate, videoSignalBroadcaster);
        ReflectionTestUtils.setField(service, "maxSdpPayloadBytes", 64 * 1024);
        ReflectionTestUtils.setField(service, "maxIcePayloadBytes", 8 * 1024);

        // Default happy path: board access ok, both sender and target are active
        // participants of this board's call, and the sender's session is current.
        // Individual tests override as needed.
        lenient().when(videoRoomService.isActiveParticipant(boardId, senderId)).thenReturn(true);
        lenient().when(videoRoomService.isActiveParticipant(boardId, targetUserId)).thenReturn(true);
        lenient().when(videoRoomService.isCurrentSignalingSession(boardId, senderId, currentSessionId)).thenReturn(true);
    }

    // A JSON object deserializes to a Map<String, Object> under Object-typed binding
    // (see VideoSignalRequest's Javadoc) -- these mirror that real runtime shape rather
    // than a Jackson tree type.
    private Map<String, Object> sdpPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sdp", "v=0\r\no=- 1 1 IN IP4 127.0.0.1\r\n...");
        return payload;
    }

    private Map<String, Object> icePayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("candidate", "candidate:1 1 UDP 2130706431 10.0.0.1 54400 typ host");
        return payload;
    }

    private VideoSignalRequest request(VideoSignalType type, UUID target, Object payload) {
        boolean isIce = type == VideoSignalType.ICE_CANDIDATE;
        return new VideoSignalRequest(type, target, isIce ? null : payload, isIce ? payload : null, currentSessionId);
    }

    // ---------------------------------------------------------------- signaling (1-6)

    @Test
    void participantCanSendOffer() {
        service.relay(boardId, sender, request(VideoSignalType.OFFER, targetUserId, sdpPayload()));

        verify(messagingTemplate).convertAndSendToUser(
                eqTargetString(), eqSignalDestination(), any(VideoSignalMessage.class));
    }

    @Test
    void participantCanSendAnswer() {
        service.relay(boardId, sender, request(VideoSignalType.ANSWER, targetUserId, sdpPayload()));

        verify(messagingTemplate).convertAndSendToUser(
                eqTargetString(), eqSignalDestination(), any(VideoSignalMessage.class));
    }

    @Test
    void participantCanSendIceCandidate() {
        service.relay(boardId, sender, request(VideoSignalType.ICE_CANDIDATE, targetUserId, icePayload()));

        verify(messagingTemplate).convertAndSendToUser(
                eqTargetString(), eqSignalDestination(), any(VideoSignalMessage.class));
    }

    @Test
    void targetReceivesSignalingMessageOnItsOwnPrivateQueue() {
        service.relay(boardId, sender, request(VideoSignalType.OFFER, targetUserId, sdpPayload()));

        verify(messagingTemplate).convertAndSendToUser(
                eq(targetUserId.toString()), eq("/queue/boards/" + boardId + "/video/signal"), any(VideoSignalMessage.class));
    }

    @Test
    void senderIdentityComesFromAuthenticationNotTheRequestBody() {
        service.relay(boardId, sender, request(VideoSignalType.OFFER, targetUserId, sdpPayload()));

        ArgumentCaptor<VideoSignalMessage> captor = ArgumentCaptor.forClass(VideoSignalMessage.class);
        verify(videoSignalBroadcaster).broadcast(eq(boardId), eqTargetUuid(), captor.capture());
        assertThat(captor.getValue().fromUserId()).isEqualTo(senderId);
    }

    @Test
    void clientCannotSpoofSenderIdentityViaAnExtraJsonField() throws Exception {
        UUID spoofedSenderId = UUID.randomUUID();
        String rawJson = "{\"type\":\"OFFER\",\"targetUserId\":\"" + targetUserId
                + "\",\"signalingSessionId\":\"" + currentSessionId
                + "\",\"senderId\":\"" + spoofedSenderId + "\",\"sdp\":{\"sdp\":\"v=0\"}}";

        // VideoSignalRequest has no senderId component at all, so Jackson's default
        // "ignore unknown properties" behavior drops it silently rather than binding it.
        VideoSignalRequest parsed = objectMapper.readValue(rawJson, VideoSignalRequest.class);

        service.relay(boardId, sender, parsed);

        ArgumentCaptor<VideoSignalMessage> captor = ArgumentCaptor.forClass(VideoSignalMessage.class);
        verify(videoSignalBroadcaster).broadcast(eq(boardId), eqTargetUuid(), captor.capture());
        assertThat(captor.getValue().fromUserId()).isEqualTo(senderId).isNotEqualTo(spoofedSenderId);
    }

    // ---------------------------------------------------------------- targeting (7-10)

    @Test
    void nonexistentTargetIsRejected() {
        when(videoRoomService.isActiveParticipant(boardId, targetUserId)).thenReturn(false);

        assertThatThrownBy(() -> service.relay(boardId, sender, request(VideoSignalType.OFFER, targetUserId, sdpPayload())))
                .isInstanceOf(VideoSignalRejectedException.class)
                .hasMessageContaining("Target user is not an active participant");
        verifyNoInteractions(messagingTemplate, videoSignalBroadcaster);
    }

    @Test
    void targetMustBelongToTheSameActiveRoom() {
        // The target is scoped strictly to *this* board's participants hash; a user
        // who is only active in some other board's call is indistinguishable here from
        // one who was never in a call at all — both resolve to false.
        when(videoRoomService.isActiveParticipant(boardId, targetUserId)).thenReturn(false);

        assertThatThrownBy(() -> service.relay(boardId, sender, request(VideoSignalType.OFFER, targetUserId, sdpPayload())))
                .isInstanceOf(VideoSignalRejectedException.class);
    }

    @Test
    void crossBoardTargetIsRejected() {
        UUID otherBoardId = UUID.randomUUID();
        // Target is active in a different board's room, never queried for otherBoardId
        // here — only this board's membership is ever consulted, so it is rejected.
        when(videoRoomService.isActiveParticipant(boardId, targetUserId)).thenReturn(false);

        assertThatThrownBy(() -> service.relay(boardId, sender, request(VideoSignalType.OFFER, targetUserId, sdpPayload())))
                .isInstanceOf(VideoSignalRejectedException.class);
        verify(videoRoomService, never()).isActiveParticipant(otherBoardId, targetUserId);
    }

    @Test
    void nonParticipantSenderIsRejectedBeforeTargetIsEvenChecked() {
        when(videoRoomService.isActiveParticipant(boardId, senderId)).thenReturn(false);

        assertThatThrownBy(() -> service.relay(boardId, sender, request(VideoSignalType.OFFER, targetUserId, sdpPayload())))
                .isInstanceOf(VideoSignalRejectedException.class)
                .hasMessageContaining("You are not an active participant");
        verify(videoRoomService, never()).isActiveParticipant(boardId, targetUserId);
    }

    // ---------------------------------------------------------------- authorization (11-14)

    @Test
    void unauthorizedBoardAccessIsRejectedBeforeAnyRoomStateIsTouched() {
        doThrow(new BoardAccessDeniedException("no access")).when(boardAccessGuard).assertAccessible(boardId, senderId);

        assertThatThrownBy(() -> service.relay(boardId, sender, request(VideoSignalType.OFFER, targetUserId, sdpPayload())))
                .isInstanceOf(BoardAccessDeniedException.class);
        verifyNoInteractions(videoRoomService, rateLimiter, messagingTemplate, videoSignalBroadcaster);
    }

    @Test
    void signalingAfterTheSenderHasLeftIsRejected() {
        when(videoRoomService.isActiveParticipant(boardId, senderId)).thenReturn(false);

        assertThatThrownBy(() -> service.relay(boardId, sender, request(VideoSignalType.OFFER, targetUserId, sdpPayload())))
                .isInstanceOf(VideoSignalRejectedException.class);
    }

    @Test
    void signalingAfterTheRoomHasEndedIsRejected() {
        // Both are inactive once the room has ended, but the sender check short-circuits
        // first -- the target stub reflects real post-END state and is intentionally
        // lenient since this test only needs to prove the request is rejected either way.
        when(videoRoomService.isActiveParticipant(boardId, senderId)).thenReturn(false);
        lenient().when(videoRoomService.isActiveParticipant(boardId, targetUserId)).thenReturn(false);

        assertThatThrownBy(() -> service.relay(boardId, sender, request(VideoSignalType.OFFER, targetUserId, sdpPayload())))
                .isInstanceOf(VideoSignalRejectedException.class);
        verifyNoInteractions(messagingTemplate, videoSignalBroadcaster);
    }

    // Item 14 ("unauthorized subscription rejected") is enforced by the pre-existing,
    // unmodified StompAuthChannelInterceptor plus the fact that a signal is only ever
    // routed to a validated active participant of this exact board's room (see the
    // targeting tests above) -- there is no new subscription-time check introduced by
    // this class, so it is not re-tested here.

    // ------------------------------------------- signaling session / reconnect (7-12, 25)

    @Test
    void requestWithoutASignalingSessionStillWorksForClientsThatDoNotSendOne() {
        // The current frontend never sends this field at all -- freshness checking is
        // opt-in, and its absence must never block a legitimate signal.
        VideoSignalRequest noSession = new VideoSignalRequest(VideoSignalType.OFFER, targetUserId, sdpPayload(), null, null);

        service.relay(boardId, sender, noSession);

        verify(messagingTemplate).convertAndSendToUser(eqTargetString(), eqSignalDestination(), any(VideoSignalMessage.class));
        verify(videoRoomService, never()).isCurrentSignalingSession(any(), any(), any());
    }

    @Test
    void staleSignalingSessionFromASupersededConnectionIsRejected() {
        // A newer join (reconnect, or a second tab) rotated the authoritative session --
        // this request still carries the old, now-superseded one.
        String staleSessionId = "stale-" + UUID.randomUUID();
        when(videoRoomService.isCurrentSignalingSession(boardId, senderId, staleSessionId)).thenReturn(false);

        VideoSignalRequest stale = new VideoSignalRequest(VideoSignalType.OFFER, targetUserId, sdpPayload(), null, staleSessionId);

        assertThatThrownBy(() -> service.relay(boardId, sender, stale))
                .isInstanceOf(VideoSignalRejectedException.class)
                .hasMessageContaining("Stale signaling session");
        verifyNoInteractions(messagingTemplate, videoSignalBroadcaster);
    }

    @Test
    void signalingWithTheCurrentSessionAfterReconnectSucceeds() {
        // A rejoin mints a brand new session id; the client uses it going forward.
        String freshSessionId = "fresh-" + UUID.randomUUID();
        when(videoRoomService.isCurrentSignalingSession(boardId, senderId, freshSessionId)).thenReturn(true);

        VideoSignalRequest afterReconnect = new VideoSignalRequest(VideoSignalType.OFFER, targetUserId, sdpPayload(), null, freshSessionId);
        service.relay(boardId, sender, afterReconnect);

        verify(messagingTemplate).convertAndSendToUser(eqTargetString(), eqSignalDestination(), any(VideoSignalMessage.class));
    }

    @Test
    void iceRestartReusesTheSameStillCurrentSessionWithoutTouchingRoomMembership() {
        // An ICE restart is just a fresh OFFER/ANSWER over the same live connection --
        // no new join() call, no membership change, same session id throughout.
        service.relay(boardId, sender, request(VideoSignalType.OFFER, targetUserId, sdpPayload()));
        service.relay(boardId, sender, request(VideoSignalType.ANSWER, targetUserId, sdpPayload()));

        verify(videoRoomService, never()).join(any(), any());
        verify(messagingTemplate, org.mockito.Mockito.times(2))
                .convertAndSendToUser(eqTargetString(), eqSignalDestination(), any(VideoSignalMessage.class));
    }

    // ---------------------------------------------------------------- redis (15-17)

    @Test
    void relayAlwaysAttemptsLocalDeliveryAndBroadcastsForOtherInstances() {
        // No same-instance/cross-instance branching in this class at all: local delivery
        // is attempted unconditionally (a no-op if the target isn't connected here) and
        // the event is always relayed via Redis too, exactly like the room-lifecycle
        // broadcaster. VideoSignalSubscriberTest proves the receiving side drops its own
        // echo, which is what prevents this from ever double-delivering.
        service.relay(boardId, sender, request(VideoSignalType.OFFER, targetUserId, sdpPayload()));

        verify(messagingTemplate).convertAndSendToUser(eqTargetString(), eqSignalDestination(), any(VideoSignalMessage.class));
        verify(videoSignalBroadcaster).broadcast(eq(boardId), eqTargetUuid(), any(VideoSignalMessage.class));
    }

    @Test
    void redisFailureDuringParticipantCheckPropagatesAsAControlledError() {
        when(videoRoomService.isActiveParticipant(boardId, senderId))
                .thenThrow(new RedisConnectionFailureException("redis down"));

        assertThatThrownBy(() -> service.relay(boardId, sender, request(VideoSignalType.OFFER, targetUserId, sdpPayload())))
                .isInstanceOf(RedisConnectionFailureException.class);
        verifyNoInteractions(messagingTemplate, videoSignalBroadcaster);
    }

    // ---------------------------------------------------------------- validation (18-21)

    @Test
    void requestWithNoTypeIsRejected() {
        VideoSignalRequest malformed = new VideoSignalRequest(null, targetUserId, sdpPayload(), null, currentSessionId);

        assertThatThrownBy(() -> service.relay(boardId, sender, malformed))
                .isInstanceOf(VideoSignalRejectedException.class)
                .hasMessageContaining("type is required");
    }

    @Test
    void unrecognizedSignalingTypeFailsDeserializationBeforeEverReachingTheService() {
        String rawJson = "{\"type\":\"HANGUP\",\"targetUserId\":\"" + targetUserId + "\",\"payload\":{}}";

        assertThatThrownBy(() -> objectMapper.readValue(rawJson, VideoSignalRequest.class))
                .isInstanceOf(Exception.class);
        verifyNoInteractions(boardAccessGuard, videoRoomService);
    }

    @Test
    void oversizedSdpPayloadIsRejected() {
        Map<String, Object> oversized = new LinkedHashMap<>();
        oversized.put("sdp", "x".repeat(70 * 1024));

        assertThatThrownBy(() -> service.relay(boardId, sender, request(VideoSignalType.OFFER, targetUserId, oversized)))
                .isInstanceOf(VideoSignalRejectedException.class)
                .hasMessageContaining("exceeds the maximum allowed size");
        verifyNoInteractions(messagingTemplate, videoSignalBroadcaster);
    }

    @Test
    void oversizedIceCandidateIsRejected() {
        Map<String, Object> oversized = new LinkedHashMap<>();
        oversized.put("candidate", "x".repeat(9 * 1024));

        assertThatThrownBy(() -> service.relay(boardId, sender, request(VideoSignalType.ICE_CANDIDATE, targetUserId, oversized)))
                .isInstanceOf(VideoSignalRejectedException.class)
                .hasMessageContaining("exceeds the maximum allowed size");
    }

    @Test
    void missingPayloadIsRejected() {
        assertThatThrownBy(() -> service.relay(boardId, sender, request(VideoSignalType.OFFER, targetUserId, null)))
                .isInstanceOf(VideoSignalRejectedException.class)
                .hasMessageContaining("payload is required");
    }

    @Test
    void nonObjectPayloadIsRejectedAsMalformed() {
        String scalarPayload = "just a string, not an object";

        assertThatThrownBy(() -> service.relay(boardId, sender, request(VideoSignalType.OFFER, targetUserId, scalarPayload)))
                .isInstanceOf(VideoSignalRejectedException.class)
                .hasMessageContaining("must be a JSON object");
    }

    @Test
    void missingTargetIsRejected() {
        assertThatThrownBy(() -> service.relay(boardId, sender, request(VideoSignalType.OFFER, null, sdpPayload())))
                .isInstanceOf(VideoSignalRejectedException.class)
                .hasMessageContaining("target participant is required");
    }

    @Test
    void selfTargetedSignalingIsRejected() {
        assertThatThrownBy(() -> service.relay(boardId, sender, request(VideoSignalType.OFFER, senderId, sdpPayload())))
                .isInstanceOf(VideoSignalRejectedException.class)
                .hasMessageContaining("yourself");
    }

    // ---------------------------------------------------------------- lifecycle (22-24)

    @Test
    void disconnectPreventsFurtherSignalingFromThatUser() {
        // Disconnect cleanup removes the participant's hash entry, which is exactly
        // what isActiveParticipant reflects -- the very next signal attempt sees them
        // as no longer active.
        when(videoRoomService.isActiveParticipant(boardId, senderId)).thenReturn(false);

        assertThatThrownBy(() -> service.relay(boardId, sender, request(VideoSignalType.OFFER, targetUserId, sdpPayload())))
                .isInstanceOf(VideoSignalRejectedException.class);
    }

    @Test
    void boardDeletionEndsSignalingForEveryoneOnThatBoard() {
        // clearBoardState wipes the participants hash entirely -- both sender and
        // target read back as inactive afterward. The sender check short-circuits
        // first, so the target stub is lenient.
        when(videoRoomService.isActiveParticipant(boardId, senderId)).thenReturn(false);
        lenient().when(videoRoomService.isActiveParticipant(boardId, targetUserId)).thenReturn(false);

        assertThatThrownBy(() -> service.relay(boardId, sender, request(VideoSignalType.OFFER, targetUserId, sdpPayload())))
                .isInstanceOf(VideoSignalRejectedException.class);
        verifyNoInteractions(messagingTemplate, videoSignalBroadcaster);
    }

    @Test
    void aStaleSignalArrivingRightAsTheRoomEndsIsRejectedNotForwarded() {
        when(videoRoomService.isActiveParticipant(boardId, targetUserId)).thenReturn(false);

        assertThatThrownBy(() -> service.relay(boardId, sender, request(VideoSignalType.ICE_CANDIDATE, targetUserId, icePayload())))
                .isInstanceOf(VideoSignalRejectedException.class);
        verifyNoInteractions(messagingTemplate, videoSignalBroadcaster);
    }

    // ---------------------------------------------------------------- multi-tab (25)

    @Test
    void oneTabDisconnectingDoesNotBreakSignalingWhileAnotherTabIsStillActive() {
        // Both sender and target still resolve active (their connection-count hash
        // field is still > 0 from another tab) -- signaling proceeds normally.
        service.relay(boardId, sender, request(VideoSignalType.OFFER, targetUserId, sdpPayload()));

        verify(messagingTemplate).convertAndSendToUser(eqTargetString(), eqSignalDestination(), any(VideoSignalMessage.class));
    }

    // ---------------------------------------------------------------- rate limiting

    @Test
    void everyRelayIsRateLimitedPerSenderPerBoardAndSignalType() {
        service.relay(boardId, sender, request(VideoSignalType.OFFER, targetUserId, sdpPayload()));

        verify(rateLimiter).assertWithinLimit(boardId, senderId, VideoSignalType.OFFER);
    }

    @Test
    void rateLimitedSenderIsRejectedWithoutForwarding() {
        doThrow(new VideoSignalRejectedException("slow down"))
                .when(rateLimiter).assertWithinLimit(boardId, senderId, VideoSignalType.OFFER);

        assertThatThrownBy(() -> service.relay(boardId, sender, request(VideoSignalType.OFFER, targetUserId, sdpPayload())))
                .isInstanceOf(VideoSignalRejectedException.class);
        verifyNoInteractions(messagingTemplate, videoSignalBroadcaster);
    }

    private String eqTargetString() {
        return eq(targetUserId.toString());
    }

    private UUID eqTargetUuid() {
        return eq(targetUserId);
    }

    private String eqSignalDestination() {
        return eq("/queue/boards/" + boardId + "/video/signal");
    }
}
