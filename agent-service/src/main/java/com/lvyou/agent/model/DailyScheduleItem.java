package com.lvyou.agent.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 单日从早到晚的具体安排，覆盖餐饮、交通、景点、休息和住宿。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class DailyScheduleItem {

    /** 时间段，例如 08:30-09:10 */
    @JsonProperty("time_range")
    private String timeRange;

    /** 时段，例如 早上、上午、中午、下午、傍晚、晚上、夜间 */
    private String period;

    /** 类型，例如 交通、早餐、景点、午餐、休息、晚餐、住宿、提醒 */
    private String type;

    /** 安排标题 */
    private String title;

    /** 地点名称或区域 */
    private String location;

    /** 具体说明 */
    private String description;

    /** 交通方式或衔接建议 */
    private String transport;

    /** 建议持续时间（分钟） */
    @JsonProperty("duration_minutes")
    private Integer durationMinutes;

    /** 费用提示 */
    @JsonProperty("cost_hint")
    private String costHint;

    /** 出行提醒 */
    private String tips;

    /** 可选纬度 */
    private Double lat;

    /** 可选经度 */
    private Double lng;
}
