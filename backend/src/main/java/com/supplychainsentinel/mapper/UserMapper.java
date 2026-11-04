package com.supplychainsentinel.mapper;

import com.supplychainsentinel.dto.UserDto;
import com.supplychainsentinel.entity.User;

/**
 * Converts between {@link User} entities and {@link UserDto} DTOs.
 */
public final class UserMapper {

    private UserMapper() { }

    public static UserDto toDto(User user) {
        return new UserDto(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getDisplayName(),
                user.getRoleNames(),
                user.isEnabled(),
                user.getCreatedAt()
        );
    }
}
