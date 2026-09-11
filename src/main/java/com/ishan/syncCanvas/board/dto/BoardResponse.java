package com.ishan.syncCanvas.board.dto;

import com.ishan.syncCanvas.board.entity.Visibility;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

import com.ishan.syncCanvas.user.dto.UserProfileResponse;

// response object should be
@Getter
@Builder
public class BoardResponse {
    private UUID id;
    private String title;
    private UserProfileResponse owner;
    private Visibility visibility;
    private Instant createdAt;
    private Instant updatedAt;
    /** Current authoritative sequence — the anchor a client needs to request only the
     *  operations it's missing on reconnect, instead of re-fetching every object. */
    private long sequence;
}