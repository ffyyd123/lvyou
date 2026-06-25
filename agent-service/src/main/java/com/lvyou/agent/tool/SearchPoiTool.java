package com.lvyou.agent.tool;

import com.lvyou.agent.data.PoiDataStore;
import com.lvyou.agent.model.PoiInfo;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Tool 1: search_poi — 根据城市和关键词搜索景点POI
 * <p>
 * 使用 @Tool 注解将 Java 方法暴露为 LLM 可调用的工具。
 * AgentScope 框架自动根据注解生成 JSON Schema 供 LLM 理解。
 * <p>
 * 数据来源: PoiDataStore（内存 Map，从 poi_data.json 加载）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SearchPoiTool {

    private final PoiDataStore poiDataStore;
    private final AmapPlaceTool amapPlaceTool;

    @Tool(name = "search_poi", description = "根据城市名称和偏好关键词搜索景点POI列表，返回景点名称、经纬度、建议停留时间、描述等信息")
    public String searchPoi(
            @ToolParam(name = "city", description = "城市名称，如'北京'、'上海'、'杭州'")
            String city,
            @ToolParam(name = "keyword", description = "搜索关键词，如'历史文化'、'自然风光'、'美食'、'购物'")
            String keyword) {

        log.info("🔍 [search_poi] city={}, keyword={}", city, keyword);

        List<PoiInfo> pois = poiDataStore.search(city, keyword);
        if (pois.isEmpty()) {
            pois = poiDataStore.search(city, null); // 回退：不限关键词
        }
        if (pois.isEmpty()) {
            return searchAmapFallback(city, keyword);
        }

        // 最多返回10个，防止上下文溢出
        List<PoiInfo> limited = pois.size() > 10 ? pois.subList(0, 10) : pois;

        String result = limited.stream()
                .map(p -> String.format(
                        "- %s | 坐标:(%.4f,%.4f) | 建议停留:%d分钟 | 分类:%s | %s",
                        p.getName(), p.getLat(), p.getLng(),
                        p.getStayTime() != null ? p.getStayTime() : 90,
                        p.getCategory() != null ? p.getCategory() : "景点",
                        p.getDescription() != null ? p.getDescription() : ""))
                .collect(Collectors.joining("\n"));

        log.info("🔍 [search_poi] 找到 {} 个POI", limited.size());
        return "在" + city + "找到以下景点(" + keyword + "):\n" + result;
    }

    private String searchAmapFallback(String city, String keyword) {
        String query = (keyword == null || keyword.isBlank() || "综合".equals(keyword))
                ? "景点"
                : keyword + " 景点";
        String result = amapPlaceTool.searchPlace(city, query, null, null);
        if (result == null || !result.contains("- ")) {
            return "未找到 " + city + " 的可验证景点信息。不能使用其他城市景点兜底；请检查高德 WebService Key，或换用已覆盖数据的目的地。";
        }
        log.info("🔍 [search_poi] 本地未覆盖，已通过高德查询 city={}, keyword={}", city, query);
        return "在" + city + "通过高德查询到以下可验证景点(" + query + "):\n" + result;
    }
}
