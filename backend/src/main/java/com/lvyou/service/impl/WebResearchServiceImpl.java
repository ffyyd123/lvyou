package com.lvyou.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lvyou.agent.model.PlannedSearchQuery;
import com.lvyou.agent.service.ResearchKeywordPlanner;
import com.lvyou.model.request.TravelPlanRequest;
import com.lvyou.model.research.SearchQuery;
import com.lvyou.model.response.ResearchReport;
import com.lvyou.model.response.ResearchSource;
import com.lvyou.model.response.ResearchTrace;
import com.lvyou.service.WebResearchService;
import com.lvyou.service.research.ToolGateway;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 联网调研服务：多源搜索、清洗去重、RAG 证据摘要。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebResearchServiceImpl implements WebResearchService {

    private static final int MAX_QUERIES = 15;
    private static final int MAX_SOURCES_PER_QUERY = 20;
    private static final int MAX_CLEANED_SOURCES = 300;
    private static final long RESEARCH_CACHE_TTL_MILLIS = 10 * 60 * 1000L;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final ToolGateway toolGateway;
    private final ResearchKeywordPlanner researchKeywordPlanner;
    private final Map<String, CachedResearchReport> researchCache = new ConcurrentHashMap<>();

    @Value("${research.search-api-url:}")
    private String searchApiUrl;

    @Value("${research.search-api-key:}")
    private String searchApiKey;

    @Override
    public ResearchReport research(TravelPlanRequest request) {
        if (!Boolean.TRUE.equals(request.getOnlineResearch())) {
            return ResearchReport.builder()
                    .enabled(false)
                    .status("disabled")
                    .destination(request.getTo())
                .preference(request.getPreference())
                .generatedAt(LocalDateTime.now())
                .targetKeywordCount(MAX_QUERIES)
                .targetSourcesPerKeyword(MAX_SOURCES_PER_QUERY)
                .targetEffectiveSourceCount(MAX_CLEANED_SOURCES)
                .trustPolicy(trustPolicy())
                .build();
        }

        String cacheKey = cacheKey(request);
        CachedResearchReport cached = researchCache.get(cacheKey);
        if (cached != null && !cached.expired()) {
            log.info("联网调研命中短时缓存: to={}, days={}, preference={}",
                    request.getTo(), request.getDays(), request.getPreference());
            return cached.report();
        }

        List<SearchQuery> queries = buildQueries(request);
        List<ResearchTrace> traces = searchApiConfigured()
                ? searchWithConfiguredApiTraces(queries)
                : toolGateway.searchWithTrace(queries);
        traces = annotateTraceRawSources(traces, request);
        List<ResearchSource> rawSources = traces.stream()
                .flatMap(trace -> trace.getRawSources().stream())
                .toList();
        List<ResearchSource> cleanedSources = cleanAndRank(rawSources, request);
        List<ResearchTrace> enrichedTraces = attachCleanedSources(traces, cleanedSources);
        String summary = buildEvidenceSummary(cleanedSources);

        String status;
        if (cleanedSources.isEmpty()) {
            status = "no_verified_sources";
        } else if (cleanedSources.stream().allMatch(s -> "search_entry_only".equals(s.getEvidenceType()))) {
            status = "entry_only";
        } else {
            status = searchApiConfigured() ? "ok" : "rag_public_search";
        }

        ResearchReport report = ResearchReport.builder()
                .enabled(true)
                .status(status)
                .destination(request.getTo())
                .preference(request.getPreference())
                .generatedAt(LocalDateTime.now())
                .rawSourceCount(rawSources.size())
                .cleanedSourceCount(cleanedSources.size())
                .targetKeywordCount(MAX_QUERIES)
                .targetSourcesPerKeyword(MAX_SOURCES_PER_QUERY)
                .targetEffectiveSourceCount(MAX_CLEANED_SOURCES)
                .evidenceSummary(summary)
                .trustPolicy(trustPolicy())
                .keywordGroups(keywordGroups(queries))
                .searchRounds(queries.stream()
                        .map(q -> q.getDirection() + "｜" + q.getRound() + "：" + q.getKeyword()
                                + (blank(q.getPurpose()) ? "" : "（" + q.getPurpose() + "）"))
                        .toList())
                .traces(enrichedTraces)
                .sources(cleanedSources)
                .build();
        researchCache.put(cacheKey, new CachedResearchReport(report, System.currentTimeMillis()));
        return report;
    }

    @Override
    public String formatForPrompt(ResearchReport report) {
        if (report == null || !Boolean.TRUE.equals(report.getEnabled())) {
            return "联网调研：未启用。";
        }

        StringBuilder builder = new StringBuilder();
        builder.append("联网调研状态：").append(report.getStatus()).append('\n');
        builder.append("调研生成时间：").append(report.getGeneratedAt()).append('\n');
        builder.append("原始来源数：").append(defaultInt(report.getRawSourceCount()))
                .append("，清洗后证据数：").append(defaultInt(report.getCleanedSourceCount())).append('\n');
        builder.append("可信规则：").append(report.getTrustPolicy()).append('\n');
        builder.append("RAG证据摘要：").append(nullToEmpty(report.getEvidenceSummary())).append('\n');
        builder.append("工具调用边界：只允许基于以下清洗后的来源做路线取舍；search_entry_only 只能作为人工复核入口，不能当成事实证据。\n");

        int index = 1;
        for (ResearchSource source : report.getSources()) {
            builder.append(index++).append(". [").append(source.getPlatform()).append("] ")
                    .append(nullToEmpty(source.getTitle())).append('\n')
                    .append("   round: ").append(nullToEmpty(source.getSearchRound()))
                    .append(" | type: ").append(nullToEmpty(source.getEvidenceType()))
                    .append(" | score: ").append(source.getScore() == null ? 0 : source.getScore()).append('\n')
                    .append("   query: ").append(nullToEmpty(source.getQuery())).append('\n')
                    .append("   url: ").append(nullToEmpty(source.getUrl())).append('\n')
                    .append("   摘要: ").append(nullToEmpty(source.getSnippet())).append('\n');
            if (index > MAX_CLEANED_SOURCES) {
                break;
            }
        }
        return builder.toString();
    }

    private List<SearchQuery> buildQueries(TravelPlanRequest request) {
        List<PlannedSearchQuery> planned = researchKeywordPlanner.plan(
                request.getFrom(),
                request.getTo(),
                request.getDays(),
                request.getPreference(),
                request.getUserIdea());
        return planned.stream()
                .limit(MAX_QUERIES)
                .map(item -> query(item.getKeyword(), item.getRound(), item.getDirection(), item.getPlatformHint(), item.getPurpose(), request))
                .toList();
    }

    private SearchQuery query(String keyword,
                              String round,
                              String direction,
                              String platformHint,
                              String purpose,
                              TravelPlanRequest request) {
        return SearchQuery.builder()
                .keyword(keyword)
                .round(round)
                .direction(blank(direction) ? directionFromRound(round) : direction)
                .platformHint(platformHint)
                .purpose(purpose)
                .destination(request.getTo())
                .preference(request.getPreference())
                .build();
    }

    private Map<String, List<String>> keywordGroups(List<SearchQuery> queries) {
        Map<String, List<String>> groups = new LinkedHashMap<>();
        groups.put("食", new ArrayList<>());
        groups.put("住", new ArrayList<>());
        groups.put("行", new ArrayList<>());
        for (SearchQuery query : queries) {
            String direction = blank(query.getDirection()) ? directionFromRound(query.getRound()) : query.getDirection();
            groups.computeIfAbsent(direction, key -> new ArrayList<>()).add(query.getKeyword());
        }
        return groups;
    }

    private boolean searchApiConfigured() {
        return searchApiUrl != null && !searchApiUrl.isBlank()
                && searchApiKey != null && !searchApiKey.isBlank();
    }

    private List<ResearchTrace> searchWithConfiguredApiTraces(List<SearchQuery> queries) {
        List<ResearchTrace> traces = new ArrayList<>();
        int order = 1;
        for (SearchQuery query : queries.stream().limit(MAX_QUERIES).toList()) {
            List<ResearchSource> sources = new ArrayList<>();
            String status = "empty";
            String message = "搜索 API 未返回有效结果。";
            try {
                String url = UriComponentsBuilder.fromHttpUrl(searchApiUrl)
                        .queryParam("q", query.getKeyword())
                        .queryParam("api_key", searchApiKey)
                        .toUriString();
                String body = restTemplate.getForObject(url, String.class);
                JsonNode root = objectMapper.readTree(body);

                JsonNode results = root.path("organic_results");
                if (!results.isArray()) {
                    results = root.path("webPages").path("value");
                }

                if (results.isArray()) {
                    int limit = Math.min(results.size(), MAX_SOURCES_PER_QUERY);
                    for (int i = 0; i < limit; i++) {
                        JsonNode node = results.get(i);
                        String link = node.path("link").asText(node.path("url").asText(""));
                        sources.add(ResearchSource.builder()
                                .platform(platformFromUrl(link))
                                .title(node.path("title").asText(node.path("name").asText("")))
                                .snippet(node.path("snippet").asText(node.path("description").asText("")))
                                .url(link)
                                .query(query.getKeyword())
                                .searchRound(query.getRound())
                                .evidenceType("search_api_result")
                                .build());
                    }
                    status = sources.isEmpty() ? "empty" : "ok";
                    message = "搜索 API 返回 " + sources.size() + " 条结果，进入清洗和目的地相关性校验。";
                }
            } catch (Exception e) {
                log.warn("搜索 API 调用失败 query={}: {}", query.getKeyword(), e.getMessage());
                status = "failed";
                message = "搜索 API 调用失败：" + e.getMessage();
            }
            traces.add(ResearchTrace.builder()
                    .order(order++)
                    .direction(query.getDirection())
                    .round(query.getRound())
                    .keyword(query.getKeyword())
                    .executedKeyword(query.getKeyword())
                    .purpose(query.getPurpose())
                    .platformHint(query.getPlatformHint())
                    .provider("ConfiguredSearchApi")
                    .status(status)
                    .rawCount(sources.size())
                    .cleanedCount(0)
                    .message(message)
                    .rawSources(sources)
                    .build());
        }
        return traces;
    }

    private List<ResearchSource> cleanAndRank(List<ResearchSource> rawSources, TravelPlanRequest request) {
        Map<String, ResearchSource> deduped = new LinkedHashMap<>();
        for (ResearchSource source : rawSources) {
            String rejectReason = rejectReason(source, request);
            if (rejectReason != null) {
                source.setRetained(false);
                source.setCleanStatus("filtered");
                source.setRejectReason(rejectReason);
                continue;
            }
            String key = normalizedUrl(source.getUrl());
            ResearchSource existing = deduped.get(key);
            int score = score(source, request);
            source.setScore(score);
            if (score < 25) {
                source.setRetained(false);
                source.setCleanStatus("filtered");
                source.setRejectReason("相关性评分不足：" + score + " 分，未达到 45 分阈值。");
                continue;
            }
            source.setRetained(true);
            source.setCleanStatus("retained");
            source.setRejectReason("");
            if (existing == null || defaultInt(existing.getScore()) < score) {
                if (existing != null) {
                    existing.setRetained(false);
                    existing.setCleanStatus("filtered");
                    existing.setRejectReason("同一 URL 已保留更高分版本。");
                }
                deduped.put(key, source);
            } else {
                source.setRetained(false);
                source.setCleanStatus("filtered");
                source.setRejectReason("同一 URL 已有更高分版本。");
            }
        }

        return deduped.values().stream()
                .sorted(Comparator.comparing((ResearchSource s) -> defaultInt(s.getScore())).reversed())
                .collect(java.util.stream.Collectors.groupingBy(
                        source -> nullToEmpty(source.getQuery()),
                        LinkedHashMap::new,
                        java.util.stream.Collectors.toList()))
                .values().stream()
                .flatMap(group -> group.stream().limit(MAX_SOURCES_PER_QUERY))
                .limit(MAX_CLEANED_SOURCES)
                .toList();
    }

    private List<ResearchTrace> attachCleanedSources(List<ResearchTrace> traces, List<ResearchSource> cleanedSources) {
        Map<String, ResearchSource> cleanedByUrl = cleanedSources.stream()
                .collect(java.util.stream.Collectors.toMap(
                        source -> normalizedUrl(nullToEmpty(source.getUrl())),
                        source -> source,
                        (first, second) -> first,
                        LinkedHashMap::new));

        for (ResearchTrace trace : traces) {
            List<ResearchSource> rawSources = trace.getRawSources() == null ? List.of() : trace.getRawSources();
            List<ResearchSource> matched = rawSources.stream()
                    .map(source -> cleanedByUrl.get(normalizedUrl(nullToEmpty(source.getUrl()))))
                    .filter(source -> source != null)
                    .toList();
            trace.setCleanedSources(matched);
            trace.setCleanedCount(matched.size());
            if (matched.isEmpty()) {
                trace.setMessage(trace.getMessage() + " 清洗后未保留：可能是不含目的地、重复、广告登录页，或相关性评分不足。");
            } else {
                trace.setMessage(trace.getMessage() + " 清洗后保留 " + matched.size() + " 条可用于 RAG 的证据。");
            }
        }
        return traces;
    }

    private List<ResearchTrace> annotateTraceRawSources(List<ResearchTrace> traces, TravelPlanRequest request) {
        for (ResearchTrace trace : traces) {
            List<ResearchSource> original = trace.getRawSources() == null ? List.of() : trace.getRawSources();
            int rejected = 0;
            for (ResearchSource source : original) {
                String rejectReason = rejectReason(source, request);
                source.setCleanStatus(rejectReason == null ? "candidate" : "filtered");
                source.setRetained(false);
                source.setRejectReason(rejectReason == null ? "" : rejectReason);
                if (rejectReason != null) {
                    rejected++;
                }
            }
            trace.setRawSources(original);
            trace.setRawCount(original.size());
            if (rejected > 0) {
                trace.setMessage(nullToEmpty(trace.getMessage()) + " 初筛标记 " + rejected + " 条不可用结果，原因已逐条展示；这些结果不会进入 RAG。");
            }
        }
        return traces;
    }

    private String rejectReason(ResearchSource source, TravelPlanRequest request) {
        if (source == null) {
            return "结果为空。";
        }
        if (blank(source.getUrl()) || blank(source.getTitle())) {
            return "缺少标题或链接，无法验证。";
        }
        if ("verified_poi_seed".equals(source.getEvidenceType()) || "amap_poi_result".equals(source.getEvidenceType())) {
            return null;
        }
        if ("search_entry_only".equals(source.getEvidenceType())) {
            return "仅为平台搜索入口，未抽取到可验证标题/摘要，不作为事实证据。";
        }
        if (!usable(source)) {
            return "百科、登录页、广告页、非公开网页或不可用链接。";
        }
        if (!destinationRelevant(source, request)) {
            return "标题/摘要/链接未命中当前目的地：" + nullToEmpty(request.getTo()) + "。";
        }
        if (!intentRelevant(source)) {
            return "与当前关键词方向不匹配，例如美食关键词搜到了泛攻略、住宿关键词搜到了景点集合。";
        }
        if (genericCityPage(source)) {
            return "泛城市介绍、百科或政府门户，不能回答当前食住行关键词。";
        }
        return null;
    }

    private boolean usable(ResearchSource source) {
        if (source == null || blank(source.getUrl()) || blank(source.getTitle())) {
            return false;
        }
        if ("verified_poi_seed".equals(source.getEvidenceType())) {
            return true;
        }
        if ("amap_poi_result".equals(source.getEvidenceType())) {
            return true;
        }
        String title = source.getTitle().toLowerCase(Locale.ROOT);
        String url = source.getUrl().toLowerCase(Locale.ROOT);
        if (title.contains("广告") || title.contains("登录") || title.contains("captcha")) {
            return false;
        }
        if (url.contains("baike.baidu.com") || url.contains("wikipedia.org") || title.contains("百科")) {
            return false;
        }
        return url.startsWith("http://") || url.startsWith("https://") || url.startsWith("amap-poi://");
    }

    private int score(ResearchSource source, TravelPlanRequest request) {
        if ("search_entry_only".equals(source.getEvidenceType())) {
            return 5;
        }
        if ("verified_poi_seed".equals(source.getEvidenceType())) {
            return 85;
        }
        if ("amap_poi_result".equals(source.getEvidenceType())) {
            return 88;
        }

        String haystack = (nullToEmpty(source.getTitle()) + " " + nullToEmpty(source.getSnippet()) + " " + nullToEmpty(source.getUrl()))
                .toLowerCase(Locale.ROOT);
        int score = 10;
        if (containsDestination(haystack, request.getTo())) score += 40;
        if (!blank(request.getPreference()) && haystack.contains(request.getPreference().toLowerCase(Locale.ROOT))) score += 15;
        if (haystack.contains("小红书") || haystack.contains("xiaohongshu.com")) score += 12;
        if (haystack.contains("抖音") || haystack.contains("douyin.com")) score += 10;
        if (haystack.contains("高德") || haystack.contains("amap.com")) score += 10;
        if (haystack.contains("gov.cn")) score += 12;
        if (haystack.contains("ctrip.com") || haystack.contains("mafengwo.cn")) score += 8;
        if (haystack.contains("开放") || haystack.contains("预约") || haystack.contains("限流")) score += 8;
        if (!blank(source.getSnippet()) && source.getSnippet().length() >= 20) score += 10;
        if (haystack.contains("2026") || haystack.contains("最新")) score += 8;
        if (intentRelevant(source)) score += 20;
        if (genericCityPage(source)) score -= 30;
        return Math.min(score, 100);
    }

    private boolean intentRelevant(ResearchSource source) {
        if ("search_entry_only".equals(source.getEvidenceType())) {
            return false;
        }
        if ("verified_poi_seed".equals(source.getEvidenceType())) {
            return true;
        }
        if ("amap_poi_result".equals(source.getEvidenceType())) {
            return true;
        }
        String query = nullToEmpty(source.getQuery()) + " " + nullToEmpty(source.getSearchRound());
        String haystack = (nullToEmpty(source.getTitle()) + " " + nullToEmpty(source.getSnippet()) + " " + nullToEmpty(source.getUrl()))
                .toLowerCase(Locale.ROOT);

        if (containsAny(query, "餐", "美食", "小吃", "特色菜", "必吃", "老字号", "早餐", "午餐", "晚餐", "探店")) {
            String titleAndUrl = (nullToEmpty(source.getTitle()) + " " + nullToEmpty(source.getUrl())).toLowerCase(Locale.ROOT);
            if (genericTravelTitle(source) && !containsAny(titleAndUrl, "餐", "美食", "小吃", "特色菜", "必吃", "老字号", "饭店", "餐厅", "早餐", "午餐", "晚餐", "探店", "夜市")) {
                return false;
            }
            return containsAny(haystack, "餐", "美食", "小吃", "特色菜", "必吃", "老字号", "饭店", "餐厅", "早餐", "午餐", "晚餐",
                    "探店", "夜市", "大众点评", "amap", "ctrip", "mafengwo", "xiaohongshu", "douyin", "小红书", "抖音")
                    || (source.getTitle() != null && source.getTitle().length() <= 30 && !source.getTitle().isBlank()
                        && !genericTravelTitle(source) && !titleEndsWithBaike(source));
        }
        if (containsAny(query, "住宿", "酒店", "民宿", "宾馆", "客栈")) {
            return containsAny(haystack, "住宿", "酒店", "民宿", "宾馆", "客栈", "预订", "携程", "飞猪", "去哪儿", "booking", "agoda",
                    "位置", "交通便利", "xiaohongshu", "douyin", "小红书", "抖音", "攻略", "推荐", "评分", "评价");
        }
        if (containsAny(query, "天气", "穿衣")) {
            return containsAny(haystack, "天气", "穿衣", "气温", "温度", "降雨", "下雨", "风力", "湿度");
        }
        if (containsAny(query, "交通", "地铁", "公交", "打车", "自驾")) {
            return containsAny(haystack, "交通", "公交", "地铁", "打车", "自驾", "路线", "换乘", "高铁", "机场", "火车站");
        }
        if (containsAny(query, "开放", "预约", "限流", "门票", "船票")) {
            return containsAny(haystack, "开放", "预约", "限流", "门票", "船票", "闭园", "官方", "gov.cn", "景区");
        }
        return containsAny(haystack, "旅游", "攻略", "路线", "景点", "游记", "避坑", "开放", "预约", "交通", "小红书", "抖音");
    }

    private boolean genericCityPage(ResearchSource source) {
        String title = nullToEmpty(source.getTitle()).toLowerCase(Locale.ROOT);
        String url = nullToEmpty(source.getUrl()).toLowerCase(Locale.ROOT);
        return title.endsWith("_百度百科")
                || title.contains("人民政府门户网站")
                || title.contains("政府门户网站")
                || url.contains("baike.baidu.com")
                || (url.contains(".gov.cn") && containsAny(nullToEmpty(source.getQuery()), "餐", "美食", "小吃", "住宿", "酒店", "民宿"));
    }

    private boolean genericTravelTitle(ResearchSource source) {
        String title = nullToEmpty(source.getTitle());
        return containsAny(title, "旅游攻略", "旅游必去", "十大景点", "超全攻略", "游记攻略", "全攻略");
    }

    private boolean titleEndsWithBaike(ResearchSource source) {
        String title = nullToEmpty(source.getTitle()).toLowerCase(Locale.ROOT);
        return title.endsWith("_百度百科") || title.contains("百科");
    }

    private boolean destinationRelevant(ResearchSource source, TravelPlanRequest request) {
        if ("search_entry_only".equals(source.getEvidenceType())) {
            return false;
        }
        if ("verified_poi_seed".equals(source.getEvidenceType())) {
            return true;
        }
        if ("amap_poi_result".equals(source.getEvidenceType())) {
            return true;
        }
        String haystack = (nullToEmpty(source.getTitle()) + " " + nullToEmpty(source.getSnippet()) + " " + nullToEmpty(source.getUrl()))
                .toLowerCase(Locale.ROOT);
        return containsDestination(haystack, request.getTo());
    }

    private boolean containsDestination(String haystack, String destination) {
        if (blank(haystack) || blank(destination)) {
            return false;
        }
        String target = destination.toLowerCase(Locale.ROOT);
        String normalized = normalizePlaceName(destination).toLowerCase(Locale.ROOT);
        if (haystack.contains(target) || (!normalized.isBlank() && haystack.contains(normalized))) {
            return true;
        }
        for (String alias : destinationAliases(destination)) {
            if (!alias.isBlank() && haystack.contains(alias.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private List<String> destinationAliases(String destination) {
        String normalized = normalizePlaceName(destination);
        List<String> aliases = new ArrayList<>();
        aliases.add(normalized);
        for (String province : List.of(
                "北京", "天津", "上海", "重庆", "河北", "山西", "辽宁", "吉林", "黑龙江", "江苏", "浙江", "安徽",
                "福建", "江西", "山东", "河南", "湖北", "湖南", "广东", "海南", "四川", "贵州", "云南", "陕西",
                "甘肃", "青海", "台湾", "内蒙古", "广西", "西藏", "宁夏", "新疆", "香港", "澳门")) {
            if (normalized.startsWith(province) && normalized.length() > province.length()) {
                aliases.add(normalized.substring(province.length()));
            }
        }
        return aliases.stream().distinct().toList();
    }

    private String normalizePlaceName(String value) {
        if (value == null) {
            return "";
        }
        return value.trim()
                .replace("特别行政区", "")
                .replace("维吾尔自治区", "")
                .replace("壮族自治区", "")
                .replace("回族自治区", "")
                .replace("自治区", "")
                .replace("省", "")
                .replace("市", "");
    }

    private String directionFromRound(String round) {
        String value = round == null ? "" : round;
        if (value.contains("住") || value.contains("酒店") || value.contains("住宿") || value.contains("民宿")) {
            return "住";
        }
        if (value.contains("食") || value.contains("餐") || value.contains("美食") || value.contains("小吃")) {
            return "食";
        }
        return "行";
    }

    private String buildEvidenceSummary(List<ResearchSource> sources) {
        if (sources.isEmpty()) {
            return "未获得可验证公开来源，不能基于互联网内容调整路线。";
        }

        long verified = sources.stream().filter(s -> !"search_entry_only".equals(s.getEvidenceType())).count();
        long entryOnly = sources.size() - verified;
        List<String> platforms = sources.stream()
                .map(ResearchSource::getPlatform)
                .filter(p -> p != null && !p.isBlank())
                .distinct()
                .limit(6)
                .toList();

        StringBuilder builder = new StringBuilder();
        builder.append("清洗后保留 ").append(sources.size()).append(" 条来源，其中可验证公开搜索结果 ")
                .append(verified).append(" 条，搜索入口 ").append(entryOnly).append(" 条。");
        builder.append("覆盖平台：").append(String.join("、", platforms)).append("。");
        builder.append("路线选择应优先采用多个来源反复出现、且仍能通过 POI 工具验证经纬度的地点；开放时间、预约、限流信息必须提示出行前复核。");
        return builder.toString();
    }

    private String trustPolicy() {
        return "真实性优先：只使用含标题、链接、摘要的公开来源；可靠性优先：去重、过滤广告登录页并按目的地/偏好/可用性打分；事实性优先：不编造平台正文、互动数、发布日期；可用性优先：所有入选地点仍需通过 POI 工具验证经纬度。";
    }

    private String cacheKey(TravelPlanRequest request) {
        return String.join("|",
                nullToEmpty(request.getFrom()),
                nullToEmpty(request.getTo()),
                String.valueOf(request.getDays()),
                nullToEmpty(request.getPreference()),
                nullToEmpty(request.getUserIdea()),
                String.valueOf(Boolean.TRUE.equals(request.getOnlineResearch())));
    }

    private String platformFromUrl(String url) {
        if (url == null) return "网页";
        if (url.contains("xiaohongshu.com")) return "小红书";
        if (url.contains("douyin.com")) return "抖音";
        if (url.contains("amap.com")) return "高德";
        if (url.contains("mafengwo.cn")) return "马蜂窝";
        if (url.contains("ctrip.com")) return "携程";
        return "网页";
    }

    private String normalizedUrl(String url) {
        try {
            if (url != null && (url.startsWith("local-poi://") || url.startsWith("amap-poi://"))) {
                return url.toLowerCase(Locale.ROOT);
            }
            URI uri = URI.create(url);
            return (uri.getHost() + uri.getPath()).toLowerCase(Locale.ROOT);
        } catch (Exception e) {
            return url.toLowerCase(Locale.ROOT);
        }
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private boolean containsAny(String text, String... terms) {
        if (text == null || text.isBlank()) {
            return false;
        }
        for (String term : terms) {
            if (text.contains(term)) {
                return true;
            }
        }
        return false;
    }

    private int defaultInt(Integer value) {
        return value == null ? 0 : value;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private record CachedResearchReport(ResearchReport report, long createdAtMillis) {
        boolean expired() {
            return System.currentTimeMillis() - createdAtMillis > RESEARCH_CACHE_TTL_MILLIS;
        }
    }
}


