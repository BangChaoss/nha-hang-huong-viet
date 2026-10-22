package com.huongViet.config;

import com.huongViet.auth.JwtService;
import com.huongViet.auth.RevokedTokenRepository;
import com.huongViet.auth.User;
import com.huongViet.auth.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

@Component
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketAuthInterceptor.class);

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final RevokedTokenRepository revokedTokenRepository;

    public WebSocketAuthInterceptor(JwtService jwtService,
                                  UserRepository userRepository,
                                  RevokedTokenRepository revokedTokenRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.revokedTokenRepository = revokedTokenRepository;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            String authHeader = accessor.getFirstNativeHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);

                String tokenHash = sha256(token);
                if (revokedTokenRepository.findByTokenHash(tokenHash).isEmpty() && jwtService.isTokenValid(token)) {
                    String userId = jwtService.extractUserId(token);
                    userRepository.findById(userId)
                        .filter(u -> u.getDeletedAt() == null && "ACTIVE".equals(u.getStatus()))
                        .ifPresent(user -> {
                            var authorities = List.of(
                                new SimpleGrantedAuthority(user.getRole().name()),
                                new SimpleGrantedAuthority("ROLE_" + user.getRole().name())
                            );
                            var auth = new UsernamePasswordAuthenticationToken(user, null, authorities);
                            accessor.setUser(auth);
                            logger.info("WebSocket authenticated user: id={}, username={}, role={}",
                                user.getId(), user.getUsername(), user.getRole());
                        });
                } else {
                    logger.warn("WebSocket CONNECT rejected: invalid or revoked token");
                }
            }
        }

        return message;
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 hashing failed", e);
        }
    }
}
