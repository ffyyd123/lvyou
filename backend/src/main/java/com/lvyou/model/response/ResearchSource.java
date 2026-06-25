package com.lvyou.model.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 联网调研来源。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResearchSource {

    private String platform;
    private String title;
    private String snippet;
    private String url;
    private String query;
    private String searchRound;
    private String evidenceType;
    private Integer score;
    private Boolean retained;
    private String cleanStatus;
    private String rejectReason;
}
