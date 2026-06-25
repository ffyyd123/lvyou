package com.lvyou.agent.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * AI Agent 输出的旅行路线结果
 * <p>
 * 输出格式（顶层直接是 days 数组）:
 * <pre>
 * {
 *   "days": [
 *     {
 *       "day": 1,
 *       "theme": "历史文化探索",
 *       "pois": [
 *         { "name": "悬空寺", "lat": 39.67, "lng": 113.71, "stayTime": 120, "description": "..." }
 *       ],
 *       "distance": 120.5,
 *       "driveTime": 180
 *     }
 *   ]
 * }
 * </pre>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TravelPlanResult {

    /** 每天的行程计划 */
    private List<DayPlan> days;

    /** 全局备选玩法方案 */
    private List<TripOption> options;

    /** 路线决策报告 */
    private RouteDecisionReport routeDecisionReport;

    /** 来源城市（内部使用，非AI输出） */
    private String from;

    /** 目的城市（内部使用，非AI输出） */
    private String to;

    /** 总天数（内部使用，非AI输出） */
    private Integer totalDays;

    /** 用户偏好（内部使用，非AI输出） */
    private String preference;
}
