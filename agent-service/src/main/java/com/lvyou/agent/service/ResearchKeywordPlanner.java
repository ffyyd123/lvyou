package com.lvyou.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lvyou.agent.model.PlannedSearchQuery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 联网调研关键词规划器。
 *
 * AI 逻辑放在 Agent 层，业务层只拿结构化关键词执行工具搜索。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ResearchKeywordPlanner {

    private static final int TARGET_PER_DIRECTION = 5;
    private static final int TARGET_QUERIES = 15;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${llm.url}")
    private String llmUrl;

    @Value("${llm.api-key}")
    private String apiKey;

    @Value("${llm.model}")
    private String model;

    public List<PlannedSearchQuery> plan(String from,
                                         String to,
                                         Integer days,
                                         String preference,
                                         String userIdea) {
        try {
            List<PlannedSearchQuery> planned = callPlanner(from, to, days, preference, userIdea);
            List<PlannedSearchQuery> normalized = normalizePlan(planned, to, preference, userIdea);
            if (normalized.size() == TARGET_QUERIES) {
                return normalized;
            }
        } catch (Exception e) {
            log.warn("AI 搜索关键词规划失败，使用兜底关键词: {}", e.getMessage());
        }
        return fallback(to, preference);
    }

    private List<PlannedSearchQuery> callPlanner(String from,
                                                 String to,
                                                 Integer days,
                                                 String preference,
                                                 String userIdea) throws Exception {
        String prompt = String.format("""
                你是旅行调研关键词规划 Agent。请先理性分析目的地、省份/城市、用户偏好和补充要求，再输出用于多轮搜索的关键词。
                目标：让后续工具搜索到可用于真实规划的食、住、行资料，避免泛泛攻略和无关数据。
                用户需求：
                - 出发地：%s
                - 目的地：%s
                - 天数：%d
                - 偏好：%s
                - 补充要求：%s

                只输出 JSON，不要 markdown。格式：
                {
                  "queries": [
                    {
                      "keyword": "搜索关键词",
                      "direction": "食 或 住 或 行",
                      "round": "搜索轮次，如 食-真实餐厅/住-住宿区域/行-路线交通",
                      "platformHint": "web 或 xhs 或 douyin",
                      "purpose": "这个查询要验证什么"
                    }
                  ]
                }

                要求：
                - 必须生成 15 个查询，不能多也不能少。
                - 三个大方向固定为：食、住、行；每个方向必须 5 个关键词。
                - 每个关键词必须包含目的地城市或省份名称，并结合用户偏好、补充要求或具体场景。
                - 食：覆盖真实餐厅、当地特色、早餐/午餐/晚餐、避坑排队、地图/点评复核。
                - 住：覆盖住宿区域、具体酒店/民宿、交通便利性、预算/安全、OTA/地图复核。
                - 行：覆盖景点路线、顺路分区、公共交通/自驾/打车、开放预约限流、天气穿衣。
                - 小红书/抖音只能作为热度和避坑入口，不能当事实来源；事实可用性优先使用官网、地图、OTA、攻略站。
                - 不要搜索出发地景点；出发地只用于交通衔接。
                """,
                from, to, days == null ? 1 : days, preference, userIdea == null ? "" : userIdea);

        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "user");
        message.put("content", prompt);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("temperature", 0.2);
        body.put("messages", List.of(message));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        String endpoint = llmUrl.endsWith("/") ? llmUrl + "chat/completions" : llmUrl + "/chat/completions";
        String response = restTemplate.postForObject(endpoint, new HttpEntity<>(body, headers), String.class);
        JsonNode root = objectMapper.readTree(response);
        String content = root.path("choices").path(0).path("message").path("content").asText("");
        JsonNode json = objectMapper.readTree(extractJson(content));

        List<PlannedSearchQuery> queries = new ArrayList<>();
        JsonNode array = json.path("queries");
        if (array.isArray()) {
            for (JsonNode node : array) {
                String keyword = node.path("keyword").asText("");
                if (keyword.isBlank()) {
                    continue;
                }
                queries.add(PlannedSearchQuery.builder()
                        .keyword(keyword)
                        .direction(normalizeDirection(node.path("direction").asText(node.path("round").asText(""))))
                        .round(node.path("round").asText("AI关键词规划"))
                        .platformHint(normalizePlatform(node.path("platformHint").asText("web")))
                        .purpose(node.path("purpose").asText(""))
                        .build());
            }
        }
        return queries;
    }

    private List<PlannedSearchQuery> normalizePlan(List<PlannedSearchQuery> planned,
                                                   String to,
                                                   String preference,
                                                   String userIdea) {
        List<PlannedSearchQuery> merged = new ArrayList<>();
        if (planned != null) {
            merged.addAll(planned);
        }
        merged.addAll(fallback(to, preference));

        Map<String, PlannedSearchQuery> deduped = new LinkedHashMap<>();
        for (PlannedSearchQuery query : merged) {
            if (query == null || query.getKeyword() == null || query.getKeyword().isBlank()) {
                continue;
            }
            String direction = normalizeDirection(query.getDirection() == null ? query.getRound() : query.getDirection());
            String keyword = sanitizeKeyword(ensureDestination(query.getKeyword(), to), to, preference);
            if (keyword.isBlank()) {
                continue;
            }
            String key = direction + "|" + keyword;
            deduped.putIfAbsent(key, PlannedSearchQuery.builder()
                    .keyword(keyword)
                    .direction(direction)
                    .round(direction + "-" + firstNonBlank(query.getRound(), "AI关键词规划"))
                    .platformHint(normalizePlatform(query.getPlatformHint()))
                    .purpose(firstNonBlank(query.getPurpose(), "验证" + direction + "方向的真实可用信息"))
                    .build());
        }

        List<PlannedSearchQuery> result = new ArrayList<>();
        for (String direction : List.of("食", "住", "行")) {
            List<PlannedSearchQuery> group = deduped.values().stream()
                    .filter(q -> direction.equals(q.getDirection()))
                    .limit(TARGET_PER_DIRECTION)
                    .toList();
            result.addAll(group);
        }
        return result.stream().limit(TARGET_QUERIES).toList();
    }

    private String sanitizeKeyword(String keyword, String destination, String preference) {
        String value = keyword == null ? "" : keyword.trim();
        if (value.isBlank()) {
            return "";
        }
        String lower = value.toLowerCase();
        if (lower.contains("[") || lower.contains("]") || lower.contains("xxx")
                || value.contains("【") || value.contains("】")
                || lower.contains("某") || lower.contains("餐厅名称") || lower.contains("酒店名称")
                || lower.contains("餐厅名") || lower.contains("酒店名") || lower.contains("具体名称")) {
            return "";
        }
        String dest = destination == null ? "" : destination.trim();
        String normalizedDest = normalizePlaceName(dest);
        if (!dest.isBlank() && !value.contains(dest) && !normalizedDest.isBlank() && !value.contains(normalizedDest)) {
            return "";
        }
        String pref = preference == null ? "" : preference.trim();
        return value.replaceAll("\\s+", " ")
                .replace("2024", "最新")
                .replace("2025", "最新")
                .replace("2026", "最新")
                .trim();
    }

    private String extractJson(String text) {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.startsWith("```json")) {
            trimmed = trimmed.substring(7);
        } else if (trimmed.startsWith("```")) {
            trimmed = trimmed.substring(3);
        }
        if (trimmed.endsWith("```")) {
            trimmed = trimmed.substring(0, trimmed.length() - 3);
        }
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        return start >= 0 && end > start ? trimmed.substring(start, end + 1) : trimmed;
    }

    private String normalizePlatform(String platform) {
        String value = platform == null ? "web" : platform.toLowerCase();
        if (value.contains("xhs") || value.contains("小红书")) {
            return "xhs";
        }
        if (value.contains("douyin") || value.contains("抖音")) {
            return "douyin";
        }
        return "web";
    }

    private String normalizeDirection(String value) {
        String text = value == null ? "" : value;
        if (text.contains("住") || text.contains("酒店") || text.contains("住宿") || text.contains("民宿")) {
            return "住";
        }
        if (text.contains("行") || text.contains("路线") || text.contains("交通") || text.contains("景点") || text.contains("天气") || text.contains("预约")) {
            return "行";
        }
        return "食";
    }

    private String ensureDestination(String keyword, String destination) {
        String kw = keyword == null ? "" : keyword.trim();
        String dest = destination == null ? "" : destination.trim();
        if (dest.isBlank() || kw.contains(dest) || kw.contains(normalizePlaceName(dest))) {
            return kw;
        }
        return dest + " " + kw;
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

    private List<PlannedSearchQuery> fallback(String to, String preference) {
        String destination = to == null ? "" : to;
        String pref = preference == null ? "" : preference;
        return List.of(
                planned(destination + " " + pref + " 必吃餐厅 老字号 本地菜 地图", "食-真实餐厅", "web", "寻找真实餐厅和当地特色"),
                planned(destination + " 早餐 午餐 晚餐 推荐 点评 高德", "食-三餐安排", "web", "验证每天三餐可落地地点"),
                planned(destination + " 餐厅 避坑 排队 小红书", "食-避坑排队", "xhs", "收集餐饮排队和避坑信号"),
                planned(destination + " 美食街 夜市 老街 小吃 交通", "食-餐饮街区", "web", "寻找适合路线衔接的餐饮街区"),
                planned(destination + " 探店 餐厅 热门 抖音", "食-热度交叉验证", "douyin", "交叉验证热门餐饮"),
                planned(destination + " 住宿 区域 酒店 交通方便 地铁 商圈", "住-住宿区域", "web", "验证住宿区域"),
                planned(destination + " 酒店 民宿 位置 安全 评价 OTA", "住-住宿评价", "web", "验证具体住宿候选"),
                planned(destination + " 亲子 情侣 家庭 住宿 区域 推荐", "住-人群匹配", "web", "按用户场景筛选住宿"),
                planned(destination + " 住宿 避坑 噪音 停车 小红书", "住-避坑", "xhs", "收集住宿风险"),
                planned(destination + " 酒店 到 景点 交通方便 高德地图", "住-交通衔接", "web", "验证住宿到景点的交通便利性"),
                planned(destination + " " + pref + " 景点 分区 顺路 路线 官方 攻略", "行-路线框架", "web", "验证目的地内景点分区和顺路顺序"),
                planned(destination + " 景区 官方 开放时间 预约 限流 闭园", "行-开放预约", "web", "验证景区可用性和风险"),
                planned(destination + " 公共交通 地铁 打车 自驾 包车 攻略", "行-交通方式", "web", "验证交通方式"),
                planned(destination + " 天气 穿衣 海边 山区 雨季 出行", "行-天气穿衣", "web", "验证天气和穿衣提醒"),
                planned(destination + " 热门景点 避坑 排队 抖音 小红书", "行-热度避坑", "douyin", "交叉验证热门景点和避坑")
        );
    }

    private PlannedSearchQuery planned(String keyword, String round, String platform, String purpose) {
        return PlannedSearchQuery.builder()
                .keyword(keyword)
                .direction(normalizeDirection(round))
                .round(round)
                .platformHint(platform)
                .purpose(purpose)
                .build();
    }

    private String firstNonBlank(String first, String second) {
        return first == null || first.isBlank() ? second : first;
    }
}
