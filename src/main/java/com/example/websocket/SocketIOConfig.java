package com.example.websocket;

import com.corundumstudio.socketio.*;
import com.example.service.UserService;
import com.example.entity.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

import java.util.Map;

@org.springframework.context.annotation.Configuration
@ConditionalOnProperty(value = "socketio.enabled", havingValue = "true", matchIfMissing = true)
public class SocketIOConfig {

    @Value("${socketio.port:8089}")
    private int socketioPort;

    @Bean
    public SocketIOServer socketIOServer(UserService userService, SocketIOEventListener eventListener) {
        Configuration config = new Configuration();
        config.setHostname("0.0.0.0");
        config.setPort(socketioPort);
        config.setOrigin("*");

        config.setAuthorizationListener(data -> {
            String token = data.getSingleUrlParam("token");
            if (token == null || token.isEmpty()) {
                return new AuthorizationResult(false);
            }
            User user = userService.findByToken(token);
            if (user == null) {
                return new AuthorizationResult(false);
            }
            return new AuthorizationResult(true, Map.of(
                "userId", user.getId(),
                "username", user.getUsername(),
                "avatar", user.getAvatar()
            ));
        });

        SocketIOServer server = new SocketIOServer(config);
        server.addListeners(eventListener);
        server.start();
        return server;
    }
}
