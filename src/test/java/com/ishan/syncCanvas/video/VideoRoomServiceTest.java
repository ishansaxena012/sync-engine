package com.ishan.syncCanvas.video;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ishan.syncCanvas.collaboration.exception.BoardAccessDeniedException;
import com.ishan.syncCanvas.collaboration.service.BoardAccessGuard;
import com.ishan.syncCanvas.security.user.UserPrincipal;
import com.ishan.syncCanvas.user.entity.User;
import com.ishan.syncCanvas.video.dto.VideoRoomEvent;
import com.ishan.syncCanvas.video.dto.VideoRoomEventType;
import com.ishan.syncCanvas.video.dto.VideoRoomResponse;
import com.ishan.syncCanvas.video.dto.VideoRosterResponse;
import com.ishan.syncCanvas.video.dto.VideoSignalingSession;
import com.ishan.syncCanvas.video.exception.VideoRoomAccessDeniedException;
import com.ishan.syncCanvas.video.exception.VideoRoomFullException;
import com.ishan.syncCanvas.video.exception.VideoRoomNotFoundException;
import com.ishan.syncCanvas.video.model.VideoParticipant;
import com.ishan.syncCanvas.video.publisher.VideoEventBroadcaster;
import com.ishan.syncCanvas.video.service.VideoRoomService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VideoRoomServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private HashOperations<String, String, String> hashOperations;
    @Mock
    private SimpMessagingTemplate messagingTemplate;
    @Mock
    private VideoEventBroadcaster videoEventBroadcaster;
    @Mock
    private BoardAccessGuard boardAccessGuard;

    private VideoRoomService videoRoomService;
    private ObjectMapper objectMapper;

    private final UUID boardId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID otherUserId = UUID.randomUUID();

    private String participantsKey;
    private String connectionsKey;
    private String countKey;
    private String creatorKey;
    private String sessionsKey;

    /**
     * A bare mock of the participants hash would report itself as empty forever,
     * regardless of what the service just put into it — so every test that cares what
     * currentRoster()/readParticipant() sees afterward backs put/entries/get/delete for
     * the participants key with this real in-memory map instead, the same way a real
     * Redis hash would behave across calls within one test.
     */
    private final Map<String, String> participantsBacking = new LinkedHashMap<>();

    /** Same idea as {@link #participantsBacking}, for the signaling-session hash. */
    private final Map<String, String> sessionsBacking = new LinkedHashMap<>();

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(redisTemplate.<String, String>opsForHash()).thenReturn(hashOperations);

        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        videoRoomService = new VideoRoomService(
                redisTemplate, objectMapper, messagingTemplate, videoEventBroadcaster, boardAccessGuard);
        ReflectionTestUtils.setField(videoRoomService, "maxParticipants", 4);
        ReflectionTestUtils.setField(videoRoomService, "roomTtlSeconds", 21600L);

        participantsKey = "video:board:" + boardId + ":participants";
        connectionsKey = "video:board:" + boardId + ":connections";
        countKey = "video:board:" + boardId + ":count";
        creatorKey = "video:board:" + boardId + ":creator";
        sessionsKey = "video:board:" + boardId + ":sessions";

        lenient().doAnswer(inv -> {
            participantsBacking.put(inv.getArgument(1), inv.getArgument(2));
            return null;
        }).when(hashOperations).put(eq(participantsKey), anyString(), anyString());
        lenient().when(hashOperations.entries(participantsKey))
                .thenAnswer(inv -> new LinkedHashMap<>(participantsBacking));
        lenient().when(hashOperations.get(eq(participantsKey), anyString()))
                .thenAnswer(inv -> participantsBacking.get((String) inv.getArgument(1)));
        lenient().when(hashOperations.delete(eq(participantsKey), anyString())).thenAnswer(inv -> {
            Object removed = participantsBacking.remove((String) inv.getArgument(1));
            return removed == null ? 0L : 1L;
        });

        lenient().doAnswer(inv -> {
            sessionsBacking.put(inv.getArgument(1), inv.getArgument(2));
            return null;
        }).when(hashOperations).put(eq(sessionsKey), anyString(), anyString());
        lenient().when(hashOperations.get(eq(sessionsKey), anyString()))
                .thenAnswer(inv -> sessionsBacking.get((String) inv.getArgument(1)));
    }

    private UserPrincipal principal(UUID id, String name) {
        return UserPrincipal.create(User.builder().id(id).name(name).email(name + "@example.com").build());
    }

    private UserPrincipal caller() {
        return principal(userId, "Ishan");
    }

    /** Seeds the participants-hash fixture as if the given user had already joined. */
    private void seedExistingParticipant(UUID id, String name) throws Exception {
        participantsBacking.put(id.toString(),
                objectMapper.writeValueAsString(new VideoParticipant(id, name, Instant.now(), true, true)));
    }

    /** Mocks a genuine first-ever join: empty room, first tab, first participant. */
    private void mockGenuineFirstJoin() {
        when(hashOperations.increment(connectionsKey, userId.toString(), 1L)).thenReturn(1L);
        when(valueOperations.increment(countKey)).thenReturn(1L);
    }

    // ---------------------------------------------------------------- room lifecycle

    @Test
    void startCreatesARoomAndAddsTheCallerAsCreator() {
        mockGenuineFirstJoin();

        VideoRoomEvent event = videoRoomService.join(boardId, caller());

        assertThat(event.type()).isEqualTo(VideoRoomEventType.ROOM_STARTED);
        assertThat(event.boardId()).isEqualTo(boardId);
        verify(valueOperations).setIfAbsent(creatorKey, userId.toString());
        verify(hashOperations).put(eq(participantsKey), eq(userId.toString()), anyString());
    }

    @Test
    void startingTwiceDoesNotCreateASecondRoom() {
        // setIfAbsent is inherently idempotent (a no-op once the key exists) — this
        // asserts the service always attempts it rather than only on some conditional
        // "first time" path, which is what makes concurrent starts race-free.
        mockGenuineFirstJoin();
        when(hashOperations.increment(connectionsKey, otherUserId.toString(), 1L)).thenReturn(1L);
        when(valueOperations.increment(countKey)).thenReturn(1L).thenReturn(2L);

        videoRoomService.join(boardId, caller());
        videoRoomService.join(boardId, principal(otherUserId, "Priya"));

        verify(valueOperations, times(2)).setIfAbsent(eq(creatorKey), anyString());
    }

    @Test
    void startAutomaticallyAddsTheCallerAsAParticipant() {
        mockGenuineFirstJoin();

        VideoRoomEvent event = videoRoomService.join(boardId, caller());

        assertThat(event.participant().userId()).isEqualTo(userId);
        assertThat(event.participant().userName()).isEqualTo("Ishan");
        assertThat(event.participants()).extracting(VideoParticipant::userId).containsExactly(userId);
    }

    @Test
    void joinAddsAParticipantToAnAlreadyActiveRoom() throws Exception {
        seedExistingParticipant(userId, "Ishan");
        when(hashOperations.increment(connectionsKey, otherUserId.toString(), 1L)).thenReturn(1L);
        when(valueOperations.increment(countKey)).thenReturn(2L); // one participant already present

        VideoRoomEvent event = videoRoomService.join(boardId, principal(otherUserId, "Priya"));

        assertThat(event.type()).isEqualTo(VideoRoomEventType.PARTICIPANT_JOINED);
    }

    @Test
    void multipleParticipantsJoinTheSameRoomNotSeparateOnes() {
        mockGenuineFirstJoin();
        videoRoomService.join(boardId, caller());

        when(hashOperations.increment(connectionsKey, otherUserId.toString(), 1L)).thenReturn(1L);
        when(valueOperations.increment(countKey)).thenReturn(2L);
        VideoRoomEvent second = videoRoomService.join(boardId, principal(otherUserId, "Priya"));

        // Both joins target the same board-scoped keys, so the resulting roster
        // contains both — a "second room" is structurally impossible here.
        assertThat(second.type()).isEqualTo(VideoRoomEventType.PARTICIPANT_JOINED);
        assertThat(second.participants()).extracting(VideoParticipant::userId)
                .containsExactlyInAnyOrder(userId, otherUserId);
    }

    @Test
    void repeatJoinFromASecondTabDoesNotRebroadcastOrDuplicateTheParticipant() {
        when(hashOperations.increment(connectionsKey, userId.toString(), 1L)).thenReturn(2L); // already had one tab

        VideoRoomEvent event = videoRoomService.join(boardId, caller());

        assertThat(event.type()).isEqualTo(VideoRoomEventType.ROOM_STATE);
        verify(hashOperations, never()).put(eq(participantsKey), anyString(), anyString());
        verifyNoInteractions(videoEventBroadcaster);
        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
        // The joining tab still learns the current roster, privately.
        verify(messagingTemplate).convertAndSendToUser(
                eq(userId.toString()), eq("/queue/boards/" + boardId + "/video/roster"), any(VideoRosterResponse.class));
    }

    @Test
    void leaveRemovesTheParticipantWhenOthersRemain() throws Exception {
        seedExistingParticipant(userId, "Ishan");
        when(redisTemplate.hasKey(countKey)).thenReturn(true);
        when(hashOperations.increment(connectionsKey, userId.toString(), -1L)).thenReturn(0L);
        when(valueOperations.decrement(countKey)).thenReturn(1L); // one participant still left

        Optional<VideoRoomEvent> event = videoRoomService.leave(boardId, caller());

        assertThat(event).isPresent();
        assertThat(event.get().type()).isEqualTo(VideoRoomEventType.PARTICIPANT_LEFT);
        assertThat(event.get().participant().userId()).isEqualTo(userId);
        assertThat(participantsBacking).doesNotContainKey(userId.toString());
        // The room itself is untouched — someone else is still in it.
        verify(redisTemplate, never()).delete(anyList());
    }

    @Test
    void lastParticipantLeavingRemovesTheRoom() throws Exception {
        seedExistingParticipant(userId, "Ishan");
        when(redisTemplate.hasKey(countKey)).thenReturn(true);
        when(hashOperations.increment(connectionsKey, userId.toString(), -1L)).thenReturn(0L);
        when(valueOperations.decrement(countKey)).thenReturn(0L);

        Optional<VideoRoomEvent> event = videoRoomService.leave(boardId, caller());

        assertThat(event).isPresent();
        assertThat(event.get().type()).isEqualTo(VideoRoomEventType.ROOM_ENDED);
        assertThat(event.get().participants()).isEmpty();
        verify(redisTemplate).delete(List.of(participantsKey, connectionsKey, countKey, creatorKey, sessionsKey));
    }

    @Test
    void leaveIsANoOpWhenTheRoomNoLongerExists() {
        when(redisTemplate.hasKey(countKey)).thenReturn(false);

        Optional<VideoRoomEvent> event = videoRoomService.leave(boardId, caller());

        assertThat(event).isEmpty();
        verify(hashOperations, never()).increment(anyString(), anyString(), anyLong());
        verifyNoInteractions(videoEventBroadcaster);
        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    void endRemovesTheEntireRoom() {
        when(valueOperations.get(creatorKey)).thenReturn(userId.toString());

        VideoRoomEvent event = videoRoomService.end(boardId, caller());

        assertThat(event.type()).isEqualTo(VideoRoomEventType.ROOM_ENDED);
        assertThat(event.participants()).isEmpty();
        verify(redisTemplate).delete(List.of(participantsKey, connectionsKey, countKey, creatorKey, sessionsKey));
    }

    @Test
    void onlyTheCreatorCanEndTheRoom() {
        when(valueOperations.get(creatorKey)).thenReturn(otherUserId.toString());

        assertThatThrownBy(() -> videoRoomService.end(boardId, caller()))
                .isInstanceOf(VideoRoomAccessDeniedException.class);

        verify(redisTemplate, never()).delete(anyList());
        verifyNoInteractions(videoEventBroadcaster);
    }

    @Test
    void endingANonExistentRoomFails() {
        when(valueOperations.get(creatorKey)).thenReturn(null);

        assertThatThrownBy(() -> videoRoomService.end(boardId, caller()))
                .isInstanceOf(VideoRoomNotFoundException.class);
    }

    // ---------------------------------------------------------------- authorization

    @Test
    void unauthorizedUserCannotStartOrJoin() {
        doThrow(new BoardAccessDeniedException("no access")).when(boardAccessGuard).assertAccessible(boardId, userId);

        assertThatThrownBy(() -> videoRoomService.join(boardId, caller()))
                .isInstanceOf(BoardAccessDeniedException.class);

        verifyNoInteractions(hashOperations, valueOperations, videoEventBroadcaster, messagingTemplate);
    }

    @Test
    void unauthorizedUserCannotInspectRoomState() {
        doThrow(new BoardAccessDeniedException("no access")).when(boardAccessGuard).assertAccessible(boardId, userId);

        assertThatThrownBy(() -> videoRoomService.getRoomState(boardId, userId))
                .isInstanceOf(BoardAccessDeniedException.class);

        verify(hashOperations, never()).entries(anyString());
    }

    @Test
    void cannotEndSomeoneElsesRoom() {
        when(valueOperations.get(creatorKey)).thenReturn(otherUserId.toString());

        assertThatThrownBy(() -> videoRoomService.end(boardId, caller()))
                .isInstanceOf(VideoRoomAccessDeniedException.class);
    }

    @Test
    void participantIdentityAlwaysComesFromTheAuthenticatedPrincipalNeverFromInput() {
        // There is no client-suppliable field anywhere in join()'s signature besides
        // boardId and the server-attached UserPrincipal — this pins that down.
        mockGenuineFirstJoin();

        VideoRoomEvent event = videoRoomService.join(boardId, principal(userId, "Ishan"));

        assertThat(event.participant().userId()).isEqualTo(userId);
        assertThat(event.participant().userName()).isEqualTo("Ishan");
    }

    // ---------------------------------------------------------------- concurrency (via atomic op return values)

    @Test
    void roomStartedDecisionComesFromTheAtomicCounterNotFromHlen() {
        // Two callers racing to be "first" would both see the participants hash as
        // empty at the instant they check it; only the INCR return value is guaranteed
        // unique per caller, which is why the service must use it (and does — verified
        // by never calling size()/entries() to decide the event type).
        when(hashOperations.increment(connectionsKey, userId.toString(), 1L)).thenReturn(1L);
        when(valueOperations.increment(countKey)).thenReturn(1L);
        VideoRoomEvent first = videoRoomService.join(boardId, caller());

        when(hashOperations.increment(connectionsKey, otherUserId.toString(), 1L)).thenReturn(1L);
        when(valueOperations.increment(countKey)).thenReturn(2L);
        VideoRoomEvent second = videoRoomService.join(boardId, principal(otherUserId, "Priya"));

        assertThat(first.type()).isEqualTo(VideoRoomEventType.ROOM_STARTED);
        assertThat(second.type()).isEqualTo(VideoRoomEventType.PARTICIPANT_JOINED);
    }

    @Test
    void concurrentLeaveAndJoinBothApplyWithoutLosingEitherUpdate() throws Exception {
        // A joins while B leaves: each operates on its own connections-hash field and
        // the shared count key only ever moves by the atomic amount each call applies,
        // regardless of interleaving.
        seedExistingParticipant(otherUserId, "Priya");
        when(redisTemplate.hasKey(countKey)).thenReturn(true);
        when(hashOperations.increment(connectionsKey, otherUserId.toString(), -1L)).thenReturn(0L);
        when(valueOperations.decrement(countKey)).thenReturn(1L);

        when(hashOperations.increment(connectionsKey, userId.toString(), 1L)).thenReturn(1L);
        when(valueOperations.increment(countKey)).thenReturn(2L);

        Optional<VideoRoomEvent> left = videoRoomService.leave(boardId, principal(otherUserId, "Priya"));
        VideoRoomEvent joined = videoRoomService.join(boardId, caller());

        assertThat(left).isPresent();
        assertThat(left.get().type()).isEqualTo(VideoRoomEventType.PARTICIPANT_LEFT);
        assertThat(joined.type()).isEqualTo(VideoRoomEventType.PARTICIPANT_JOINED);
        assertThat(joined.participants()).extracting(VideoParticipant::userId).containsExactly(userId);
    }

    // ---------------------------------------------------------------- disconnect / multi-tab

    @Test
    void disconnectRemovalUsesTheSameSafeRemovalPathAsExplicitLeave() throws Exception {
        seedExistingParticipant(userId, "Ishan");
        when(redisTemplate.hasKey(countKey)).thenReturn(true);
        when(hashOperations.increment(connectionsKey, userId.toString(), -1L)).thenReturn(0L);
        when(valueOperations.decrement(countKey)).thenReturn(0L);

        videoRoomService.removeParticipantOnDisconnect(boardId, userId);

        verify(redisTemplate).delete(List.of(participantsKey, connectionsKey, countKey, creatorKey, sessionsKey));
        // No board-access check for a disconnect-triggered cleanup — access being
        // revoked must never block removing stale membership.
        verifyNoInteractions(boardAccessGuard);
    }

    @Test
    void oneTabDisconnectingDoesNotRemoveAUserWithAnotherTabStillInTheCall() {
        when(redisTemplate.hasKey(countKey)).thenReturn(true);
        when(hashOperations.increment(connectionsKey, userId.toString(), -1L)).thenReturn(1L); // one tab remains

        videoRoomService.removeParticipantOnDisconnect(boardId, userId);

        verify(hashOperations, never()).delete(eq(participantsKey), anyString());
        verify(valueOperations, never()).decrement(countKey);
        verifyNoInteractions(videoEventBroadcaster);
    }

    // ---------------------------------------------------------------- board deletion

    @Test
    void clearBoardStateDeletesAllVideoKeysSilently() {
        videoRoomService.clearBoardState(boardId);

        verify(redisTemplate).delete(List.of(participantsKey, connectionsKey, countKey, creatorKey, sessionsKey));
        verifyNoInteractions(videoEventBroadcaster, messagingTemplate);
    }

    @Test
    void clearBoardStateSwallowsRedisFailuresRatherThanPropagating() {
        doThrow(new RuntimeException("redis down")).when(redisTemplate).delete(anyList());

        videoRoomService.clearBoardState(boardId); // must not throw — board deletion cannot be aborted by this
    }

    // ---------------------------------------------------------------- Redis failure handling

    @Test
    void joinDoesNotSwallowARedisFailure() {
        when(hashOperations.increment(anyString(), anyString(), eq(1L)))
                .thenThrow(new RedisConnectionFailureException("down"));

        assertThatThrownBy(() -> videoRoomService.join(boardId, caller()))
                .isInstanceOf(RedisConnectionFailureException.class);

        // Nothing was broadcast for a join that did not actually succeed.
        verifyNoInteractions(videoEventBroadcaster);
    }

    @Test
    void leaveDoesNotSwallowARedisFailure() {
        when(redisTemplate.hasKey(countKey)).thenReturn(true);
        when(hashOperations.increment(connectionsKey, userId.toString(), -1L))
                .thenThrow(new RedisConnectionFailureException("down"));

        assertThatThrownBy(() -> videoRoomService.leave(boardId, caller()))
                .isInstanceOf(RedisConnectionFailureException.class);
    }

    // ---------------------------------------------------------------- room capacity

    @Test
    void joinIsRejectedWhenTheRoomIsAlreadyAtCapacity() {
        ReflectionTestUtils.setField(videoRoomService, "maxParticipants", 2);
        when(hashOperations.increment(connectionsKey, userId.toString(), 1L)).thenReturn(1L);
        when(valueOperations.increment(countKey)).thenReturn(3L); // already 2 present, this would be a 3rd

        assertThatThrownBy(() -> videoRoomService.join(boardId, caller()))
                .isInstanceOf(VideoRoomFullException.class);

        // Rejected before ever recording the participant or broadcasting anything.
        verify(hashOperations, never()).put(eq(participantsKey), anyString(), anyString());
        verifyNoInteractions(videoEventBroadcaster);
        // Both increments are rolled back so the room's real occupancy is unaffected.
        verify(valueOperations).decrement(countKey);
        verify(hashOperations).increment(connectionsKey, userId.toString(), -1L);
    }

    @Test
    void joinSucceedsExactlyAtTheConfiguredLimit() {
        ReflectionTestUtils.setField(videoRoomService, "maxParticipants", 2);
        when(hashOperations.increment(connectionsKey, userId.toString(), 1L)).thenReturn(1L);
        when(valueOperations.increment(countKey)).thenReturn(2L); // exactly at the limit, not over it

        VideoRoomEvent event = videoRoomService.join(boardId, caller());

        assertThat(event.type()).isEqualTo(VideoRoomEventType.PARTICIPANT_JOINED);
    }

    @Test
    void aSecondTabOfAnAlreadyActiveParticipantIsExemptFromTheCapacityCheck() {
        ReflectionTestUtils.setField(videoRoomService, "maxParticipants", 1);
        // tabCount > 1 -- this is the same participant's second tab, not a new one, so
        // the room-count path (and therefore the capacity check) is never touched.
        when(hashOperations.increment(connectionsKey, userId.toString(), 1L)).thenReturn(2L);

        VideoRoomEvent event = videoRoomService.join(boardId, caller());

        assertThat(event.type()).isEqualTo(VideoRoomEventType.ROOM_STATE);
        verify(valueOperations, never()).increment(countKey);
    }

    // ---------------------------------------------------------------- signaling session

    @Test
    void joinReturnsAFreshSignalingSessionPrivately() {
        mockGenuineFirstJoin();

        videoRoomService.join(boardId, caller());

        ArgumentCaptor<VideoSignalingSession> captor = ArgumentCaptor.forClass(VideoSignalingSession.class);
        verify(messagingTemplate).convertAndSendToUser(
                eq(userId.toString()), eq("/queue/boards/" + boardId + "/video/session"), captor.capture());
        assertThat(captor.getValue().signalingSessionId()).isNotBlank();
    }

    @Test
    void everyJoinCallRotatesToANewSignalingSessionSupersedingTheOldOne() {
        mockGenuineFirstJoin();
        videoRoomService.join(boardId, caller());
        String firstSessionId = sessionsBacking.get(userId.toString());

        // Same user, a second connection (their own reconnect, or a second tab) --
        // tabCount now 2, no new room-count/capacity involvement.
        when(hashOperations.increment(connectionsKey, userId.toString(), 1L)).thenReturn(2L);
        videoRoomService.join(boardId, caller());
        String secondSessionId = sessionsBacking.get(userId.toString());

        assertThat(secondSessionId).isNotBlank().isNotEqualTo(firstSessionId);
        assertThat(videoRoomService.isCurrentSignalingSession(boardId, userId, firstSessionId)).isFalse();
        assertThat(videoRoomService.isCurrentSignalingSession(boardId, userId, secondSessionId)).isTrue();
    }

    @Test
    void rejoinAfterDisconnectProducesAGenuinelyNewParticipantAndSession() {
        // First join, then a full disconnect (drains to zero, deletes room state).
        mockGenuineFirstJoin();
        videoRoomService.join(boardId, caller());
        String firstSessionId = sessionsBacking.get(userId.toString());

        when(redisTemplate.hasKey(countKey)).thenReturn(true);
        when(hashOperations.increment(connectionsKey, userId.toString(), -1L)).thenReturn(0L);
        when(valueOperations.decrement(countKey)).thenReturn(0L);
        videoRoomService.removeParticipantOnDisconnect(boardId, userId);
        sessionsBacking.clear(); // deleteRoomKeys() would really wipe the sessions hash too

        // Rejoin looks exactly like a brand new join -- a fresh distinct participant.
        when(hashOperations.increment(connectionsKey, userId.toString(), 1L)).thenReturn(1L);
        when(valueOperations.increment(countKey)).thenReturn(1L);
        videoRoomService.join(boardId, caller());
        String rejoinSessionId = sessionsBacking.get(userId.toString());

        assertThat(rejoinSessionId).isNotBlank().isNotEqualTo(firstSessionId);
    }

    @Test
    void isCurrentSignalingSessionIsFalseWhenNoSessionHasEverBeenIssued() {
        assertThat(videoRoomService.isCurrentSignalingSession(boardId, userId, "anything")).isFalse();
    }

    // ---------------------------------------------------------------- mic/camera state

    @Test
    void updateParticipantStateUpdatesStoredFlagsAndBroadcasts() throws Exception {
        seedExistingParticipant(userId, "Ishan");

        videoRoomService.updateParticipantState(boardId, caller(), false, true);

        VideoParticipant stored = objectMapper.readValue(participantsBacking.get(userId.toString()), VideoParticipant.class);
        assertThat(stored.isMicEnabled()).isFalse();
        assertThat(stored.isCameraEnabled()).isTrue();
        assertThat(stored.userName()).isEqualTo("Ishan"); // untouched by the update
        verify(messagingTemplate).convertAndSend(eq("/topic/boards/" + boardId + "/video"), any(Object.class));
        verify(videoEventBroadcaster).broadcast(any());
    }

    @Test
    void updateParticipantStateIsRejectedWhenNotAnActiveParticipant() {
        assertThatThrownBy(() -> videoRoomService.updateParticipantState(boardId, caller(), false, false))
                .isInstanceOf(VideoRoomAccessDeniedException.class);

        verifyNoInteractions(videoEventBroadcaster);
        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    void updateParticipantStateRequiresBoardAccess() {
        doThrow(new BoardAccessDeniedException("no access")).when(boardAccessGuard).assertAccessible(boardId, userId);

        assertThatThrownBy(() -> videoRoomService.updateParticipantState(boardId, caller(), true, true))
                .isInstanceOf(BoardAccessDeniedException.class);

        verifyNoInteractions(videoEventBroadcaster, messagingTemplate);
    }

    @Test
    void updateParticipantStateCannotChangeSomeoneElsesFlags() throws Exception {
        // updateParticipantState only ever takes the caller's own UserPrincipal id --
        // there is no target-user parameter anywhere in its signature to spoof.
        seedExistingParticipant(otherUserId, "Priya");

        assertThatThrownBy(() -> videoRoomService.updateParticipantState(boardId, caller(), false, false))
                .isInstanceOf(VideoRoomAccessDeniedException.class);

        VideoParticipant stillUnchanged = objectMapper.readValue(participantsBacking.get(otherUserId.toString()), VideoParticipant.class);
        assertThat(stillUnchanged.isMicEnabled()).isTrue();
    }

    // ---------------------------------------------------------------- REST snapshot

    @Test
    void getRoomStateReportsInactiveWhenNoCallExists() {
        VideoRoomResponse response = videoRoomService.getRoomState(boardId, userId);

        assertThat(response.active()).isFalse();
        assertThat(response.participants()).isEmpty();
    }

    @Test
    void getRoomStateReportsActiveWithTheCurrentRosterWhenACallIsRunning() throws Exception {
        seedExistingParticipant(userId, "Ishan");
        seedExistingParticipant(otherUserId, "Priya");

        VideoRoomResponse response = videoRoomService.getRoomState(boardId, userId);

        assertThat(response.active()).isTrue();
        assertThat(response.participants()).extracting(VideoParticipant::userId)
                .containsExactlyInAnyOrder(userId, otherUserId);
    }
}
