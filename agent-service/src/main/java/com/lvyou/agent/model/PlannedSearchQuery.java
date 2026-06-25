package com.lvyou.agent.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI 规划出的联网搜索查询。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PlannedSearchQuery {

    /** 搜索关键词 */
    private String keyword;

    /** 搜索轮次或意图 */
    private String round;

    /** 调研方向：食、住、行 */
    private String direction;

    /** 平台提示：web/xhs/douyin */
    private String platformHint;

    /** 为什么需要这个查询 */
    private String purpose;
}
