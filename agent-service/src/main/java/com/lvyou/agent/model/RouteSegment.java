package com.lvyou.agent.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 相邻景点之间的路线分段，用于前端明确展示前后顺序。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class RouteSegment {

    private Integer index;

    private String from;

    private String to;

    private Double distance;

    @JsonProperty("drive_time")
    private Integer driveTime;

    private String summary;
}
