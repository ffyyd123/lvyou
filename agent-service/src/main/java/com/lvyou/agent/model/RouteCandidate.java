package com.lvyou.agent.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 路线候选方案，用于解释 AI 为什么选择当前路线。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class RouteCandidate {

    /** 候选方案名称 */
    private String name;

    /** 策略类型 */
    private String strategy;

    /** 每天的路线摘要 */
    private String daysSummary;

    /** 总移动距离，单位公里 */
    private Double totalDistance;

    /** 总驾车时间，单位分钟 */
    private Integer totalDriveTime;

    /** 方案评分，0-100 */
    private Integer score;

    /** 取舍说明 */
    private String tradeOff;

    /** 是否为最终采用方案 */
    private Boolean selected;
}
