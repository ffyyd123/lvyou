package com.lvyou.service.research;

import com.lvyou.agent.data.PoiDataStore;
import com.lvyou.agent.model.PoiInfo;
import com.lvyou.model.research.SearchProviderResult;
import com.lvyou.model.research.SearchQuery;
import com.lvyou.model.response.ResearchSource;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 本地可验证 POI 证据工具。
 *
 * 只返回目的地已入库且带坐标的 POI 种子，不伪装成互联网搜索结果。
 */
@Component
@RequiredArgsConstructor
public class PoiEvidenceProvider implements SearchProvider {

    private final PoiDataStore poiDataStore;

    @Override
    public String name() {
        return "PoiEvidenceService";
    }

    @Override
    public boolean supports(SearchQuery query) {
        return query != null;
    }

    @Override
    public SearchProviderResult search(SearchQuery query) {
        String city = query.getDestination();
        String evidenceKeyword = evidenceKeyword(query);
        List<PoiInfo> pois = poiDataStore.getByCity(city).stream()
                .filter(poi -> matchesEvidence(poi, evidenceKeyword))
                .toList();
        if (pois.isEmpty() && evidenceKeyword == null) {
            pois = poiDataStore.search(city, null);
        }

        List<ResearchSource> sources = pois.stream()
                .filter(poi -> poi.getName() != null && poi.getLat() != null && poi.getLng() != null)
                .limit(20)
                .map(poi -> ResearchSource.builder()
                        .platform("本地可验证POI")
                        .title(poi.getName())
                        .snippet(buildSnippet(poi))
                        .url("local-poi://" + normalize(city) + "/" + normalize(poi.getName()))
                        .query(query.getKeyword())
                        .searchRound(query.getRound())
                        .evidenceType("verified_poi_seed")
                        .build())
                .toList();

        return SearchProviderResult.builder()
                .provider(name())
                .status(sources.isEmpty() ? "empty" : "ok")
                .executedKeyword(city + " " + (evidenceKeyword == null ? query.getPreference() : evidenceKeyword) + " 本地可验证POI")
                .message(sources.isEmpty()
                        ? "本地 POI 种子库未覆盖当前目的地，不能用其它城市点位兜底。"
                        : "已读取目的地本地可验证 POI 种子，包含坐标、分类、描述和出行复核提示；当高德 WebService 或社交平台正文不可用时，用作可解释降级证据。")
                .sources(sources)
                .build();
    }

    private String evidenceKeyword(SearchQuery query) {
        String direction = query.getDirection() == null ? "" : query.getDirection();
        if ("食".equals(direction)) {
            return "餐饮";
        }
        if ("住".equals(direction)) {
            return "住宿";
        }
        if ("行".equals(direction)) {
            return "行程";
        }
        String text = (query.getKeyword() == null ? "" : query.getKeyword()) + " "
                + (query.getRound() == null ? "" : query.getRound()) + " "
                + (query.getPurpose() == null ? "" : query.getPurpose()) + " "
                + (query.getPreference() == null ? "" : query.getPreference());
        if ("食".equals(direction) || containsAny(text, "餐", "美食", "小吃", "特色菜", "必吃", "老字号", "早餐", "午餐", "晚餐")) {
            return "餐饮";
        }
        if ("住".equals(direction) || containsAny(text, "住宿", "酒店", "民宿", "宾馆", "客栈")) {
            return "住宿";
        }
        return query.getPreference();
    }

    private boolean matchesEvidence(PoiInfo poi, String evidenceKeyword) {
        if (poi == null) {
            return false;
        }
        if (evidenceKeyword == null || evidenceKeyword.isBlank()) {
            return true;
        }
        if ("行程".equals(evidenceKeyword)) {
            String category = nullToEmpty(poi.getCategory());
            return !category.contains("餐饮") && !category.contains("住宿");
        }
        String text = nullToEmpty(poi.getName()) + " "
                + nullToEmpty(poi.getCategory()) + " "
                + nullToEmpty(poi.getDescription()) + " "
                + nullToEmpty(poi.getTags());
        return text.contains(evidenceKeyword);
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

    private String buildSnippet(PoiInfo poi) {
        return "城市：" + nullToEmpty(poi.getCity())
                + "；分类：" + nullToEmpty(poi.getCategory())
                + "；坐标：" + poi.getLat() + "," + poi.getLng()
                + "；描述：" + nullToEmpty(poi.getDescription())
                + "；提醒：" + nullToEmpty(poi.getTips());
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", "-");
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
