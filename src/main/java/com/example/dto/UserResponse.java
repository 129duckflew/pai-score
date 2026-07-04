package com.example.dto;

public class UserResponse {
    private Long id;
    private String username;
    private String token;
    private String avatar;

    public UserResponse(Long id, String username, String token, String avatar) {
        this.id = id;
        this.username = username;
        this.token = token;
        this.avatar = avatar;
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getToken() { return token; }
    public String getAvatar() { return avatar; }
}
