package com.lvyou.agent.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 当晚住宿建议。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class StayPlan {

    /** 推荐住宿区域 */
    private String area;

    /** 酒店类型建议 */
    private String hotelType;

    /** 推荐理由 */
    private String reason;

    /** 入住提醒 */
    private String checkInTip;

    /** 周边交通建议 */
    private String transportTip;
}
