package com.lvyou.model.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 旅行计划响应 — 直接对应 AI Agent 输出的 JSON 结构
 * <p>
 * Agent 输出格式:
 * <pre>
 * {
 *   "days": [
 *     { "day": 1, "theme": "...", "pois": [...], "distance": 120, "drive_time": 180 }
 *   ]
 * }
 * </pre>
 * <p>
 * 后端在此基础之上附加 from/to/days/preference 信息。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TravelPlanResponse {

    /** 出发城市 */
    private String from;

    /** 目的城市 */
    private String to;

    /** 总天数 */
    private Integer totalDays;

    /** 偏好 */
    private String preference;

    /** 用户想法（可选） */
    private String userIdea;

    /** 每日行程 */
    private List<DayPlan> days;

    /** 全局备选玩法方案 */
    private List<TripOption> options;

    /** 路线决策报告 */
    private RouteDecisionReport routeDecisionReport;

    /** 联网调研报告 */
    private ResearchReport researchReport;
}
