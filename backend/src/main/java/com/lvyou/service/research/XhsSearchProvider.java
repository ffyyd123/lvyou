package com.lvyou.service.research;

import com.lvyou.model.research.SearchProviderResult;
import com.lvyou.model.research.SearchQuery;
import com.lvyou.model.response.ResearchSource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 小红书公开索引搜索工具。使用"关键词+小红书"在Bing上搜索公开内容。
 */
@Component
public class XhsSearchProvider extends AbstractSearchProvider {

    @Override
    public String name() {
        return "XhsSearchService";
    }

    @Override
    public boolean supports(SearchQuery query) {
        return "xhs".equals(query.getPlatformHint())
                || (("web".equals(query.getPlatformHint()) || query.getPlatformHint() == null) && socialFoodOrAvoidQuery(query));
    }

    @Override
    public SearchProviderResult search(SearchQuery query) {
        String keyword = query.getKeyword() + " 小红书";
        List<ResearchSource> sources = new ArrayList<>(searchBing(query, keyword, "小红书"));
        if (sources.isEmpty()) {
            sources.add(searchEntry(query, query.getKeyword(), "小红书", "https://www.xiaohongshu.com/search_result?keyword="));
        }
        return SearchProviderResult.builder()
                .provider(name())
                .status(sources.stream().anyMatch(s -> "public_search_result".equals(s.getEvidenceType())) ? "ok" : "entry_only")
                .executedKeyword(keyword)
                .message(sources.stream().anyMatch(s -> "public_search_result".equals(s.getEvidenceType()))
                        ? "已通过公开搜索检索小红书相关内容，只使用可见标题、摘要和链接。"
                        : "公开搜索未抽到小红书可验证标题摘要，仅保留平台搜索入口用于人工复核。")
                .sources(sources)
                .build();
    }
}
