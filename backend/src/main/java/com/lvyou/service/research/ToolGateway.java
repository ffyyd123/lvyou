package com.lvyou.service.research;

import com.lvyou.model.research.SearchProviderResult;
import com.lvyou.model.research.SearchQuery;
import com.lvyou.model.response.ResearchTrace;
import com.lvyou.model.response.ResearchSource;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 联网工具网关，统一限制工具调用边界。
 */
@Component
@RequiredArgsConstructor
public class ToolGateway {

    private static final int MAX_QUERIES = 15;
    private static final int MAX_SOURCES_PER_PROVIDER = 20;

    private final List<SearchProvider> providers;

    public List<ResearchSource> search(List<SearchQuery> queries) {
        return searchWithTrace(queries).stream()
                .flatMap(trace -> trace.getRawSources().stream())
                .toList();
    }

    public List<ResearchTrace> searchWithTrace(List<SearchQuery> queries) {
        List<ResearchTrace> traces = new ArrayList<>();
        int order = 1;
        for (SearchQuery query : queries.stream().limit(MAX_QUERIES).toList()) {
            boolean matchedProvider = false;
            for (SearchProvider provider : providers) {
                if (!provider.supports(query)) {
                    continue;
                }
                matchedProvider = true;
                SearchProviderResult result = provider.search(query);
                List<ResearchSource> providerSources = result == null || result.getSources() == null
                        ? List.of()
                        : result.getSources().stream().limit(MAX_SOURCES_PER_PROVIDER).toList();
                String status = result == null || result.getStatus() == null ? "empty" : result.getStatus();
                String executedKeyword = result == null ? null : result.getExecutedKeyword();
                String message = result == null ? null : result.getMessage();
                traces.add(trace(order++, query, provider.name(), status, executedKeyword, message, providerSources));
            }
            if (!matchedProvider) {
                traces.add(trace(order++, query, "未匹配工具", "skipped", null, null, List.of()));
            }
        }
        return traces;
    }

    private ResearchTrace trace(int order,
                                SearchQuery query,
                                String provider,
                                String status,
                                String executedKeyword,
                                String message,
                                List<ResearchSource> sources) {
        return ResearchTrace.builder()
                .order(order)
                .direction(query.getDirection())
                .round(query.getRound())
                .keyword(query.getKeyword())
                .executedKeyword(executedKeyword)
                .purpose(query.getPurpose())
                .platformHint(query.getPlatformHint())
                .provider(provider)
                .status(status)
                .rawCount(sources.size())
                .cleanedCount(0)
                .message(message == null || message.isBlank() ? buildMessage(status, sources.size()) : message)
                .rawSources(new ArrayList<>(sources))
                .build();
    }

    private String buildMessage(String status, int count) {
        if ("ok".equals(status)) {
            return "工具返回 " + count + " 条公开搜索结果，进入清洗和目的地相关性校验。";
        }
        if ("entry_only".equals(status)) {
            return "未抽取到可验证正文，仅保留平台搜索入口用于人工复核，不作为事实证据。";
        }
        if ("skipped".equals(status)) {
            return "当前关键词未匹配可用搜索工具，已跳过。";
        }
        return "工具返回 " + count + " 条结果，状态：" + status + "。";
    }

    public List<ResearchSource> legacySearch(List<SearchQuery> queries) {
        List<ResearchSource> sources = new ArrayList<>();
        for (SearchQuery query : queries.stream().limit(MAX_QUERIES).toList()) {
            for (SearchProvider provider : providers) {
                if (!provider.supports(query)) {
                    continue;
                }
                SearchProviderResult result = provider.search(query);
                if (result == null || result.getSources() == null) {
                    continue;
                }
                sources.addAll(result.getSources().stream()
                        .limit(MAX_SOURCES_PER_PROVIDER)
                        .toList());
            }
        }
        return sources;
    }
}
