package com.example.service;

import com.example.entity.User;
import com.example.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class UserService {

    private static final List<String> AVATARS = List.of("🃏", "♠️", "♥️", "♦️", "♣️", "🎯", "🎲", "🏆", "🧮", "💰", "⭐", "🎪", "🎨", "🎭", "🎵", "🌈", "🍀", "🔥", "💎", "🦊");

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User register(String username) {
        User user = userRepository.findByUsername(username).orElse(null);
        if (user == null) {
            user = new User();
            user.setUsername(username);
            user.setAvatar(AVATARS.get((int)(Math.random() * AVATARS.size())));
            user.setCreatedAt(LocalDateTime.now());
        }
        user.setToken(UUID.randomUUID().toString());
        return userRepository.save(user);
    }

    public User findByToken(String token) {
        return userRepository.findByToken(token).orElse(null);
    }

    public User findById(Long id) {
        return userRepository.findById(id).orElse(null);
    }

    public User updateUser(User user) {
        return userRepository.save(user);
    }

    public Page<User> listUsers(String query, Pageable pageable) {
        if (query == null || query.isBlank()) {
            return userRepository.findAll(pageable);
        }
        return userRepository.findByUsernameContainingIgnoreCase(query.trim(), pageable);
    }

    public User createUser(String username, String avatar) {
        User user = new User();
        user.setUsername(username.trim());
        user.setAvatar(avatar != null && !avatar.isBlank() ? avatar.trim() : AVATARS.get((int)(Math.random() * AVATARS.size())));
        user.setToken(UUID.randomUUID().toString());
        user.setCreatedAt(LocalDateTime.now());
        return userRepository.save(user);
    }

    public User updateUser(Long id, String username, String avatar, String token, String activeRoomCode) {
        User user = findById(id);
        if (user == null) return null;
        if (username != null && !username.isBlank()) user.setUsername(username.trim());
        user.setAvatar(avatar != null && !avatar.isBlank() ? avatar.trim() : null);
        if (token != null && !token.isBlank()) user.setToken(token.trim());
        user.setActiveRoomCode(activeRoomCode != null && !activeRoomCode.isBlank() ? activeRoomCode.trim() : null);
        return userRepository.save(user);
    }

    public void deleteUser(Long id) {
        userRepository.deleteById(id);
    }
}
