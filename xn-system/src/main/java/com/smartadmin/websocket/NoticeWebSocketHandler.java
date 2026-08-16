package com.smartadmin.websocket;

import com.smartadmin.security.JwtUtil;
import com.smartadmin.service.TokenBlacklistService;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class NoticeWebSocketHandler extends TextWebSocketHandler {

    private final JwtUtil jwtUtil;
    private final NoticeSessionHub sessionHub;
    private final ObjectMapper objectMapper;
    private final TokenBlacklistService tokenBlacklistService;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String token = resolveToken(session);
        if (!StringUtils.hasText(token) || !jwtUtil.validateToken(token)) {
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("unauthorized"));
            return;
        }
        Long userId = jwtUtil.getUserId(token);
        if (userId == null) {
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("invalid token"));
            return;
        }
        // 被踢用户的浏览器会自动重连，这里必须复核黑名单，否则会重新出现在在线列表里
        if (tokenBlacklistService.isRevoked(token, userId, jwtUtil.getIssuedAtMillis(token))) {
            session.sendMessage(
                    new TextMessage(
                            objectMapper.writeValueAsString(
                                    Map.of(
                                            "type", "auth:force-logout",
                                            "reason", "kicked",
                                            "message", "登录状态已失效，请重新登录"))));
            session.close(NoticeSessionHub.KICKED_STATUS);
            return;
        }
        sessionHub.register(userId, session);
        session.sendMessage(
                new TextMessage(
                        objectMapper.writeValueAsString(
                                Map.of("type", "connected", "userId", userId))));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message)
            throws Exception {
        String payload = message.getPayload();
        if (!StringUtils.hasText(payload)) {
            return;
        }
        JsonNode node = objectMapper.readTree(payload);
        String type = node.path("type").asText("");
        if ("ping".equalsIgnoreCase(type)) {
            session.sendMessage(
                    new TextMessage(objectMapper.writeValueAsString(Map.of("type", "pong"))));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessionHub.unregister(session);
    }

    private String resolveToken(WebSocketSession session) {
        URI uri = session.getUri();
        if (uri == null || uri.getQuery() == null) {
            return null;
        }
        for (String part : uri.getQuery().split("&")) {
            String[] kv = part.split("=", 2);
            if (kv.length == 2 && "token".equals(kv[0])) {
                return URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
            }
        }
        return null;
    }
}
