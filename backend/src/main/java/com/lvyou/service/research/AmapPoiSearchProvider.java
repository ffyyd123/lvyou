package com.lvyou.service.research;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lvyou.model.research.SearchProviderResult;
import com.lvyou.model.research.SearchQuery;
import com.lvyou.model.response.ResearchSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 高德 POI 搜索工具。
 *
 * 用真实地点数据补强网页搜索结果，避免只拿到百科、政府门户或泛攻略时继续伪造路线。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AmapPoiSearchProvider implements SearchProvider {

    private static final int MAX_RESULTS = 20;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${amap.web-service-key:${AMAP_WEBSERVICE_KEY:${VITE_AMAP_KEY:}}}")
    private String amapKey;

    @Override
    public String name() {
        return "AmapPoiSearchService";
    }

    @Override
    public boolean supports(SearchQuery query) {
        return query != null;
    }

    @Override
    public SearchProviderResult search(SearchQuery query) {
        if (amapKey == null || amapKey.isBlank()) {
            return SearchProviderResult.builder()
                    .provider(name())
                    .status("skipped")
                    .executedKeyword("高德 POI 未执行：缺少 WebService Key")
                    .message("高德 WebService Key 未配置，无法用真实 POI 数据补强搜索结果。")
                    .sources(List.of())
                    .build();
        }

        String city = cityName(query.getDestination());
        List<String> keywords = buildPoiKeywords(query, city);
        List<ResearchSource> sources = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        Set<String> failureInfos = new LinkedHashSet<>();
        for (String keyword : keywords) {
            if (sources.size() >= MAX_RESULTS) {
                break;
            }
            sources.addAll(searchOne(query, city, keyword, seen, failureInfos, MAX_RESULTS - sources.size()));
        }

        String executed = "高德POI：" + String.join(" / ", keywords);
        String failureText = failureInfos.isEmpty() ? "" : " 高德接口返回：" + String.join("、", failureInfos) + "。";
        return SearchProviderResult.builder()
                .provider(name())
                .status(sources.isEmpty() ? "empty" : "ok")
                .executedKeyword(executed)
                .message(sources.isEmpty()
                        ? "已调用高德 POI 搜索，但没有拿到与当前关键词匹配的真实地点；不会用其它城市点位兜底。" + failureText
                        : "已调用高德 POI 搜索，返回真实地点名称、地址、类型和坐标，可用于 RAG 约束、地图标点和路线可用性校验。")
                .sources(sources)
                .build();
    }

    private List<ResearchSource> searchOne(SearchQuery query,
                                           String city,
                                           String keyword,
                                           Set<String> seen,
                                           Set<String> failureInfos,
                                           int remaining) {
        try {
            String url = UriComponentsBuilder.fromHttpUrl("https://restapi.amap.com/v3/place/text")
                    .queryParam("keywords", keyword)
                    .queryParam("city", city)
                    .queryParam("citylimit", true)
                    .queryParam("offset", Math.min(25, Math.max(1, remaining)))
                    .queryParam("page", 1)
                    .queryParam("extensions", "all")
                    .queryParam("output", "JSON")
                    .queryParam("key", amapKey)
                    .toUriString();
            String body = restTemplate.getForObject(url, String.class);
            JsonNode root = objectMapper.readTree(body);
            if (!"1".equals(root.path("status").asText())) {
                String info = root.path("info").asText("");
                failureInfos.add(info.isBlank() ? "未知错误" : info);
                log.warn("高德 POI 搜索失败 keyword={}, info={}", keyword, info);
                return List.of();
            }

            List<ResearchSource> result = new ArrayList<>();
            for (JsonNode poi : root.path("pois")) {
                String id = poi.path("id").asText("");
                String name = poi.path("name").asText("");
                String location = poi.path("location").asText("");
                if (name.isBlank() || location.isBlank()) {
                    continue;
                }
                String dedupeKey = id.isBlank() ? name + "|" + location : id;
                if (!seen.add(dedupeKey)) {
                    continue;
                }
                result.add(ResearchSource.builder()
                        .platform("高德POI")
                        .title(name)
                        .snippet(buildSnippet(poi, keyword))
                        .url("amap-poi://" + normalize(city) + "/" + normalize(dedupeKey))
                        .query(query.getKeyword())
                        .searchRound(query.getRound())
                        .evidenceType("amap_poi_result")
                        .build());
                if (result.size() >= remaining) {
                    break;
                }
            }
            return result;
        } catch (Exception e) {
            log.warn("高德 POI 搜索异常 keyword={}: {}", keyword, e.getMessage());
            return List.of();
        }
    }

    private String buildSnippet(JsonNode poi, String keyword) {
        JsonNode biz = poi.path("biz_ext");
        return "关键词：" + keyword
                + "；省份：" + poi.path("pname").asText("")
                + "；城市：" + poi.path("cityname").asText("")
                + "；区县：" + poi.path("adname").asText("")
                + "；地址：" + poi.path("address").asText("")
                + "；坐标：" + poi.path("location").asText("")
                + "；类型：" + poi.path("type").asText("")
                + "；评分：" + biz.path("rating").asText("")
                + "；人均：" + biz.path("cost").asText("");
    }

    private List<String> buildPoiKeywords(SearchQuery query, String city) {
        String direction = query.getDirection() == null ? "" : query.getDirection();
        String text = nullToEmpty(query.getKeyword()) + " " + nullToEmpty(query.getRound()) + " " + nullToEmpty(query.getPurpose());
        List<String> keywords = new ArrayList<>();
        if ("食".equals(direction) || containsAny(text, "餐", "美食", "小吃", "特色菜", "必吃", "老字号", "探店")) {
            keywords.add(city + " 餐厅");
            keywords.add(city + " 美食");
            keywords.add(city + " 小吃");
            keywords.add(city + " 老字号");
            keywords.add(city + " 特色菜");
        } else if ("住".equals(direction) || containsAny(text, "住宿", "酒店", "民宿", "宾馆", "客栈")) {
            keywords.add(city + " 酒店");
            keywords.add(city + " 民宿");
            keywords.add(city + " 住宿");
            keywords.add(city + " 高铁站 酒店");
            keywords.add(city + " 市中心 酒店");
        } else {
            keywords.add(city + " 旅游景点");
            keywords.add(city + " 风景名胜");
            keywords.add(city + " 博物馆");
            keywords.add(city + " 公园");
            keywords.add(city + " 交通枢纽");
        }
        return keywords.stream().distinct().toList();
    }

    private String cityName(String destination) {
        String value = destination == null ? "" : destination.trim();
        String normalized = value
                .replace("特别行政区", "")
                .replace("维吾尔自治区", "")
                .replace("壮族自治区", "")
                .replace("回族自治区", "")
                .replace("自治区", "")
                .replace("省", "");
        int index = normalized.lastIndexOf("市");
        if (index > 0) {
            int start = 0;
            for (String province : List.of(
                    "北京", "天津", "上海", "重庆", "河北", "山西", "辽宁", "吉林", "黑龙江", "江苏", "浙江", "安徽",
                    "福建", "江西", "山东", "河南", "湖北", "湖南", "广东", "海南", "四川", "贵州", "云南", "陕西",
                    "甘肃", "青海", "台湾", "内蒙古", "广西", "西藏", "宁夏", "新疆", "香港", "澳门")) {
                if (normalized.startsWith(province) && normalized.length() > province.length()) {
                    start = province.length();
                    break;
                }
            }
            return normalized.substring(start, index + 1);
        }
        return normalized.isBlank() ? value : normalized;
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

    private String normalize(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", "-");
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
