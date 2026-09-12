package com.ishan.syncCanvas.video.controller;

import com.ishan.syncCanvas.common.response.ApiResponse;
import com.ishan.syncCanvas.common.response.ResponseUtil;
import com.ishan.syncCanvas.security.user.UserPrincipal;
import com.ishan.syncCanvas.video.dto.VideoRoomResponse;
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
 * Current video-room snapshot for a board — lets a client that just opened the board
 * (or a page reload) learn whether a call is already active before subscribing to the
 * live STOMP topic, e.g. to render a "join call" affordance immediately.
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

    @GetMapping
    public ResponseEntity<ApiResponse<VideoRoomResponse>> getRoomState(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable UUID boardId) {

        return ResponseUtil.success(
                videoRoomService.getRoomState(boardId, userPrincipal.getId()),
                "Video room state fetched successfully",
                HttpStatus.OK);
    }
}
