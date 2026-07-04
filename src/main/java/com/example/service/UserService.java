package com.example.service;

import com.example.entity.User;
import com.example.repository.UserRepository;
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
}
