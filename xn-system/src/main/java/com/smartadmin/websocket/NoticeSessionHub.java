package com.smartadmin.websocket;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class NoticeSessionHub {

    private static final Logger log = LoggerFactory.getLogger(NoticeSessionHub.class);

    /** 强制下线专用关闭码；4000-4999 为应用私有区间，浏览器可原样拿到 code 与 reason。 */
    public static final CloseStatus KICKED_STATUS = new CloseStatus(4401, "kicked");

    private final ObjectMapper objectMapper;

    /** userId -> sessions（同一用户可多端） */
    private final Map<Long, Set<WebSocketSession>> sessionsByUser = new ConcurrentHashMap<>();

    /** 在线用户元信息：连接数、最早连接时间(epoch ms)、客户端 IP */
    public record OnlineUserMeta(Long userId, int sessionCount, long connectedAt, String ip) {}

    public void register(Long userId, WebSocketSession session) {
        sessionsByUser.computeIfAbsent(userId, k -> new CopyOnWriteArraySet<>()).add(session);
        session.getAttributes().put("userId", userId);
        session.getAttributes().put("connectedAt", System.currentTimeMillis());
        session.getAttributes().put("ip", resolveIp(session));
    }

    private String resolveIp(WebSocketSession session) {
        InetSocketAddress address = session.getRemoteAddress();
        if (address != null && address.getAddress() != null) {
            return address.getAddress().getHostAddress();
        }
        return "-";
    }

    /** 当前在线用户列表（按用户聚合多端会话） */
    public List<OnlineUserMeta> getOnlineUsers() {
        List<OnlineUserMeta> list = new ArrayList<>();
        for (Map.Entry<Long, Set<WebSocketSession>> entry : sessionsByUser.entrySet()) {
            long earliest = Long.MAX_VALUE;
            String ip = null;
            int count = 0;
            for (WebSocketSession session : entry.getValue()) {
                if (!session.isOpen()) {
                    continue;
                }
                count++;
                Object connectedAt = session.getAttributes().get("connectedAt");
                if (connectedAt instanceof Long ts && ts < earliest) {
                    earliest = ts;
                }
                Object ipValue = session.getAttributes().get("ip");
                if (ip == null && ipValue instanceof String s) {
                    ip = s;
                }
            }
            if (count == 0) {
                continue;
            }
            list.add(
                    new OnlineUserMeta(
                            entry.getKey(),
                            count,
                            earliest == Long.MAX_VALUE ? 0 : earliest,
                            ip == null ? "-" : ip));
        }
        return list;
    }

    /** 强制下线：关闭该用户全部 WebSocket 会话，返回关闭的连接数 */
    public int kickUser(Long userId) {
        return kickUser(userId, null);
    }

    /** 强制下线：先向该用户各端推送 payload，再关闭全部 WebSocket 会话，返回关闭的连接数 */
    public int kickUser(Long userId, Object payload) {
        Set<WebSocketSession> sessions = sessionsByUser.remove(userId);
        if (sessions == null || sessions.isEmpty()) {
            return 0;
        }
        TextMessage notice =
                payload == null ? null : new TextMessage(objectMapper.writeValueAsString(payload));
        int closed = 0;
        for (WebSocketSession session : sessions) {
            if (notice != null) {
                sendSafe(session, notice);
            }
            try {
                if (session.isOpen()) {
                    session.close(KICKED_STATUS);
                    closed++;
                }
            } catch (IOException e) {
                log.debug("ws kick close failed: {}", session.getId());
            }
        }
        return closed;
    }

    public void unregister(WebSocketSession session) {
        Object raw = session.getAttributes().get("userId");
        if (!(raw instanceof Long userId)) {
            return;
        }
        Set<WebSocketSession> set = sessionsByUser.get(userId);
        if (set != null) {
            set.remove(session);
            if (set.isEmpty()) {
                sessionsByUser.remove(userId);
            }
        }
    }

    public void sendToUser(Long userId, Object payload) {
        Set<WebSocketSession> set = sessionsByUser.get(userId);
        if (set == null || set.isEmpty()) {
            return;
        }
        TextMessage message = new TextMessage(objectMapper.writeValueAsString(payload));
        for (WebSocketSession session : set) {
            sendSafe(session, message);
        }
    }

    public void broadcast(Object payload) {
        TextMessage message = new TextMessage(objectMapper.writeValueAsString(payload));
        for (Set<WebSocketSession> set : sessionsByUser.values()) {
            for (WebSocketSession session : set) {
                sendSafe(session, message);
            }
        }
    }

    private void sendSafe(WebSocketSession session, TextMessage message) {
        if (session == null || !session.isOpen()) {
            return;
        }
        try {
            synchronized (session) {
                session.sendMessage(message);
            }
        } catch (IOException e) {
            log.debug("ws send failed: {}", session.getId());
        }
    }
}
