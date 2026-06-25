package com.lvyou.agent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 旅行规划请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TravelPlanRequest {

    /** 出发城市 */
    private String from;

    /** 目的城市 */
    private String to;

    /** 旅行天数 */
    private Integer days;

    /** 旅行偏好（如：历史文化、自然风光、美食、购物） */
    private String preference;

    /** 用户自定义想法（可选） */
    private String userIdea;

    /** 是否启用联网调研 */
    private Boolean onlineResearch;

    /** 联网调研上下文 */
    private String researchContext;

    /** 会话ID（可选，用于多轮对话） */
    private String sessionId;
}
