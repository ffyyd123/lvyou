package com.lvyou.model.research;

import com.lvyou.model.response.ResearchSource;
import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 单个搜索工具返回结果。
 */
@Data
@Builder
public class SearchProviderResult {

    private String provider;
    private String status;
    private String executedKeyword;
    private String message;

    @Builder.Default
    private List<ResearchSource> sources = new ArrayList<>();
}
