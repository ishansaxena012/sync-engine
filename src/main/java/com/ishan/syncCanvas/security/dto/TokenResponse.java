package com.ishan.syncCanvas.security.dto;

import com.ishan.syncCanvas.user.dto.UserProfileResponse;

public record TokenResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        UserProfileResponse user
) {
    public TokenResponse(String accessToken, String refreshToken, UserProfileResponse user) {
        this(accessToken, refreshToken, "Bearer", user);
    }
}
