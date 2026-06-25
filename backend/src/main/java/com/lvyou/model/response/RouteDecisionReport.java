package com.lvyou.model.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 路线决策报告。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class RouteDecisionReport {

    private String selectedStrategy;
    private String summary;
    private Double totalDistance;
    private Integer totalDriveTime;
    private Integer routeScore;
    private String optimizationNotes;
    private List<RouteCandidate> candidates;
}
