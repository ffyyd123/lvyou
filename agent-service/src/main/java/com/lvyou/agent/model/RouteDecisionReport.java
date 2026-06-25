package com.lvyou.agent.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 路线决策报告，记录候选路线、评分和最终取舍理由。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class RouteDecisionReport {

    /** 最终采用的策略 */
    private String selectedStrategy;

    /** 总结说明 */
    private String summary;

    /** 最终方案总距离，单位公里 */
    private Double totalDistance;

    /** 最终方案总移动时间，单位分钟 */
    private Integer totalDriveTime;

    /** 最终方案评分 */
    private Integer routeScore;

    /** 优化说明 */
    private String optimizationNotes;

    /** 候选路线列表 */
    private List<RouteCandidate> candidates;
}
