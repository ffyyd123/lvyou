package com.lvyou.agent.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 单日行程计划
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class DayPlan {

    /** 第几天 */
    private Integer day;

    /** 当日主题 */
    private String theme;

    /** 当日景点列表 */
    private List<PoiInfo> pois;

    /** 相邻景点之间的路线分段 */
    private List<RouteSegment> segments;

    /** 当日备选玩法 */
    private List<TripOption> options;

    /** 从早到晚的完整衣食住行安排 */
    private List<DailyScheduleItem> schedule;

    /** 当晚住宿建议 */
    private StayPlan stay;

    /** 当日穿衣建议 */
    private String clothingTips;

    /** 当日交通总建议 */
    private String dailyTransport;

    /** 当日预算提示 */
    private String budgetHint;

    /** 当日总行车距离（公里） */
    private Double distance;

    /** 当日总驾车时间（分钟） */
    @JsonProperty("drive_time")
    private Integer driveTime;
}
