package com.katixo.hospital.realtime;

import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BoardWebSocketHandlerTest {

    private final BoardWebSocketHandler handler = new BoardWebSocketHandler();

    private WebSocketSession session(String key, boolean open) {
        WebSocketSession s = mock(WebSocketSession.class);
        Map<String, Object> attrs = new HashMap<>();
        attrs.put(BoardWebSocketHandler.KEY_ATTR, key);
        when(s.getAttributes()).thenReturn(attrs);
        when(s.isOpen()).thenReturn(open);
        return s;
    }

    @Test
    void registersAndUnregistersSessionsByKey() throws Exception {
        WebSocketSession s = session("tenantA:1", true);
        handler.afterConnectionEstablished(s);
        assertEquals(1, handler.sessionCount("tenantA:1"));

        handler.afterConnectionClosed(s, CloseStatus.NORMAL);
        assertEquals(0, handler.sessionCount("tenantA:1"));
    }

    @Test
    void broadcastReachesOnlyMatchingTenant() throws Exception {
        WebSocketSession a = session("tenantA:1", true);
        WebSocketSession b = session("tenantB:1", true);
        handler.afterConnectionEstablished(a);
        handler.afterConnectionEstablished(b);

        handler.broadcast("tenantA:1", "queue");

        verify(a).sendMessage(any(TextMessage.class));
        verify(b, never()).sendMessage(any(TextMessage.class));
    }

    @Test
    void broadcastSkipsClosedSessions() throws Exception {
        WebSocketSession closed = session("tenantA:1", false);
        handler.afterConnectionEstablished(closed);

        handler.broadcast("tenantA:1", "beds");

        verify(closed, never()).sendMessage(any(TextMessage.class));
    }
}
