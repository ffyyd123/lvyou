package com.lvyou.service.research;

import com.lvyou.model.research.SearchProviderResult;
import com.lvyou.model.research.SearchQuery;
import com.lvyou.model.response.ResearchSource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 抖音公开索引搜索工具。使用"关键词+抖音"在Bing上搜索公开内容。
 */
@Component
public class DouyinSearchProvider extends AbstractSearchProvider {

    @Override
    public String name() {
        return "DouyinSearchService";
    }

    @Override
    public boolean supports(SearchQuery query) {
        return "douyin".equals(query.getPlatformHint())
                || (("web".equals(query.getPlatformHint()) || query.getPlatformHint() == null) && socialTravelOrStayQuery(query));
    }

    @Override
    public SearchProviderResult search(SearchQuery query) {
        String keyword = query.getKeyword() + " 抖音";
        List<ResearchSource> sources = new ArrayList<>(searchBing(query, keyword, "抖音"));
        if (sources.isEmpty()) {
            sources.add(searchEntry(query, query.getKeyword(), "抖音", "https://www.douyin.com/search/"));
        }
        return SearchProviderResult.builder()
                .provider(name())
                .status(sources.stream().anyMatch(s -> "public_search_result".equals(s.getEvidenceType())) ? "ok" : "entry_only")
                .executedKeyword(keyword)
                .message(sources.stream().anyMatch(s -> "public_search_result".equals(s.getEvidenceType()))
                        ? "已通过公开搜索检索抖音相关内容，只使用可见标题、摘要和链接。"
                        : "公开搜索未抽到抖音可验证标题摘要，仅保留平台搜索入口用于人工复核。")
                .sources(sources)
                .build();
    }
}
