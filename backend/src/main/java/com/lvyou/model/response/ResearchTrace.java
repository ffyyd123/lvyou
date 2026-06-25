package com.lvyou.model.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 联网调研运行轨迹，用于前端展示每一步真实执行过程。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResearchTrace {

    private Integer order;
    private String direction;
    private String round;
    private String keyword;
    private String executedKeyword;
    private String purpose;
    private String platformHint;
    private String provider;
    private String status;
    private Integer rawCount;
    private Integer cleanedCount;
    private String message;

    @Builder.Default
    private List<ResearchSource> rawSources = new ArrayList<>();

    @Builder.Default
    private List<ResearchSource> cleanedSources = new ArrayList<>();
}
