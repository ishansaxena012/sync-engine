package com.ishan.syncCanvas.user.dto;

import java.time.Instant;
import java.util.UUID;

public record UserProfileResponse(
        UUID id,
        String googleId,
        String name,
        String email,
        String profilePicture,
        Instant createdAt,
        Instant updatedAt
) {}
