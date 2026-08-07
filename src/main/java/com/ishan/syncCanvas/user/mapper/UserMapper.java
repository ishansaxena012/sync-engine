package com.ishan.syncCanvas.user.mapper;

import com.ishan.syncCanvas.user.dto.UserProfileResponse;
import com.ishan.syncCanvas.user.entity.User;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    public UserProfileResponse toResponse(User user) {
        if (user == null) return null;
        return new UserProfileResponse(
                user.getId(),
                user.getGoogleId(),
                user.getName(),
                user.getEmail(),
                user.getProfilePicture(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}
