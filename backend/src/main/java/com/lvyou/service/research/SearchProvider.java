package com.lvyou.service.research;

import com.lvyou.model.research.SearchProviderResult;
import com.lvyou.model.research.SearchQuery;

/**
 * 联网搜索工具接口。
 */
public interface SearchProvider {

    String name();

    boolean supports(SearchQuery query);

    SearchProviderResult search(SearchQuery query);
}
