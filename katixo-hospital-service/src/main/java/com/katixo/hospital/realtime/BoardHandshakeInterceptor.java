package com.katixo.hospital.realtime;

import com.katixo.hospital.auth.JwtClaims;
import com.katixo.hospital.auth.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

/**
 * Authenticates the board WebSocket handshake from a {@code ?token=<JWT>} query
 * param (browsers can't set Authorization headers on a WS upgrade) and stamps
 * the session with its {@code tenantId:branchId} routing key so broadcasts stay
 * tenant-isolated. Rejects the handshake when the token is missing or invalid.
 */
@Slf4j
@RequiredArgsConstructor
public class BoardHandshakeInterceptor implements HandshakeInterceptor {

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String token = UriComponentsBuilder.fromUri(request.getURI()).build()
                .getQueryParams().getFirst("token");
        if (token == null || token.isBlank() || !jwtTokenProvider.validateToken(token)) {
            log.warn("Board WS handshake rejected: missing or invalid token");
            return false;
        }
        JwtClaims claims = jwtTokenProvider.getClaimsFromToken(token);
        attributes.put(BoardWebSocketHandler.KEY_ATTR, claims.getTenantId() + ":" + claims.getBranchId());
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // no-op
    }
}
