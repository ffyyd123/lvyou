package com.lvyou.agent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * 高德 LBS 工具：查询真实餐饮、住宿、天气等出行要素。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AmapPlaceTool {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${amap.web-service-key:}")
    private String amapKey;

    @Tool(name = "search_amap_place", description = "使用高德 WebService 查询真实地点。适合搜索景点周边餐厅、酒店、交通站点。返回名称、地址、经纬度、类型、评分等公开字段")
    public String searchPlace(
            @ToolParam(name = "city", description = "城市或省份，如山西、太原市")
            String city,
            @ToolParam(name = "keyword", description = "关键词，如刀削面、老字号、酒店、停车场")
            String keyword,
            @ToolParam(name = "lat", description = "中心点纬度，可为空")
            Double lat,
            @ToolParam(name = "lng", description = "中心点经度，可为空")
            Double lng) {
        if (amapKey == null || amapKey.isBlank()) {
            return "高德 WebService Key 未配置，无法查询真实地点。";
        }

        try {
            UriComponentsBuilder builder;
            if (lat != null && lng != null) {
                builder = UriComponentsBuilder.fromHttpUrl("https://restapi.amap.com/v3/place/around")
                        .queryParam("location", lng + "," + lat)
                        .queryParam("radius", 3000)
                        .queryParam("sortrule", "weight");
            } else {
                builder = UriComponentsBuilder.fromHttpUrl("https://restapi.amap.com/v3/place/text")
                        .queryParam("city", city);
            }

            String url = builder.queryParam("keywords", keyword)
                    .queryParam("offset", 5)
                    .queryParam("page", 1)
                    .queryParam("extensions", "all")
                    .queryParam("output", "JSON")
                    .queryParam("key", amapKey)
                    .toUriString();

            String body = restTemplate.getForObject(url, String.class);
            JsonNode root = objectMapper.readTree(body);
            if (!"1".equals(root.path("status").asText())) {
                return "高德地点查询失败：" + root.path("info").asText("未知错误");
            }

            List<String> lines = new ArrayList<>();
            for (JsonNode poi : root.path("pois")) {
                String name = poi.path("name").asText("");
                String address = poi.path("address").asText("");
                String location = poi.path("location").asText("");
                String type = poi.path("type").asText("");
                String rating = poi.path("biz_ext").path("rating").asText("");
                String cost = poi.path("biz_ext").path("cost").asText("");
                if (name.isBlank() || location.isBlank()) {
                    continue;
                }
                lines.add(String.format("- %s | 地址:%s | 坐标:%s | 类型:%s | 评分:%s | 人均:%s",
                        name, address, location, type, rating, cost));
            }
            return lines.isEmpty()
                    ? "未找到可用高德地点：" + keyword
                    : "高德真实地点查询结果(" + keyword + "):\n" + String.join("\n", lines);
        } catch (Exception e) {
            log.warn("高德地点查询失败 keyword={}: {}", keyword, e.getMessage());
            return "高德地点查询异常：" + e.getMessage();
        }
    }

    @Tool(name = "search_amap_weather", description = "使用高德 WebService 查询城市实时天气，用于穿衣、交通和行程风险提醒")
    public String searchWeather(
            @ToolParam(name = "city", description = "城市或省份，如山西、太原市")
            String city) {
        if (amapKey == null || amapKey.isBlank()) {
            return fallbackWeather(city, "高德 WebService Key 未配置");
        }

        try {
            String url = UriComponentsBuilder.fromHttpUrl("https://restapi.amap.com/v3/weather/weatherInfo")
                    .queryParam("city", city)
                    .queryParam("extensions", "base")
                    .queryParam("output", "JSON")
                    .queryParam("key", amapKey)
                    .toUriString();
            String body = restTemplate.getForObject(url, String.class);
            JsonNode root = objectMapper.readTree(body);
            if (!"1".equals(root.path("status").asText()) || !root.path("lives").isArray() || root.path("lives").isEmpty()) {
                return fallbackWeather(city, "高德天气未查询到：" + root.path("info").asText("未知错误"));
            }
            JsonNode live = root.path("lives").get(0);
            return String.format("高德实时天气：%s %s，天气%s，温度%s℃，风向%s，风力%s，湿度%s%%，发布时间%s。",
                    live.path("province").asText(""),
                    live.path("city").asText(city),
                    live.path("weather").asText(""),
                    live.path("temperature").asText(""),
                    live.path("winddirection").asText(""),
                    live.path("windpower").asText(""),
                    live.path("humidity").asText(""),
                    live.path("reporttime").asText(""));
        } catch (Exception e) {
            log.warn("高德天气查询失败 city={}: {}", city, e.getMessage());
            return fallbackWeather(city, "高德天气查询异常：" + e.getMessage());
        }
    }

    private String fallbackWeather(String city, String reason) {
        try {
            String url = UriComponentsBuilder.fromHttpUrl("https://wttr.in")
                    .pathSegment(city)
                    .queryParam("format", "j1")
                    .toUriString();
            String body = restTemplate.getForObject(url, String.class);
            JsonNode root = objectMapper.readTree(body);
            JsonNode current = root.path("current_condition").isArray() && !root.path("current_condition").isEmpty()
                    ? root.path("current_condition").get(0)
                    : null;
            if (current == null) {
                return weatherUnavailable(city, reason);
            }
            String weather = current.path("lang_zh").isArray() && !current.path("lang_zh").isEmpty()
                    ? current.path("lang_zh").get(0).path("value").asText("")
                    : current.path("weatherDesc").isArray() && !current.path("weatherDesc").isEmpty()
                    ? current.path("weatherDesc").get(0).path("value").asText("")
                    : "";
            return String.format("免 Key 天气兜底：%s 当前%s，温度%s℃，体感%s℃，湿度%s%%，风速%s km/h。来源 wttr.in；触发原因：%s，出行前仍需用官方天气复核。",
                    city,
                    weather,
                    current.path("temp_C").asText(""),
                    current.path("FeelsLikeC").asText(""),
                    current.path("humidity").asText(""),
                    current.path("windspeedKmph").asText(""),
                    reason);
        } catch (Exception e) {
            log.warn("免 Key 天气兜底失败 city={}: {}", city, e.getMessage());
            return weatherUnavailable(city, reason + "；免 Key 天气兜底失败：" + e.getMessage());
        }
    }

    private String weatherUnavailable(String city, String reason) {
        return String.format("%s天气服务暂不可用（%s）。已按季节通用原则提醒穿舒适鞋、备雨具/防晒和外套；出行前必须用官方天气复核。",
                city, reason);
    }
}
