package com.lvyou.model.research;

import lombok.Builder;
import lombok.Data;

/**
 * 联网搜索请求。
 */
@Data
@Builder
public class SearchQuery {

    private String keyword;
    private String round;
    private String direction;
    private String platformHint;
    private String destination;
    private String preference;
    private String purpose;
}
