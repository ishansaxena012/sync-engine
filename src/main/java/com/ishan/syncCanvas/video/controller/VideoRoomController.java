package com.ishan.syncCanvas.video.controller;

import com.ishan.syncCanvas.collaboration.service.BoardAccessGuard;
import com.ishan.syncCanvas.common.response.ApiResponse;
import com.ishan.syncCanvas.common.response.ResponseUtil;
import com.ishan.syncCanvas.security.user.UserPrincipal;
import com.ishan.syncCanvas.video.dto.IceServersResponse;
import com.ishan.syncCanvas.video.dto.VideoRoomResponse;
import com.ishan.syncCanvas.video.service.IceServerProvider;
import com.ishan.syncCanvas.video.service.VideoRoomService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Current video-room snapshot and static WebRTC configuration for a board.
 *
 * <p>Sits under the existing {@code /api/v1/boards} namespace, mirroring {@code
 * ChatHistoryController}. Always returns a response — an empty roster ({@code
 * active: false}) means no call is running, not a 404.
 */
@RestController
@RequestMapping("/api/v1/boards/{boardId}/video")
@RequiredArgsConstructor
public class VideoRoomController {

    private final VideoRoomService videoRoomService;
    private final BoardAccessGuard boardAccessGuard;
    private final IceServerProvider iceServerProvider;

    /** Lets a client that just opened the board learn whether a call is already active before subscribing to the live STOMP topic. */
    @GetMapping
    public ResponseEntity<ApiResponse<VideoRoomResponse>> getRoomState(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable UUID boardId) {

        return ResponseUtil.success(
                videoRoomService.getRoomState(boardId, userPrincipal.getId()),
                "Video room state fetched successfully",
                HttpStatus.OK);
    }

    /**
     * Static STUN/TURN configuration for the browser's {@code RTCPeerConnection}. Board
     * access only, not active room membership — a client needs this before it has ever
     * joined the call, to construct its peer connection ahead of sending START/JOIN.
     */
    @GetMapping("/ice-servers")
    public ResponseEntity<ApiResponse<IceServersResponse>> getIceServers(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable UUID boardId) {

        boardAccessGuard.assertAccessible(boardId, userPrincipal.getId());
        return ResponseUtil.success(
                new IceServersResponse(iceServerProvider.getIceServers()),
                "ICE server configuration fetched successfully",
                HttpStatus.OK);
    }
}
