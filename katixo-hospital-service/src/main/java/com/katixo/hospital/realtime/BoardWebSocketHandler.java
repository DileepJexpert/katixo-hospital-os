package com.katixo.hospital.realtime;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Real-time board feed. Holds the open WebSocket sessions per tenant:branch and
 * pushes one-line topic "nudges" (e.g. {@code {"topic":"queue"}}); the client
 * re-fetches the relevant REST endpoint on receipt. Tenant isolation is enforced
 * by the handshake interceptor, which stamps each session with its own key — a
 * broadcast only reaches sessions for the same tenant:branch.
 */
@Component
@Slf4j
public class BoardWebSocketHandler extends TextWebSocketHandler {

    /** Session attribute holding the {@code tenantId:branchId} routing key. */
    static final String KEY_ATTR = "boardKey";

    private final Map<String, Set<WebSocketSession>> sessionsByKey = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String key = (String) session.getAttributes().get(KEY_ATTR);
        if (key == null) {
            return;
        }
        sessionsByKey.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet()).add(session);
        log.debug("Board WS connected for {} ({} sessions)", key, sessionCount(key));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String key = (String) session.getAttributes().get(KEY_ATTR);
        if (key == null) {
            return;
        }
        Set<WebSocketSession> set = sessionsByKey.get(key);
        if (set != null) {
            set.remove(session);
            if (set.isEmpty()) {
                sessionsByKey.remove(key);
            }
        }
    }

    /** Pushes a topic nudge to every open session for a tenant:branch key. Fail-soft. */
    public void broadcast(String key, String topic) {
        Set<WebSocketSession> set = sessionsByKey.get(key);
        if (set == null || set.isEmpty()) {
            return;
        }
        TextMessage msg = new TextMessage("{\"topic\":\"" + topic + "\"}");
        for (WebSocketSession s : set) {
            try {
                if (s.isOpen()) {
                    s.sendMessage(msg);
                }
            } catch (IOException | RuntimeException e) {
                log.debug("Board WS send failed: {}", e.getMessage());
            }
        }
    }

    /** Open session count for a key (test/diagnostics). */
    int sessionCount(String key) {
        Set<WebSocketSession> set = sessionsByKey.get(key);
        return set == null ? 0 : set.size();
    }
}
