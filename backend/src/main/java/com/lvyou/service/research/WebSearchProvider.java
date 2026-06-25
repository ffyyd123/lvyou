package com.lvyou.service.research;

import com.lvyou.model.research.SearchProviderResult;
import com.lvyou.model.research.SearchQuery;
import org.springframework.stereotype.Component;

/**
 * 通用网页搜索工具。
 */
@Component
public class WebSearchProvider extends AbstractSearchProvider {

    @Override
    public String name() {
        return "WebSearchService";
    }

    @Override
    public boolean supports(SearchQuery query) {
        return query.getPlatformHint() == null || "web".equals(query.getPlatformHint());
    }

    @Override
    public SearchProviderResult search(SearchQuery query) {
        String keyword = buildIntentSearchKeyword(query);
        var sources = searchBing(query, keyword, "网页");
        return SearchProviderResult.builder()
                .provider(name())
                .status(sources.isEmpty() ? "empty" : "ok")
                .executedKeyword(keyword)
                .message(sources.isEmpty()
                        ? "已执行公开网页搜索，但没有抽取到可验证结果；请查看实际查询词判断是否过窄或被搜索引擎限制。"
                        : "已执行公开网页搜索，实际查询词已记录，结果进入清洗和目的地相关性校验。")
                .sources(sources)
                .build();
    }
}
