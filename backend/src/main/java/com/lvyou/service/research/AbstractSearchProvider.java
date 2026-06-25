package com.lvyou.service.research;

import com.lvyou.model.research.SearchQuery;
import com.lvyou.model.response.ResearchSource;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Slf4j
abstract class AbstractSearchProvider implements SearchProvider {

    protected List<ResearchSource> searchBing(SearchQuery query, String keyword, String platform) {
        try {
            String url = "https://www.bing.com/search?q=" + encode(keyword) + "&count=20";
            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/126 Safari/537.36")
                    .referrer("https://www.bing.com/")
                    .timeout(12000)
                    .get();

            List<ResearchSource> sources = new ArrayList<>();
            Set<String> seenUrls = new LinkedHashSet<>();
            for (Element link : extractResultLinks(doc)) {
                String href = link.attr("href");
                String titleText = link.text();
                if (href == null || href.isBlank() || titleText == null || titleText.isBlank()) { continue; }
                if (!href.startsWith("http://") && !href.startsWith("https://")) { continue; }
                if (!seenUrls.add(href)) { continue; }
                Element item = nearestResultContainer(link);
                String snippetText = extractSnippet(item, link);
                ResearchSource source = ResearchSource.builder()
                        .platform(platformFromUrl(href, platform))
                        .title(titleText)
                        .snippet(snippetText)
                        .url(href)
                        .query(query.getKeyword())
                        .searchRound(query.getRound())
                        .evidenceType("public_search_result")
                        .build();
                if (!rawResultRelevant(query, source)) { continue; }
                sources.add(source);
                if (sources.size() >= 20) { break; }
            }
            if (sources.isEmpty()) {
                List<ResearchSource> relaxed = lenientSearch(query, keyword, platform, doc);
                if (!relaxed.isEmpty()) { return relaxed; }
                return searchBingRss(query, keyword, platform);
            }
            return sources;
        } catch (Exception e) {
            log.warn("{} 搜索失败 keyword={}: {}", name(), keyword, e.getMessage());
            return List.of();
        }
    }


    private List<ResearchSource> lenientSearch(SearchQuery query, String keyword, String platform, Document doc) {
        List<ResearchSource> sources = new ArrayList<>();
        Set<String> seenUrls = new LinkedHashSet<>();
        for (Element link : extractResultLinks(doc)) {
            String href = link.attr("href");
            String titleText = link.text();
            if (href == null || href.isBlank() || titleText == null || titleText.isBlank()) { continue; }
            if (!href.startsWith("http://") && !href.startsWith("https://")) { continue; }
            if (!seenUrls.add(href)) { continue; }
            Element item = nearestResultContainer(link);
            String snippetText = extractSnippet(item, link);
            ResearchSource source = ResearchSource.builder()
                    .platform(platformFromUrl(href, platform))
                    .title(titleText)
                    .snippet(snippetText)
                    .url(href)
                    .query(query.getKeyword())
                    .searchRound(query.getRound() + "-宽松")
                    .evidenceType("public_search_result")
                    .build();
            String text = ((titleText == null ? "" : titleText) + " " + (href == null ? "" : href)).toLowerCase();
            if (containsAny(text, "baike.baidu.com", "wikipedia.org", "百科")) { continue; }
            if (containsAny(text, ".gov.cn", "人民政府", "政府门户")) { continue; }
            sources.add(source);
            if (sources.size() >= 15) { break; }
        }
        return sources;
    }

    private List<ResearchSource> searchBingRss(SearchQuery query, String keyword, String platform) {
        try {
            String url = "https://www.bing.com/search?q=" + encode(keyword) + "&format=rss&count=20";
            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/126 Safari/537.36")
                    .referrer("https://www.bing.com/")
                    .timeout(12000)
                    .parser(Parser.xmlParser())
                    .get();
            List<ResearchSource> strictSources = new ArrayList<>();
            Set<String> seenUrls = new LinkedHashSet<>();
            for (Element item : doc.select("item")) {
                String href = textOf(item, "link");
                String title = textOf(item, "title");
                String snippet = textOf(item, "description");
                if (href.isBlank() || title.isBlank() || !seenUrls.add(href)) { continue; }
                ResearchSource source = ResearchSource.builder()
                        .platform(platformFromUrl(href, platform))
                        .title(title)
                        .snippet(snippet)
                        .url(href)
                        .query(query.getKeyword())
                        .searchRound(query.getRound())
                        .evidenceType("public_search_result")
                        .build();
                if (!rawResultRelevant(query, source)) { continue; }
                strictSources.add(source);
                if (strictSources.size() >= 15) { break; }
            }
            if (!strictSources.isEmpty()) { return strictSources; }
            List<ResearchSource> relaxedSources = new ArrayList<>();
            seenUrls.clear();
            for (Element item : doc.select("item")) {
                String href = textOf(item, "link");
                String title = textOf(item, "title");
                String snippet = textOf(item, "description");
                if (href.isBlank() || title.isBlank() || !seenUrls.add(href)) { continue; }
                String text = (title + " " + href).toLowerCase();
                if (containsAny(text, "baike.baidu.com", "wikipedia.org", "百科")) { continue; }
                if (containsAny(text, ".gov.cn", "人民政府", "政府门户")) { continue; }
                ResearchSource source = ResearchSource.builder()
                        .platform(platformFromUrl(href, platform))
                        .title(title)
                        .snippet(snippet)
                        .url(href)
                        .query(query.getKeyword())
                        .searchRound(query.getRound() + "-宽松")
                        .evidenceType("public_search_result")
                        .build();
                relaxedSources.add(source);
                if (relaxedSources.size() >= 15) { break; }
            }
            return relaxedSources;
        } catch (Exception e) {
            log.warn("{} RSS 搜索失败 keyword={}: {}", name(), keyword, e.getMessage());
            return List.of();
        }
    }


    private String textOf(Element item, String selector) {
        Element element = item.selectFirst(selector);
        return element == null ? "" : element.text();
    }

    private List<Element> extractResultLinks(Document doc) {
        List<Element> links = new ArrayList<>();
        links.addAll(doc.select("li.b_algo h2 a[href]"));
        links.addAll(doc.select("h2 a[href]"));
        links.addAll(doc.select("a.tilk[href], a[href][aria-label]"));
        links.addAll(doc.select("main a[href], #b_results a[href]"));
        return links;
    }

    private Element nearestResultContainer(Element link) {
        Element current = link;
        for (int i = 0; i < 4 && current != null; i++) {
            if (current.tagName().equals("li") || current.className().contains("b_algo")
                    || current.className().contains("b_ans") || current.className().contains("algo")) {
                return current;
            }
            current = current.parent();
        }
        return link.parent();
    }

    private String extractSnippet(Element item, Element link) {
        if (item == null) { return ""; }
        Element snippet = item.selectFirst(".b_caption p, .b_snippet, p, .snippet, .b_lineclamp");
        if (snippet != null) { return snippet.text(); }
        String text = item.text();
        String title = link.text();
        if (text == null || title == null) { return ""; }
        return text.replace(title, "").trim();
    }

    protected ResearchSource searchEntry(SearchQuery query, String keyword, String platform, String url) {
        return ResearchSource.builder()
                .platform(platform)
                .title("搜索入口：" + keyword)
                .snippet("未能抽取可验证公开结果")
                .url(url + encode(keyword))
                .query(keyword)
                .searchRound(query.getRound())
                .evidenceType("search_entry_only")
                .score(0)
                .build();
    }


    protected String buildIntentSearchKeyword(SearchQuery query) {
        String keyword = query.getKeyword() == null ? "" : query.getKeyword().trim();
        String direction = query.getDirection() == null ? "" : query.getDirection();
        String round = query.getRound() == null ? "" : query.getRound();
        String text = keyword + " " + round;

        if ("食".equals(direction) || containsAny(text, "餐", "美食", "小吃", "特色菜", "必吃", "老字号", "早餐", "午餐", "晚餐", "探店")) {
            return keyword + " 餐厅 美食 小吃 特色菜 老字号 点评 推荐";
        }
        if ("住".equals(direction) || containsAny(text, "住宿", "酒店", "民宿", "OTA")) {
            return keyword + " 酒店 民宿 住宿 位置 评价 预订 交通便利";
        }
        if (containsAny(text, "天气", "穿衣")) {
            return keyword + " 天气 穿衣 气温 降雨 出行 提醒";
        }
        if (containsAny(text, "交通", "地铁", "公交", "打车", "自驾")) {
            return keyword + " 交通 公交 地铁 打车 自驾 攻略";
        }
        if (containsAny(text, "开放", "预约", "限流", "闭园", "船票")) {
            return keyword + " 官方 开放时间 预约 限流 门票 最新";
        }
        return keyword + " 旅游 攻略 路线 景点 真实体验";
    }

    protected boolean socialFoodOrAvoidQuery(SearchQuery query) {
        String text = ((query.getKeyword() == null ? "" : query.getKeyword()) + " "
                + (query.getRound() == null ? "" : query.getRound()) + " "
                + (query.getPurpose() == null ? "" : query.getPurpose()));
        return "食".equals(query.getDirection())
                || containsAny(text, "餐厅", "美食", "小吃", "特色菜", "必吃", "老字号", "探店", "避坑", "排队", "热门");
    }

    protected boolean socialTravelOrStayQuery(SearchQuery query) {
        String text = ((query.getKeyword() == null ? "" : query.getKeyword()) + " "
                + (query.getRound() == null ? "" : query.getRound()) + " "
                + (query.getPurpose() == null ? "" : query.getPurpose()));
        return containsAny(text, "避坑", "热门", "排队", "住宿", "酒店", "民宿", "景点", "路线", "攻略", "探店");
    }

    protected boolean containsAny(String text, String... terms) {
        if (text == null || text.isBlank()) { return false; }
        for (String term : terms) {
            if (text.contains(term)) { return true; }
        }
        return false;
    }


    private boolean rawResultRelevant(SearchQuery query, ResearchSource source) {
        String text = ((source.getTitle() == null ? "" : source.getTitle()) + " "
                + (source.getSnippet() == null ? "" : source.getSnippet()) + " "
                + (source.getUrl() == null ? "" : source.getUrl())).toLowerCase();
        String roundText = ((query.getKeyword() == null ? "" : query.getKeyword()) + " "
                + (query.getRound() == null ? "" : query.getRound()) + " "
                + (query.getPurpose() == null ? "" : query.getPurpose()));

        if (containsAny(text, "baike.baidu.com", "wikipedia.org", "百科")) { return false; }
        if (("食".equals(query.getDirection()) || "住".equals(query.getDirection()))
                && containsAny(text, "政府门户", "人民政府", ".gov.cn", "政府网")) { return false; }

        if ("食".equals(query.getDirection()) || containsAny(roundText, "餐", "美食", "小吃", "特色菜", "必吃", "老字号", "早餐", "午餐", "晚餐", "探店")) {
            String titleAndUrl = ((source.getTitle() == null ? "" : source.getTitle()) + " " + (source.getUrl() == null ? "" : source.getUrl())).toLowerCase();
            if (genericTravelTitle(source.getTitle()) && !containsAny(titleAndUrl, "餐", "美食", "小吃", "特色菜", "必吃", "老字号", "饭店", "餐厅", "早餐", "午餐", "晚餐", "探店", "夜市")) {
                return false;
            }
            return containsAny(text, "餐", "美食", "小吃", "特色菜", "必吃", "老字号", "饭店", "餐厅", "早餐", "午餐", "晚餐", "探店", "夜市", "大众点评", "高德", "马蜂窝", "携程", "小红书", "抖音", "douyin", "xiaohongshu")
                    || (source.getTitle() != null && !source.getTitle().isBlank() && !genericTravelTitle(source.getTitle()) && source.getTitle().length() <= 30);
        }
        if ("住".equals(query.getDirection()) || containsAny(roundText, "住宿", "酒店", "民宿", "宾馆", "客栈")) {
            return containsAny(text, "住宿", "酒店", "民宿", "宾馆", "客栈", "预订", "携程", "飞猪", "去哪儿", "booking", "agoda", "位置", "交通便利", "小红书", "抖音", "douyin", "xiaohongshu")
                    || containsAny(text, "攻略", "推荐", "价格", "评分", "评价");
        }

        if (containsAny(roundText, "天气", "穿衣")) {
            return containsAny(text, "天气", "穿衣", "气温", "温度", "降雨", "下雨", "风力", "湿度");
        }
        if (containsAny(roundText, "交通", "地铁", "公交", "打车", "自驾")) {
            return containsAny(text, "交通", "公交", "地铁", "打车", "自驾", "路线", "换乘", "高铁", "机场", "火车站");
        }
        if (containsAny(roundText, "开放", "预约", "限流", "门票", "船票")) {
            return containsAny(text, "开放", "预约", "限流", "门票", "船票", "闭园", "官方", ".gov.cn", "景区");
        }
        return containsAny(text, "旅游", "攻略", "路线", "景点", "游记", "避坑", "开放", "预约", "交通", "小红书", "抖音")
                || (source.getTitle() != null && !source.getTitle().isBlank() && !genericTravelTitle(source.getTitle()) && source.getTitle().length() <= 40);
    }

    private boolean genericTravelTitle(String title) {
        String value = title == null ? "" : title;
        return containsAny(value, "旅游攻略", "旅游必去", "十大景点", "超全攻略", "游记攻略", "全攻略");
    }

    protected String platformFromUrl(String url, String fallback) {
        if (url == null) return fallback;
        if (url.contains("xiaohongshu.com")) return "小红书";
        if (url.contains("douyin.com")) return "抖音";
        if (url.contains("amap.com")) return "高德";
        if (url.contains("mafengwo.cn")) return "马蜂窝";
        if (url.contains("ctrip.com")) return "携程";
        return fallback;
    }

    protected String encode(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }
}
