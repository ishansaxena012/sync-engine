package com.ishan.syncCanvas.video;

import com.ishan.syncCanvas.collaboration.exception.BoardAccessDeniedException;
import com.ishan.syncCanvas.collaboration.service.BoardAccessGuard;
import com.ishan.syncCanvas.common.response.ApiResponse;
import com.ishan.syncCanvas.security.user.UserPrincipal;
import com.ishan.syncCanvas.user.entity.User;
import com.ishan.syncCanvas.video.controller.VideoRoomController;
import com.ishan.syncCanvas.video.dto.IceServerConfig;
import com.ishan.syncCanvas.video.dto.IceServersResponse;
import com.ishan.syncCanvas.video.dto.VideoRoomResponse;
import com.ishan.syncCanvas.video.service.IceServerProvider;
import com.ishan.syncCanvas.video.service.VideoRoomService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VideoRoomControllerTest {

    @Mock
    private VideoRoomService videoRoomService;
    @Mock
    private BoardAccessGuard boardAccessGuard;
    @Mock
    private IceServerProvider iceServerProvider;

    private VideoRoomController controller() {
        return new VideoRoomController(videoRoomService, boardAccessGuard, iceServerProvider);
    }

    private final UUID boardId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UserPrincipal principal =
            UserPrincipal.create(User.builder().id(userId).name("Ishan").email("ishan@example.com").build());

    @Test
    void returnsInactiveStateRatherThanNotFoundWhenNoCallExists() {
        when(videoRoomService.getRoomState(boardId, userId))
                .thenReturn(new VideoRoomResponse(boardId, false, List.of()));

        ResponseEntity<ApiResponse<VideoRoomResponse>> response = controller().getRoomState(principal, boardId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getData().active()).isFalse();
    }

    @Test
    void returnsTheActiveRoomStateWhenACallIsRunning() {
        VideoRoomResponse active = new VideoRoomResponse(boardId, true, List.of());
        when(videoRoomService.getRoomState(boardId, userId)).thenReturn(active);

        ResponseEntity<ApiResponse<VideoRoomResponse>> response = controller().getRoomState(principal, boardId);

        assertThat(response.getBody().getData()).isSameAs(active);
    }

    @Test
    void unauthorizedBoardAccessPropagatesRatherThanBeingSwallowed() {
        doThrow(new BoardAccessDeniedException("no access"))
                .when(videoRoomService).getRoomState(boardId, userId);
        VideoRoomController controller = controller();

        assertThatThrownBy(() -> controller.getRoomState(principal, boardId))
                .isInstanceOf(BoardAccessDeniedException.class);
    }

    // ---------------------------------------------------------------- ICE server config

    @Test
    void authenticatedUserWithBoardAccessCanRetrieveIceServerConfiguration() {
        List<IceServerConfig> servers = List.of(new IceServerConfig(List.of("stun:stun.l.google.com:19302"), null, null));
        when(iceServerProvider.getIceServers()).thenReturn(servers);

        ResponseEntity<ApiResponse<IceServersResponse>> response = controller().getIceServers(principal, boardId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getData().iceServers()).isEqualTo(servers);
        verify(boardAccessGuard).assertAccessible(boardId, userId);
    }

    @Test
    void unauthorizedBoardAccessToIceServersIsRejectedBeforeQueryingTheProvider() {
        doThrow(new BoardAccessDeniedException("no access")).when(boardAccessGuard).assertAccessible(boardId, userId);
        VideoRoomController controller = controller();

        assertThatThrownBy(() -> controller.getIceServers(principal, boardId))
                .isInstanceOf(BoardAccessDeniedException.class);
        verifyNoInteractions(iceServerProvider);
    }

    @Test
    void iceServersDoNotRequireActiveVideoRoomMembership() {
        // A client needs ICE servers before it has ever joined the call, to construct
        // its RTCPeerConnection ahead of sending START/JOIN -- only board access, no
        // room-service interaction at all.
        when(iceServerProvider.getIceServers()).thenReturn(List.of());

        controller().getIceServers(principal, boardId);

        verifyNoInteractions(videoRoomService);
    }
}
