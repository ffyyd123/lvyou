package com.lvyou.agent.data;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lvyou.agent.model.PoiInfo;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

/**
 * POI数据存储 — 基于内存Map，禁止使用数据库
 * <p>
 * 数据加载优先级：
 * 1. 从 classpath:poi_data.json 文件加载
 * 2. 文件不存在时使用内置硬编码数据
 * <p>
 * 索引结构：
 * - cityIndex: 城市名 -> POI列表
 * - categoryIndex: 分类 -> POI列表
 */
@Slf4j
@Component
public class PoiDataStore {

    private final Map<String, List<PoiInfo>> cityIndex = new HashMap<>();
    private final Map<String, List<PoiInfo>> categoryIndex = new HashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostConstruct
    public void init() {
        if (!loadFromJsonFile()) {
            loadBuiltinData();
        }
        log.info("📊 POI数据存储初始化完成: {} 个城市, {} 个POI",
                cityIndex.size(),
                cityIndex.values().stream().mapToInt(List::size).sum());
    }

    /**
     * 从 JSON 文件加载 POI 数据
     */
    private boolean loadFromJsonFile() {
        try {
            InputStream is = null;
            Path workspaceData = Path.of("..", "data", "poi_data.json").normalize();
            if (Files.exists(workspaceData)) {
                is = Files.newInputStream(workspaceData);
            }
            if (is == null) {
                is = getClass().getClassLoader().getResourceAsStream("poi_data.json");
            }
            if (is == null) {
                log.warn("未找到 poi_data.json 文件");
                return false;
            }

            List<PoiInfo> allPois = objectMapper.readValue(is,
                    new TypeReference<List<PoiInfo>>() {});

            for (PoiInfo poi : allPois) {
                if (poi.getCity() != null) {
                    cityIndex.computeIfAbsent(poi.getCity(), k -> new ArrayList<>()).add(poi);
                }
                if (poi.getCategory() != null) {
                    categoryIndex.computeIfAbsent(poi.getCategory(), k -> new ArrayList<>()).add(poi);
                }
            }

            log.info("从JSON文件加载了 {} 个POI，覆盖 {} 个城市", allPois.size(), cityIndex.size());
            return !allPois.isEmpty();
        } catch (Exception e) {
            log.warn("从JSON文件加载POI失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 搜索POI
     *
     * @param city    城市名称（模糊匹配）
     * @param keyword 关键词（模糊匹配，可为null）
     * @return 匹配的POI列表
     */
    public List<PoiInfo> search(String city, String keyword) {
        List<PoiInfo> cityPois = findCity(city);
        if (cityPois.isEmpty()) {
            log.info("POI 数据未覆盖目的地 city={}，拒绝回退到其他城市数据", city);
            return Collections.emptyList();
        }

        if (keyword == null || keyword.isBlank()) {
            return new ArrayList<>(cityPois);
        }

        String kw = keyword.toLowerCase();
        List<PoiInfo> matched = cityPois.stream()
                .filter(p -> matchesKeyword(p, kw))
                .collect(Collectors.toList());
        return matched.isEmpty() ? new ArrayList<>(cityPois) : matched;
    }

    /**
     * 获取城市所有POI
     */
    public List<PoiInfo> getByCity(String city) {
        return findCity(city);
    }

    /**
     * 获取所有城市列表
     */
    public Set<String> getCities() {
        return cityIndex.keySet();
    }

    /**
     * 全局搜索 POI，用于模型给出地级市或景点名但数据按省份归档的场景。
     */
    public List<PoiInfo> searchAll(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return cityIndex.values().stream()
                    .flatMap(List::stream)
                    .distinct()
                    .collect(Collectors.toList());
        }

        String kw = keyword.toLowerCase();
        return cityIndex.values().stream()
                .flatMap(List::stream)
                .filter(p -> matchesKeyword(p, kw)
                        || (p.getCity() != null && (p.getCity().contains(keyword) || keyword.contains(p.getCity()))))
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * 模糊匹配城市名
     */
    private List<PoiInfo> findCity(String city) {
        if (city == null || city.isBlank()) {
            return Collections.emptyList();
        }

        // 精确匹配
        List<PoiInfo> result = cityIndex.get(city);
        if (result != null) {
            return result;
        }

        // 模糊匹配时优先选择目的地末尾城市，再选择最长城市名，避免“山西省长治市”命中“山西”省级数据。
        String normalizedCity = normalizePlaceName(city);
        return cityIndex.entrySet().stream()
            .filter(entry -> entry.getKey().contains(city) || city.contains(entry.getKey()))
                .sorted((left, right) -> {
                    int leftScore = cityMatchScore(left.getKey(), normalizedCity);
                    int rightScore = cityMatchScore(right.getKey(), normalizedCity);
                    if (leftScore != rightScore) {
                        return Integer.compare(rightScore, leftScore);
                    }
                    return Integer.compare(right.getKey().length(), left.getKey().length());
                })
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(Collections.emptyList());
    }

    private int cityMatchScore(String indexedCity, String normalizedQuery) {
        String normalizedIndexed = normalizePlaceName(indexedCity);
        if (normalizedQuery.equals(normalizedIndexed)) {
            return 100;
        }
        if (normalizedQuery.endsWith(normalizedIndexed)) {
            return 80;
        }
        if (normalizedQuery.contains(normalizedIndexed)) {
            return 50;
        }
        if (normalizedIndexed.contains(normalizedQuery)) {
            return 30;
        }
        return 0;
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

    /**
     * 关键词匹配（名称、分类、描述）
     */
    private boolean matchesKeyword(PoiInfo poi, String keyword) {
        return (poi.getName() != null && poi.getName().toLowerCase().contains(keyword))
                || (poi.getCategory() != null && poi.getCategory().toLowerCase().contains(keyword))
                || (poi.getDescription() != null && poi.getDescription().toLowerCase().contains(keyword));
    }

    /**
     * 内置硬编码数据（兜底方案）
     */
    private void loadBuiltinData() {
        log.info("使用内置POI数据");

        // 北京
        addPoi("北京", "故宫", 39.9163, 116.3972, 180, "历史文化",
                "明清两代皇家宫殿，世界最大宫殿建筑群");
        addPoi("北京", "天安门广场", 39.9087, 116.3975, 60, "历史文化",
                "世界最大城市广场，中国象征性地标");
        addPoi("北京", "颐和园", 39.9999, 116.2755, 150, "自然风光",
                "清代皇家园林，中国古典园林典范");
        addPoi("北京", "长城（八达岭）", 40.3597, 116.0200, 180, "历史文化",
                "世界文化遗产，万里长城精华段");
        addPoi("北京", "天坛", 39.8822, 116.4066, 90, "历史文化",
                "明清皇帝祭天场所，建筑艺术瑰宝");
        addPoi("北京", "南锣鼓巷", 39.9380, 116.4032, 90, "美食",
                "北京最古老胡同之一，美食小吃集中地");
        addPoi("北京", "798艺术区", 39.9842, 116.4951, 120, "文化艺术",
                "当代艺术聚集区，创意文化地标");
        addPoi("北京", "鸟巢", 39.9928, 116.3889, 60, "现代建筑",
                "2008奥运会主体育场，建筑奇观");

        // 上海
        addPoi("上海", "外滩", 31.2400, 121.4900, 90, "历史文化",
                "万国建筑博览群，黄浦江畔经典景观");
        addPoi("上海", "东方明珠", 31.2397, 121.4998, 90, "现代建筑",
                "上海地标建筑，可俯瞰浦江两岸");
        addPoi("上海", "豫园", 31.2272, 121.4924, 120, "历史文化",
                "明代江南园林，城隍庙美食相伴");
        addPoi("上海", "南京路步行街", 31.2350, 121.4750, 120, "购物",
                "中华第一商业街，购物天堂");
        addPoi("上海", "迪士尼乐园", 31.1433, 121.6600, 480, "主题乐园",
                "中国大陆首座迪士尼主题乐园");
        addPoi("上海", "新天地", 31.2200, 121.4750, 90, "美食",
                "石库门建筑群改造，时尚餐饮聚集地");

        // 杭州
        addPoi("杭州", "西湖", 30.2427, 120.1460, 240, "自然风光",
                "世界文化遗产，中国十大风景名胜");
        addPoi("杭州", "灵隐寺", 30.2432, 120.1000, 120, "历史文化",
                "千年古刹，江南禅宗名寺");
        addPoi("杭州", "雷峰塔", 30.2308, 120.1483, 60, "历史文化",
                "西湖十景之一，白蛇传说发生地");
        addPoi("杭州", "龙井村", 30.2200, 120.1100, 120, "自然风光",
                "龙井茶原产地，茶园风光无限");
        addPoi("杭州", "河坊街", 30.2400, 120.1700, 90, "美食",
                "杭州历史文化街区，特色小吃云集");
        addPoi("杭州", "西溪湿地", 30.2700, 120.0700, 180, "自然风光",
                "城市湿地公园，《非诚勿扰》取景地");

        // 成都
        addPoi("成都", "宽窄巷子", 30.6667, 104.0533, 90, "美食",
                "成都三大历史文化保护区之一");
        addPoi("成都", "武侯祠", 30.6480, 104.0470, 90, "历史文化",
                "三国文化圣地，诸葛亮纪念地");
        addPoi("成都", "锦里", 30.6455, 104.0465, 90, "美食",
                "西蜀第一街，民俗文化美食街");
        addPoi("成都", "大熊猫基地", 30.7350, 104.1440, 180, "自然风光",
                "世界著名大熊猫保护研究基地");
        addPoi("成都", "杜甫草堂", 30.6620, 104.0270, 90, "历史文化",
                "诗圣杜甫故居，古典园林建筑");
        addPoi("成都", "青城山", 30.9000, 103.5667, 300, "自然风光",
                "道教名山，世界文化遗产");

        // 西安
        addPoi("西安", "兵马俑", 34.3850, 109.2730, 180, "历史文化",
                "世界第八大奇迹，秦始皇陵陪葬坑");
        addPoi("西安", "大雁塔", 34.2197, 108.9633, 90, "历史文化",
                "唐代佛教建筑，西安地标");
        addPoi("西安", "回民街", 34.2650, 108.9430, 90, "美食",
                "西安著名美食街，清真小吃云集");
        addPoi("西安", "西安城墙", 34.2630, 108.9460, 120, "历史文化",
                "中国现存规模最大的古代城垣");
        addPoi("西安", "华清宫", 34.3610, 109.2060, 120, "历史文化",
                "唐代皇家温泉行宫，骊山脚下");
        addPoi("西安", "钟楼", 34.2610, 108.9420, 45, "历史文化",
                "西安市中心地标建筑");

        // 南京
        addPoi("南京", "中山陵", 32.0600, 118.8480, 120, "历史文化",
                "孙中山先生陵墓，中国近代建筑第一陵");
        addPoi("南京", "夫子庙", 32.0200, 118.7900, 120, "美食",
                "秦淮河畔，南京历史文化名街");
        addPoi("南京", "明孝陵", 32.0600, 118.8340, 120, "历史文化",
                "明太祖朱元璋陵墓，世界文化遗产");
        addPoi("南京", "总统府", 32.0460, 118.7970, 90, "历史文化",
                "中国近代史重要遗址");
        addPoi("南京", "玄武湖", 32.0750, 118.7960, 120, "自然风光",
                "江南三大名湖之一");

        // 丽江
        addPoi("丽江", "丽江古城", 26.8720, 100.2330, 240, "历史文化",
                "世界文化遗产，纳西族文化中心");
        addPoi("丽江", "玉龙雪山", 27.1250, 100.1800, 360, "自然风光",
                "纳西族神山，北半球最南雪山");
        addPoi("丽江", "束河古镇", 26.9260, 100.2030, 120, "历史文化",
                "茶马古道重要驿站");
        addPoi("丽江", "泸沽湖", 27.6900, 100.8000, 480, "自然风光",
                "高原明珠，摩梭人聚居地");

        // 三亚
        addPoi("三亚", "亚龙湾", 18.2300, 109.6400, 300, "自然风光",
                "天下第一湾，碧海银沙");
        addPoi("三亚", "天涯海角", 18.2900, 109.3500, 120, "自然风光",
                "海南标志性景区");
        addPoi("三亚", "南山寺", 18.3100, 109.2000, 180, "历史文化",
                "南海观音像，108米高");
        addPoi("三亚", "蜈支洲岛", 18.3100, 109.7700, 360, "自然风光",
                "中国第一潜水基地");

        // 桂林
        addPoi("桂林", "漓江", 25.0000, 110.4200, 360, "自然风光",
                "桂林山水甲天下，20元人民币背景");
        addPoi("桂林", "阳朔西街", 24.7780, 110.4900, 120, "美食",
                "中西文化交融的古老街道");
        addPoi("桂林", "象鼻山", 25.2750, 110.2950, 60, "自然风光",
                "桂林城徽，漓江畔标志景观");
        addPoi("桂林", "龙脊梯田", 25.7600, 110.1300, 240, "自然风光",
                "世界梯田之冠，壮瑶文化景观");
    }

    private void addPoi(String city, String name, double lat, double lng,
                        int stayTime, String category, String description) {
        PoiInfo poi = PoiInfo.builder()
                .name(name)
                .city(city)
                .lat(lat)
                .lng(lng)
                .stayTime(stayTime)
                .category(category)
                .description(description)
                .build();
        cityIndex.computeIfAbsent(city, k -> new ArrayList<>()).add(poi);
        categoryIndex.computeIfAbsent(category, k -> new ArrayList<>()).add(poi);
    }
}
