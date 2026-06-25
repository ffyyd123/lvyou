package com.lvyou.agent.memory;

import com.lvyou.agent.model.TravelPlanResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 旅行记忆管理器 — 轻量内存实现
 * <p>
 * 禁止使用数据库，所有数据存储在内存 Map 中。
 * <p>
 * 保存内容：
 * - 用户偏好（旅行类型、节奏）
 * - 当前会话路线
 * - 对话历史记录
 * <p>
 * 线程安全：使用 ConcurrentHashMap
 */
@Slf4j
@Component
public class TravelMemory {

    /** 会话存储: sessionId -> SessionData */
    private final Map<String, SessionData> sessions = new ConcurrentHashMap<>();

    /**
     * 获取或创建会话
     */
    public String getOrCreateSession(String existingSessionId) {
        if (existingSessionId != null && sessions.containsKey(existingSessionId)) {
            return existingSessionId;
        }
        String sessionId = UUID.randomUUID().toString().substring(0, 8);
        sessions.put(sessionId, new SessionData(sessionId));
        log.info("🧠 [Memory] 创建新会话: {}", sessionId);
        return sessionId;
    }

    /**
     * 更新用户偏好
     */
    public void updatePreference(String sessionId, String preference, Integer days) {
        SessionData session = sessions.get(sessionId);
        if (session != null) {
            session.preference = preference;
            session.days = days;
            log.info("🧠 [Memory] 更新偏好: session={}, preference={}, days={}", sessionId, preference, days);
        }
    }

    /**
     * 获取用户偏好
     */
    public String getPreference(String sessionId) {
        SessionData session = sessions.get(sessionId);
        return session != null ? session.preference : null;
    }

    /**
     * 获取天数
     */
    public Integer getDays(String sessionId) {
        SessionData session = sessions.get(sessionId);
        return session != null ? session.days : null;
    }

    /**
     * 保存旅行路线结果
     */
    public void savePlan(String sessionId, TravelPlanResult result) {
        SessionData session = sessions.get(sessionId);
        if (session != null) {
            session.currentPlan = result;
            log.info("🧠 [Memory] 保存路线: session={}, days={}", sessionId,
                    result.getDays() != null ? result.getDays().size() : 0);
        }
    }

    /**
     * 获取当前路线
     */
    public TravelPlanResult getCurrentPlan(String sessionId) {
        SessionData session = sessions.get(sessionId);
        return session != null ? session.currentPlan : null;
    }

    /**
     * 记录对话
     */
    public void recordConversation(String sessionId, String role, String content) {
        SessionData session = sessions.get(sessionId);
        if (session != null) {
            session.conversations.add(new Conversation(role, content));
            // 限制对话历史长度（保留最近50条）
            if (session.conversations.size() > 50) {
                session.conversations.remove(0);
            }
        }
    }

    /**
     * 获取对话历史摘要
     */
    public String getConversationSummary(String sessionId) {
        SessionData session = sessions.get(sessionId);
        if (session == null || session.conversations.isEmpty()) {
            return "暂无历史对话。";
        }
        StringBuilder sb = new StringBuilder();
        // 只取最近10条
        int start = Math.max(0, session.conversations.size() - 10);
        for (int i = start; i < session.conversations.size(); i++) {
            Conversation c = session.conversations.get(i);
            sb.append(String.format("[%s]: %s\n", c.role,
                    c.content.length() > 200 ? c.content.substring(0, 200) + "..." : c.content));
        }
        return sb.toString();
    }

    /**
     * 获取面向当前目的地的会话摘要。
     *
     * 换目的地时，只保留偏好和天数，避免旧路线、旧餐厅、旧酒店污染新规划。
     */
    public String getDestinationAwareSummary(String sessionId, String destination) {
        SessionData session = sessions.get(sessionId);
        if (session == null) {
            return "暂无历史对话。";
        }
        String currentDestination = session.currentPlan == null ? null : session.currentPlan.getTo();
        if (currentDestination == null || destination == null || !samePlace(currentDestination, destination)) {
            return String.format("历史偏好：%s；常用天数：%s。旧目的地路线已忽略，不能复用旧景点、餐厅、酒店或路线。",
                    session.preference == null ? "未记录" : session.preference,
                    session.days == null ? "未记录" : session.days + "天");
        }
        return getConversationSummary(sessionId);
    }

    /**
     * 清除会话
     */
    public void clearSession(String sessionId) {
        sessions.remove(sessionId);
        log.info("🧠 [Memory] 清除会话: {}", sessionId);
    }

    private boolean samePlace(String first, String second) {
        String a = normalizePlaceName(first);
        String b = normalizePlaceName(second);
        return !a.isBlank() && !b.isBlank() && (a.contains(b) || b.contains(a));
    }

    private String normalizePlaceName(String value) {
        if (value == null) {
            return "";
        }
        return value.trim()
                .replace("特别行政区", "")
                .replace("维吾尔自治区", "")
                .replace("壮族自治区", "")
                .replace("回族自治区", "")
                .replace("自治区", "")
                .replace("省", "")
                .replace("市", "");
    }

    // ========== 内部数据结构 ==========

    static class SessionData {
        String sessionId;
        String preference;
        Integer days;
        TravelPlanResult currentPlan;
        List<Conversation> conversations = new ArrayList<>();

        SessionData(String sessionId) {
            this.sessionId = sessionId;
        }
    }

    static class Conversation {
        String role;
        String content;

        Conversation(String role, String content) {
            this.role = role;
            this.content = content;
        }
    }
}
