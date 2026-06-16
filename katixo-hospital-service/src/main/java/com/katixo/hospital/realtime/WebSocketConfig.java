package com.katixo.hospital.realtime;

import com.katixo.hospital.auth.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * Registers the real-time board WebSocket at {@code /ws/board}. Raw text frames
 * (no STOMP) keep the Flutter web client dependency-light. The handshake is
 * JWT-authenticated via the query-param interceptor.
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final BoardWebSocketHandler boardWebSocketHandler;
    private final JwtTokenProvider jwtTokenProvider;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(boardWebSocketHandler, "/ws/board")
                .addInterceptors(new BoardHandshakeInterceptor(jwtTokenProvider))
                .setAllowedOriginPatterns("*");
    }
}
