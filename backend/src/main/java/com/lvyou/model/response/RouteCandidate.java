package com.lvyou.model.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 路线候选方案。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class RouteCandidate {

    private String name;
    private String strategy;
    private String daysSummary;
    private Double totalDistance;
    private Integer totalDriveTime;
    private Integer score;
    private String tradeOff;
    private Boolean selected;
}
