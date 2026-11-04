package com.supplychainsentinel.service;

import com.supplychainsentinel.dto.CreateUserRequest;
import com.supplychainsentinel.dto.UpdateUserRequest;
import com.supplychainsentinel.dto.UserDto;
import com.supplychainsentinel.entity.Role;
import com.supplychainsentinel.entity.User;
import com.supplychainsentinel.exception.AuthException;
import com.supplychainsentinel.mapper.UserMapper;
import com.supplychainsentinel.repository.RoleRepository;
import com.supplychainsentinel.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * User management business logic (ADMIN operations).
 */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public List<UserDto> getAllUsers() {
        return userRepository.findAll().stream()
                .map(UserMapper::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public UserDto getUserById(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new AuthException("User not found", HttpStatus.NOT_FOUND));
        return UserMapper.toDto(user);
    }

    @Transactional
    public UserDto createUser(CreateUserRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new AuthException("Username already exists", HttpStatus.CONFLICT);
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new AuthException("Email already exists", HttpStatus.CONFLICT);
        }

        User user = new User();
        user.setUsername(request.username());
        user.setEmail(request.email());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setDisplayName(request.displayName());
        user.setEnabled(true);
        user.setAccountLocked(false);
        user.setFailedLoginAttempts(0);

        if (request.roles() != null && !request.roles().isEmpty()) {
            user.setRoles(resolveRoles(request.roles()));
        }

        userRepository.save(user);
        return UserMapper.toDto(user);
    }

    @Transactional
    public UserDto updateUser(UUID id, UpdateUserRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new AuthException("User not found", HttpStatus.NOT_FOUND));

        if (request.email() != null) {
            user.setEmail(request.email());
        }
        if (request.displayName() != null) {
            user.setDisplayName(request.displayName());
        }
        if (request.enabled() != null) {
            user.setEnabled(request.enabled());
        }
        if (request.roles() != null) {
            user.setRoles(resolveRoles(request.roles()));
        }
        if (request.password() != null) {
            user.setPasswordHash(passwordEncoder.encode(request.password()));
        }

        userRepository.save(user);
        return UserMapper.toDto(user);
    }

    @Transactional
    public void deleteUser(UUID id) {
        if (!userRepository.existsById(id)) {
            throw new AuthException("User not found", HttpStatus.NOT_FOUND);
        }
        userRepository.deleteById(id);
    }

    // ── Helpers ──────────────────────────────────────────────

    private Set<Role> resolveRoles(Set<String> roleNames) {
        Set<Role> roles = new HashSet<>();
        for (String name : roleNames) {
            Role role = roleRepository.findByName(name)
                    .orElseThrow(() -> new AuthException(
                            "Unknown role: " + name, HttpStatus.BAD_REQUEST));
            roles.add(role);
        }
        return roles;
    }
}
