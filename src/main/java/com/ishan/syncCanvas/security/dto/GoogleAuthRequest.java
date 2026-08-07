package com.ishan.syncCanvas.security.dto;

import jakarta.validation.constraints.NotBlank;

public record GoogleAuthRequest(
        @NotBlank(message = "ID Token is required")
        String idToken
) {}
