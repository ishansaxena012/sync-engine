package com.ishan.syncCanvas.chat.controller;

import com.ishan.syncCanvas.chat.dto.ChatMessageResponse;
import com.ishan.syncCanvas.chat.service.ChatService;
import com.ishan.syncCanvas.common.response.ApiResponse;
import com.ishan.syncCanvas.common.response.ResponseUtil;
import com.ishan.syncCanvas.security.user.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Chat history for a board, so a client opening (or reopening) a board can populate the
 * panel without replaying anything through the canvas operation stream.
 *
 * <p>Sits under the existing {@code /api/v1/boards} namespace and returns the same
 * {@code ApiResponse<Page<...>>} envelope as {@code BoardController#getBoards}, so the
 * client's existing pagination handling applies unchanged.
 *
 * <p>Page 0 is the newest messages; within a page they run oldest → newest. Page size is
 * capped in {@link ChatService} regardless of what the query string asks for.
 */
@RestController
@RequestMapping("/api/v1/boards/{boardId}/chat")
@RequiredArgsConstructor
public class ChatHistoryController {

    private final ChatService chatService;

    @GetMapping
    public ResponseEntity<ApiResponse<Page<ChatMessageResponse>>> getHistory(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable UUID boardId,
            Pageable pageable) {

        return ResponseUtil.success(
                chatService.history(boardId, userPrincipal.getId(), pageable),
                "Chat history fetched successfully",
                HttpStatus.OK);
    }
}
