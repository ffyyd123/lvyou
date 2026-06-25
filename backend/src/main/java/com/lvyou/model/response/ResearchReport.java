package com.lvyou.model.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 联网调研报告。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResearchReport {

    private Boolean enabled;
    private String status;
    private String destination;
    private String preference;
    private LocalDateTime generatedAt;
    private Integer rawSourceCount;
    private Integer cleanedSourceCount;
    private Integer targetKeywordCount;
    private Integer targetSourcesPerKeyword;
    private Integer targetEffectiveSourceCount;
    private String evidenceSummary;
    private String trustPolicy;

    @Builder.Default
    private Map<String, List<String>> keywordGroups = Map.of();

    @Builder.Default
    private List<ResearchSource> sources = new ArrayList<>();

    @Builder.Default
    private List<String> searchRounds = new ArrayList<>();

    @Builder.Default
    private List<ResearchTrace> traces = new ArrayList<>();
}
