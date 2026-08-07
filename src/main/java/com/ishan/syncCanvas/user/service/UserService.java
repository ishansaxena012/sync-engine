package com.ishan.syncCanvas.user.service;

import com.ishan.syncCanvas.user.dto.UserProfileResponse;
import com.ishan.syncCanvas.user.entity.User;
import java.util.UUID;

public interface UserService {
    User findOrCreateGoogleUser(String googleId, String email, String name, String profilePicture);
    UserProfileResponse getUserProfile(UUID userId);
    User getUserById(UUID userId);
}
