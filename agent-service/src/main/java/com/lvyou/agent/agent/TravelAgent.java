package com.lvyou.agent.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lvyou.agent.data.PoiDataStore;
import com.lvyou.agent.memory.TravelMemory;
import com.lvyou.agent.model.DayPlan;
import com.lvyou.agent.model.DailyScheduleItem;
import com.lvyou.agent.model.PoiInfo;
import com.lvyou.agent.model.RouteCandidate;
import com.lvyou.agent.model.RouteDecisionReport;
import com.lvyou.agent.model.RouteSegment;
import com.lvyou.agent.model.StayPlan;
import com.lvyou.agent.model.TripOption;
import com.lvyou.agent.model.TravelPlanRequest;
import com.lvyou.agent.model.TravelPlanResult;
import com.lvyou.agent.tool.CalcDistanceTool;
import com.lvyou.agent.tool.AmapPlaceTool;
import com.lvyou.agent.tool.SearchPoiTool;
import com.lvyou.agent.tool.ValidateRouteTool;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.model.Model;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.memory.compaction.CompactionConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 旅行规划 Agent — 核心 AI 推理引擎
 * <p>
 * 使用 AgentScope HarnessAgent (v2.0) 实现 ReAct 推理流程：
 * Thought → Action (调用Tools) → Observation → ... → Final Answer
 * <p>
 * HarnessAgent 是 ReActAgent 的企业级包装，提供：
 * - 工作区管理 (workspace)
 * - 对话压缩 (compaction)
 * - 长期记忆 (MEMORY.md)
 * - 会话持久化
 * <p>
 * 参考: https://java.agentscope.io/v2/zh/docs/quickstart.html
 */
@Slf4j
@Component
public class TravelAgent {

    private final Model chatModel;
    private final SearchPoiTool searchPoiTool;
    private final CalcDistanceTool calcDistanceTool;
    private final ValidateRouteTool validateRouteTool;
    private final AmapPlaceTool amapPlaceTool;
    private final TravelMemory travelMemory;
    private final PoiDataStore poiDataStore;
    private final ObjectMapper objectMapper;

    @Value("${agent.timeout:120000}")
    private long agentTimeout;

    public TravelAgent(Model chatModel,
                       SearchPoiTool searchPoiTool,
                       CalcDistanceTool calcDistanceTool,
                       ValidateRouteTool validateRouteTool,
                       AmapPlaceTool amapPlaceTool,
                       TravelMemory travelMemory,
                       PoiDataStore poiDataStore) {
        this.chatModel = chatModel;
        this.searchPoiTool = searchPoiTool;
        this.calcDistanceTool = calcDistanceTool;
        this.validateRouteTool = validateRouteTool;
        this.amapPlaceTool = amapPlaceTool;
        this.travelMemory = travelMemory;
        this.poiDataStore = poiDataStore;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 生成旅行路线（ReAct 模式）
     * <p>
     * 流程：
     * 1. 创建/获取会话
     * 2. 构建系统提示词（包含输出格式约束）
     * 3. 注册工具（POI搜索 + 距离计算 + 路线校验）
     * 4. 创建 HarnessAgent
     * 5. 发送用户消息，Agent 自主推理并调用工具
     * 6. 解析 JSON 结果
     */
    public TravelPlanResult generatePlan(TravelPlanRequest request) {
        String sessionId = travelMemory.getOrCreateSession(request.getSessionId());
        travelMemory.updatePreference(sessionId, request.getPreference(), request.getDays());

        log.info("🤖 [TravelAgent] 开始规划: from={}, to={}, days={}, preference={}",
                request.getFrom(), request.getTo(), request.getDays(), request.getPreference());

        try {
            if (shouldUseVerifiedPoiFastPath(request)) {
                log.info("🤖 [TravelAgent] 目的地已有可验证 POI，使用快速结构化路线生成，避免 ReAct 超时");
                TravelPlanResult result = buildFallbackPlan(request);
                if (result != null && result.getDays() != null && !result.getDays().isEmpty()) {
                    travelMemory.savePlan(sessionId, result);
                    travelMemory.recordConversation(sessionId, "user", buildUserMessage(request, sessionId));
                    travelMemory.recordConversation(sessionId, "assistant", "已基于本地可验证 POI 快速生成结构化行程。");
                }
                return result;
            }

            // 1. 构建系统提示词
            String sysPrompt = buildSystemPrompt(request, sessionId);

            // 2. 注册工具（每次调用新建 Toolkit 保证线程安全）
            Toolkit toolkit = new Toolkit();
            toolkit.registerTool(searchPoiTool);
            toolkit.registerTool(calcDistanceTool);
            toolkit.registerTool(validateRouteTool);
            toolkit.registerTool(amapPlaceTool);

            // 3. 创建 HarnessAgent
            HarnessAgent agent = HarnessAgent.builder()
                    .name("TravelPlanner")
                    .sysPrompt(sysPrompt)
                    .model(chatModel)
                    .toolkit(toolkit)
                    .workspace(Paths.get(".agentscope/workspace/travel"))
                    .compaction(CompactionConfig.builder()
                            .triggerMessages(20)
                            .keepMessages(5)
                            .build())
                    .build();

            // 4. 构建用户消息
            String userMessage = buildUserMessage(request, sessionId);

            // 5. 创建运行时上下文
            RuntimeContext ctx = RuntimeContext.builder()
                    .sessionId(sessionId)
                    .userId("travel-user")
                    .build();

            log.info("🤖 [TravelAgent] 发送请求到 LLM (ReAct循环, 超时{}秒)...", agentTimeout / 1000);
            var response = agent.call(new UserMessage(userMessage), ctx)
                    .block(Duration.ofMillis(agentTimeout));

            String responseText = response != null ? response.getTextContent() : "";
            log.info("🤖 [TravelAgent] LLM响应长度: {} 字符", responseText != null ? responseText.length() : 0);
            log.debug("🤖 [TravelAgent] LLM原始响应:\n{}", responseText);

            // 6. 解析JSON结果
            TravelPlanResult result = parseResponse(responseText, request);

            // 7. 如果解析成功，保存到记忆
            if (result != null && result.getDays() != null && !result.getDays().isEmpty()) {
                travelMemory.savePlan(sessionId, result);
                travelMemory.recordConversation(sessionId, "user", userMessage);
                travelMemory.recordConversation(sessionId, "assistant", responseText);
            }

            return result;

        } catch (Exception e) {
            log.error("🤖 [TravelAgent] 规划失败: {}", e.getMessage(), e);
            return buildFallbackPlan(request);
        }
    }

    private boolean shouldUseVerifiedPoiFastPath(TravelPlanRequest request) {
        if (request == null || request.getTo() == null || request.getTo().isBlank()) {
            return false;
        }
        List<PoiInfo> candidates = poiDataStore.search(request.getTo(), request.getPreference());
        if (candidates.isEmpty()) {
            candidates = poiDataStore.getByCity(request.getTo());
        }
        long verifiedCount = candidates.stream()
                .filter(p -> belongsToDestination(p, request.getTo()))
                .filter(p -> p.getName() != null && p.getLat() != null && p.getLng() != null)
                .count();
        String researchContext = request.getResearchContext() == null ? "" : request.getResearchContext();
        boolean researchAlreadyHasPoiEvidence = researchContext.contains("verified_poi_seed")
                || researchContext.contains("本地可验证POI")
                || researchContext.contains("local-poi://");
        return verifiedCount >= 3 && (Boolean.TRUE.equals(request.getOnlineResearch()) || researchAlreadyHasPoiEvidence);
    }

    /**
     * 构建系统提示词
     * <p>
     * 关键约束：
     * - 必须输出纯 JSON
     * - 必须包含经纬度
     * - 必须调用工具搜索 POI
     * - 必须调用工具计算距离
     */
    private String buildSystemPrompt(TravelPlanRequest request, String sessionId) {
        String userIdeaSection = "";
        if (request.getUserIdea() != null && !request.getUserIdea().isBlank()) {
            userIdeaSection = String.format("""
                    
                    ## 用户额外要求（必须严格遵守）
                    %s
                    """, request.getUserIdea());
        }

        String researchSection = "";
        if (Boolean.TRUE.equals(request.getOnlineResearch())) {
            researchSection = String.format("""
                    
                    ## 联网实时调研上下文（必须优先参考）
                    %s
                    
                    使用规则：
                    - 只能使用后端 Tool Gateway 清洗后的 RAG 证据，不要自行假设你访问过抖音、小红书或其它外部平台。
                    - 优先选择多个来源反复出现、与用户偏好一致、且能通过 search_poi 验证坐标的景点/街区/餐饮动线。
                    - evidenceType 为 search_entry_only 的来源只能作为人工复核入口，不能当作事实依据。
                    - 如果来源状态不是 ok 或 rag_public_search，只能把这些条目当作搜索入口和待验证方向，不能声称已经读取了抖音或小红书正文。
                    - 不要编造平台笔记、点赞数、发布日期或用户评价。
                    - 路线仍必须经过 search_poi 获取真实经纬度，不能只凭网页标题入选。
                    - 对可能闭园、限流、施工、预约的地点，在 description 中提示“出行前需复核开放状态”。
                    """, request.getResearchContext() == null ? "无可用联网调研结果。" : request.getResearchContext());
        }

        return String.format("""
                你是一个专业的旅行路线规划师。你的任务是为用户规划 %s 到 %s 的 %d 天旅行路线。
                用户偏好：%s。%s%s
                
                ## 你必须使用的工具
                1. search_poi(city, keyword) - 搜索景点POI，获取名称、经纬度、停留时间
                2. calc_distance(lat1, lng1, lat2, lng2) - 计算两个景点间的距离和驾车时间
                3. validate_route(total_drive_minutes, total_stay_minutes, poi_count) - 校验路线是否合理
                4. search_amap_place(city, keyword, lat, lng) - 查询真实餐厅、酒店、交通点等高德地点
                5. search_amap_weather(city) - 查询实时天气，用于穿衣和交通风险判断
                
                ## 你必须遵循的流程
                1. 先分析用户需求和 RAG 证据，给出至少2种路线候选思路：顺路效率优先、体验丰富优先、轻松低强度。
                2. 再用 search_poi 搜索候选景点，必须按地理聚类分天，不能跨区域来回折返。
                3. 对每个候选路线用 calc_distance 计算相邻景点间距离，选择总移动时间更短且体验完整的方案。
                4. 用 search_amap_place 查询每天午餐、晚餐、住宿的真实推荐地点；餐厅/酒店不能只写“周边”。
                5. 用 search_amap_weather 查询目的地天气，生成穿衣和风险提醒。
                6. 最后用 validate_route 校验每天的路线，若不合理必须减少景点或改顺序。
                
                ## 输出格式要求（严格遵守）
                你必须输出纯 JSON，不要包含任何解释文字、markdown代码块标记。
                JSON 结构如下：
                {
                  "days": [
                    {
                      "day": 1,
                      "theme": "当日主题",
                      "pois": [
                        {
                          "name": "景点名称",
                          "lat": 39.9042,
                          "lng": 116.4074,
                          "stay_time": 120,
                          "description": "景点简介",
                          "imageUrl": "可用图片URL，如没有则留空",
                          "reason": "为什么推荐这里",
                          "tags": "世界遗产,古建,拍照",
                          "bestFor": "适合人群或玩法",
                          "tips": "预约、开放状态、交通提醒"
                        }
                      ],
                      "segments": [
                        {
                          "index": 1,
                          "from": "前一个景点",
                          "to": "后一个景点",
                          "distance": 12.5,
                          "drive_time": 25,
                          "summary": "第1段：从A到B，建议驾车/打车"
                        }
                      ],
                      "options": [
                        {
                          "name": "轻松版",
                          "style": "少走路",
                          "summary": "减少跨城移动，适合家庭",
                          "tradeOff": "景点数量少，但体力压力低",
                          "suitableFor": "亲子、老人",
                          "poiNames": "景点A、景点B"
                        }
                      ],
                      "schedule": [
                        {
                          "time_range": "07:30-08:10",
                          "period": "早上",
                          "type": "早餐",
                          "title": "在酒店周边吃早餐",
                          "location": "住宿区域周边",
                          "description": "选择当地早餐或便利早餐，避免空腹赶路。",
                          "transport": "步行",
                          "duration_minutes": 40,
                          "cost_hint": "约20-40元/人",
                          "tips": "旺季建议提前出门，给首段交通预留缓冲。"
                        },
                        {
                          "time_range": "08:10-09:00",
                          "period": "早上",
                          "type": "交通",
                          "title": "前往上午第一站",
                          "location": "景点A",
                          "description": "从住宿区域出发，优先选择打车/自驾/公共交通中更稳定的方式。",
                          "transport": "打车或自驾",
                          "duration_minutes": 50,
                          "cost_hint": "以实时路况为准",
                          "tips": "出发前复核景区开放、预约和天气。"
                        }
                      ],
                      "stay": {
                        "area": "推荐住宿区域",
                        "hotelType": "舒适型酒店或民宿",
                        "reason": "靠近当日最后一站或次日出发方向，减少折返。",
                        "checkInTip": "建议提前确认停车、早餐和最晚入住时间。",
                        "transportTip": "优先选择靠近主干路或公共交通的住宿点。"
                      },
                      "clothingTips": "根据天气穿舒适鞋，山区或夜间准备外套。",
                      "dailyTransport": "当日以打车/自驾衔接为主，短距离步行。",
                      "budgetHint": "餐饮、门票和交通预算需按实时价格复核。",
                      "distance": 50.5,
                      "drive_time": 75
                    }
                  ],
                  "options": [
                    {
                      "name": "深度古建线",
                      "style": "文化密度高",
                      "summary": "主打世界遗产、古城和古建",
                      "tradeOff": "路程更长，但信息量更足",
                      "suitableFor": "第一次到访、历史文化爱好者",
                      "poiNames": "景点A、景点B、景点C"
                    }
                  ]
                }
                
                重要规则：
                - 每天3-6个景点，按地理位置顺序排列（由近到远）；如果目的地数据不足，必须提供至少2个备选玩法说明，帮助用户权衡。
                - stay_time 单位为分钟（每个景点60-180分钟）
                - distance 为该天总行车距离（公里）
                - drive_time 为该天总驾车时间（分钟）
                - segments 必须表达前后顺序，例如“第1段：悬空寺 → 云冈石窟”
                - options 必须表达不同玩法的取舍，不能只是重复主路线
                - 必须把“为什么这条路线更优”写进 dailyTransport 或 options.summary：例如少折返、同区域串联、先远后近/先近后远。
                - schedule 是最重要的执行计划，每天至少 8 条，从 07:30 到 21:30 覆盖早上、上午、中午、下午、傍晚、晚上、夜间。
                - schedule 每天必须包含：早餐、上午交通、上午景点、午餐、下午景点、晚餐、夜间活动或休息、住宿/入住提醒。
                - schedule 不能只写景点，要覆盖衣食住行：早餐/午餐/晚餐要推荐具体餐厅或餐饮街区，住宿要推荐具体酒店或住宿区域，交通要说明衔接方式和耗时。
                - stay、clothingTips、dailyTransport、budgetHint 每天都必须填写，住宿区域要靠近当日最后一站或次日出发方向。
                - 经纬度必须是真实坐标
                - 输出必须是合法JSON，不要用markdown代码块包裹
                """,
                request.getFrom(), request.getTo(), request.getDays(),
                request.getPreference(), userIdeaSection, researchSection);
    }

    /**
     * 构建用户消息
     */
    private String buildUserMessage(TravelPlanRequest request, String sessionId) {
        String history = travelMemory.getDestinationAwareSummary(sessionId, request.getTo());
        String userIdeaLine = "";
        if (request.getUserIdea() != null && !request.getUserIdea().isBlank()) {
            userIdeaLine = String.format("- 额外要求：%s\n", request.getUserIdea());
        }
        String onlineLine = Boolean.TRUE.equals(request.getOnlineResearch())
                ? "- 联网规划：已启用，请结合联网调研上下文做实时路线取舍。\n"
                : "- 联网规划：未启用。\n";

        return String.format("""
                请为我规划旅行路线：
                - 出发地：%s
                - 目的地：%s
                - 天数：%d 天
                - 偏好：%s
                %s%s
                ## 会话记忆
                %s

                注意：会话记忆只能用于理解用户偏好，不能复用旧目的地的景点、餐厅、酒店或路线。出发地只用于交通衔接，不能作为目的地景点推荐。

                请严格按照系统提示的流程，使用工具搜索景点、计算距离、校验路线，最后输出JSON结果。
                记住：直接输出JSON，不要用markdown代码块。
                """,
                request.getFrom(), request.getTo(), request.getDays(),
                request.getPreference(), onlineLine, userIdeaLine, history);
    }

    /**
     * 解析 LLM 返回的 JSON
     */
    private TravelPlanResult parseResponse(String responseText, TravelPlanRequest request) {
        if (responseText == null || responseText.isBlank()) {
            log.warn("⚠️ LLM返回为空 (可能超时)");
            return buildFallbackPlan(request);
        }

        try {
            // 尝试提取 JSON（处理可能的 markdown 代码块）
            String json = extractJson(responseText);
            log.debug("提取的JSON: {}", json);

            TravelPlanResult result = objectMapper.readValue(json, TravelPlanResult.class);
            result.setFrom(request.getFrom());
            result.setTo(request.getTo());
            result.setTotalDays(request.getDays());
            result.setPreference(request.getPreference());
            enrichPlan(result, request);

            log.info("✅ 成功解析路线: {} 天", result.getDays() != null ? result.getDays().size() : 0);
            return result;

        } catch (JsonProcessingException e) {
            log.error("❌ JSON解析失败: {}", e.getMessage());
            log.error("原始响应: {}", responseText);
            return buildFallbackPlan(request);
        }
    }

    /**
     * 从LLM响应中提取JSON（处理可能的markdown代码块）
     */
    private String extractJson(String text) {
        String trimmed = text.trim();

        // 移除 markdown 代码块标记
        if (trimmed.startsWith("```json")) {
            trimmed = trimmed.substring(7);
        } else if (trimmed.startsWith("```")) {
            trimmed = trimmed.substring(3);
        }
        if (trimmed.endsWith("```")) {
            trimmed = trimmed.substring(0, trimmed.length() - 3);
        }

        // 找到第一个 { 和最后一个 }
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1);
        }

        return trimmed;
    }

    /**
     * 基于本地 POI 数据构建兜底路线。
     *
     * 兜底路线仍必须来自真实 POI 数据，不能返回泛化假坐标。
     */
    private TravelPlanResult buildFallbackPlan(TravelPlanRequest request) {
        log.warn("⚠️ 使用本地POI数据生成兜底路线");

        List<PoiInfo> candidates = poiDataStore.search(request.getTo(), request.getPreference());
        if (candidates.isEmpty()) {
            candidates = poiDataStore.getByCity(request.getTo());
        }
        candidates = candidates.stream()
                .filter(p -> belongsToDestination(p, request.getTo()))
                .filter(p -> p.getName() != null && p.getLat() != null && p.getLng() != null)
                .toList();

        if (candidates.isEmpty()) {
            candidates = searchAmapFallbackPois(request);
        }
        if (candidates.isEmpty()) {
            return buildInsufficientDataPlan(request);
        }

        List<List<PoiInfo>> dayGroups = splitIntoOptimizedDays(candidates, request.getDays(), request.getDays() <= 1 ? 5 : 4);
        List<DayPlan> days = new ArrayList<>();
        for (int d = 0; d < request.getDays(); d++) {
            List<PoiInfo> dayPois = new ArrayList<>();
            List<PoiInfo> sourcePois = dayGroups.isEmpty() ? candidates : dayGroups.get(d % dayGroups.size());
            for (PoiInfo source : sourcePois) {
                dayPois.add(PoiInfo.builder()
                        .name(source.getName())
                        .lat(source.getLat())
                        .lng(source.getLng())
                        .stayTime(source.getStayTime() != null ? source.getStayTime() : 90)
                        .description(source.getDescription())
                        .imageUrl(source.getImageUrl())
                        .reason(source.getReason())
                        .tags(source.getTags())
                        .bestFor(source.getBestFor())
                        .tips(source.getTips())
                        .build());
            }
            dayPois = optimizeDayRoute(dayPois);

            double distance = calculatePathDistance(dayPois);
            int driveTime = (int) Math.round(distance / 40.0 * 60);
            List<RouteSegment> segments = buildSegments(dayPois);

            days.add(DayPlan.builder()
                    .day(d + 1)
                    .theme(request.getPreference() + "路线")
                    .pois(dayPois)
                    .segments(segments)
                    .options(buildDayOptions(dayPois, request.getPreference()))
                    .schedule(buildDailySchedule(dayPois, segments, request, d + 1))
                    .stay(buildStayPlan(dayPois, request, d + 1))
                    .clothingTips(buildClothingTips(request))
                    .dailyTransport(buildDailyTransport(segments))
                    .budgetHint("预算需按实时门票、餐饮和交通价格复核；建议预留10%-20%机动费用。")
                    .distance(Math.round(distance * 10.0) / 10.0)
                    .driveTime(driveTime)
                    .build());
        }

        TravelPlanResult result = TravelPlanResult.builder()
                .from(request.getFrom())
                .to(request.getTo())
                .totalDays(request.getDays())
                .preference(request.getPreference())
                .days(days)
                .options(buildTripOptions(candidates, request.getPreference()))
                .build();
        result.setRouteDecisionReport(buildRouteDecisionReport(result, request, candidates));
        return result;
    }

    private TravelPlanResult buildInsufficientDataPlan(TravelPlanRequest request) {
        log.warn("⚠️ 目的地 {} 暂无可验证 POI，返回结构化数据不足结果", request.getTo());
        int totalDays = Math.max(1, request.getDays());
        List<DayPlan> days = new ArrayList<>();
        for (int d = 0; d < totalDays; d++) {
            int day = d + 1;
            days.add(DayPlan.builder()
                    .day(day)
                    .theme("第 " + day + " 天｜地图点位待确认")
                    .pois(List.of())
                    .segments(List.of())
                    .options(List.of())
                    .schedule(buildInsufficientDataSchedule(request, day))
                    .stay(StayPlan.builder()
                            .area(request.getTo())
                            .hotelType("建议优先选择交通便利片区")
                            .reason("当前没有拿到足够可靠的酒店坐标，因此先给出住宿区域方向，不强行推荐具体酒店。")
                            .checkInTip("可以补充更具体的区县、景点或预算后重新规划，系统会重新核验地图点位。")
                            .transportTip("住宿位置需结合最终确认的景点、车站和餐饮点再决定。")
                            .build())
                    .clothingTips("暂未获取到目的地天气与实地行程数据，请出行前按官方天气预报复核穿衣。")
                    .dailyTransport("地图点位暂未确认；系统不会使用出发地或旧城市景点凑路线。")
                    .budgetHint("餐饮、住宿与交通价格需要在点位确认后重新估算。")
                    .distance(0.0)
                    .driveTime(0)
                    .build());
        }

        return TravelPlanResult.builder()
                .from(request.getFrom())
                .to(request.getTo())
                .totalDays(totalDays)
                .preference(request.getPreference())
                .days(days)
                .options(List.of(TripOption.builder()
                        .name("补充目的地信息后重新规划")
                        .style("真实点位优先")
                        .summary("已完成联网调研，但地图坐标还不够可靠，暂不绘制路线。")
                        .tradeOff("不会生成看似完整但坐标不可靠的路线；补充区县、核心景点或预算后可重新生成。")
                        .suitableFor("需要真实性、可靠性和可执行性的旅行计划")
                        .poiNames("地图点位待确认")
                        .build()))
                .routeDecisionReport(buildInsufficientRouteDecisionReport(request))
                .build();
    }

    private List<DailyScheduleItem> buildInsufficientDataSchedule(TravelPlanRequest request, int day) {
        String destination = request.getTo();
        return List.of(
                DailyScheduleItem.builder()
                        .timeRange("08:30-09:00")
                        .period("早上")
                        .type("数据校验")
                        .title("确认目的地范围")
                        .location(destination)
                        .description("当前目的地的地图点位还不够可靠，建议确认具体城市、区县或核心片区。")
                        .tips("系统不会把出发地景点或历史目的地内容混入本次规划。")
                        .durationMinutes(30)
                        .build(),
                DailyScheduleItem.builder()
                        .timeRange("09:00-12:00")
                        .period("上午")
                        .type("景点")
                        .title("确认景点地图点位")
                        .location(destination)
                        .description("缺少足够可靠的景点坐标和开放信息，暂不生成地图路线。")
                        .tips("补充更明确的景点、区县或预算后可重新规划。")
                        .durationMinutes(180)
                        .build(),
                DailyScheduleItem.builder()
                        .timeRange("12:00-13:30")
                        .period("中午")
                        .type("午餐")
                        .title("确认餐饮候选")
                        .location(destination)
                        .description("缺少目的地餐饮来源，暂不推荐具体餐厅，避免生成不可用结果。")
                        .durationMinutes(90)
                        .build(),
                DailyScheduleItem.builder()
                        .timeRange("14:00-17:30")
                        .period("下午")
                        .type("行程")
                        .title("确认路线衔接")
                        .location(destination)
                        .description("缺少可执行坐标与交通衔接信息，暂不计算路线距离。")
                        .durationMinutes(210)
                        .build(),
                DailyScheduleItem.builder()
                        .timeRange("18:00-19:30")
                        .period("晚上")
                        .type("晚餐")
                        .title("确认晚餐候选")
                        .location(destination)
                        .description("餐饮推荐需结合真实来源和地图 POI 复核后再输出。")
                        .durationMinutes(90)
                        .build(),
                DailyScheduleItem.builder()
                        .timeRange("20:00-21:00")
                        .period("夜间")
                        .type("住宿")
                        .title("确认住宿区域")
                        .location(destination)
                        .description("住宿区域暂以目的地为范围，具体酒店和片区需要更多真实来源。")
                        .tips("第 " + day + " 天不会绘制未经核验的虚假点位。")
                        .durationMinutes(60)
                        .build());
    }

    private RouteDecisionReport buildInsufficientRouteDecisionReport(TravelPlanRequest request) {
        RouteCandidate candidate = RouteCandidate.builder()
                .name("地图点位待确认")
                .strategy("真实点位优先")
                .daysSummary("当前没有足够可靠的 " + request.getTo() + " 地图坐标点")
                .totalDistance(0.0)
                .totalDriveTime(0)
                .score(0)
                .tradeOff("暂不绘制地图路线，避免旧目的地、出发地或错误坐标污染结果。")
                .selected(true)
                .build();
        return RouteDecisionReport.builder()
                .selectedStrategy("地图点位未确认")
                .summary("已完成联网调研，但当前没有拿到足够可靠的目的地地图坐标，因此暂不绘制路线。")
                .totalDistance(0.0)
                .totalDriveTime(0)
                .routeScore(0)
                .optimizationNotes("请补充更具体的区县、核心景点或预算后重新规划；地图层不会绘制未经核验的坐标路线。")
                .candidates(List.of(candidate))
                .build();
    }

    private List<PoiInfo> searchAmapFallbackPois(TravelPlanRequest request) {
        List<PoiInfo> pois = new ArrayList<>();
        for (String keyword : amapFallbackKeywords(request)) {
            try {
                String text = amapPlaceTool.searchPlace(request.getTo(), keyword, null, null);
                pois.addAll(parseAmapPois(text, request.getTo(), keyword));
                if (pois.size() >= Math.max(6, request.getDays() * 4)) {
                    break;
                }
            } catch (Exception e) {
                log.warn("高德兜底 POI 查询失败 keyword={}: {}", keyword, e.getMessage());
            }
        }
        return pois.stream()
                .filter(p -> p.getName() != null && p.getLat() != null && p.getLng() != null)
                .collect(Collectors.collectingAndThen(
                        Collectors.toMap(PoiInfo::getName, p -> p, (a, b) -> a, java.util.LinkedHashMap::new),
                        map -> new ArrayList<>(map.values())));
    }

    private List<String> amapFallbackKeywords(TravelPlanRequest request) {
        String preference = request.getPreference() == null ? "" : request.getPreference();
        List<String> keywords = new ArrayList<>();
        if (preference.contains("美食")) {
            keywords.add("特色美食");
            keywords.add("老字号餐厅");
        }
        if (preference.contains("自然")) {
            keywords.add("风景名胜");
            keywords.add("公园");
        }
        keywords.add(preference.isBlank() ? "旅游景点" : preference);
        keywords.add("博物馆");
        keywords.add("风景名胜");
        keywords.add("酒店");
        return keywords.stream().filter(k -> k != null && !k.isBlank()).distinct().toList();
    }

    private List<PoiInfo> parseAmapPois(String text, String city, String keyword) {
        if (text == null || text.isBlank() || !text.contains("坐标:")) {
            return List.of();
        }
        List<PoiInfo> pois = new ArrayList<>();
        for (String line : text.split("\\R")) {
            String value = line.trim();
            if (!value.startsWith("- ") || !value.contains("坐标:")) {
                continue;
            }
            String name = value.substring(2, value.indexOf("|")).trim();
            String location = between(value, "坐标:", "| 类型:");
            String type = between(value, "类型:", "| 评分:");
            String[] parts = location.split(",");
            if (parts.length != 2) {
                continue;
            }
            try {
                double lng = Double.parseDouble(parts[0].trim());
                double lat = Double.parseDouble(parts[1].trim());
                pois.add(PoiInfo.builder()
                        .name(name)
                        .city(city)
                        .category(keyword)
                        .lat(lat)
                        .lng(lng)
                        .stayTime(type.contains("酒店") ? 0 : 90)
                        .description("来自高德 WebService 的真实地点结果，类型：" + type)
                        .reason("本地 POI 不足时用于补充真实地图点位。")
                        .tags(type)
                        .tips("开放时间、预约和价格仍需出行前复核。")
                        .build());
            } catch (NumberFormatException ignored) {
                // 忽略坐标格式异常的单条结果。
            }
        }
        return pois;
    }

    private String between(String text, String start, String end) {
        int startIndex = text.indexOf(start);
        if (startIndex < 0) {
            return "";
        }
        startIndex += start.length();
        int endIndex = text.indexOf(end, startIndex);
        if (endIndex < 0) {
            endIndex = text.length();
        }
        return text.substring(startIndex, endIndex).trim();
    }

    private void enrichPlan(TravelPlanResult result, TravelPlanRequest request) {
        if (result == null || result.getDays() == null) {
            return;
        }

        List<PoiInfo> cityPois = poiDataStore.getByCity(request.getTo());
        cityPois = destinationPois(cityPois, request.getTo());

        rebalanceDaysByGeography(result, request, cityPois);

        for (DayPlan day : result.getDays()) {
            if (day.getPois() == null) {
                day.setPois(new ArrayList<>());
            }

            List<PoiInfo> enrichedPois = new ArrayList<>();
            for (PoiInfo poi : day.getPois()) {
                PoiInfo enriched = enrichPoi(poi, cityPois, request.getTo());
                if (acceptableForDestination(enriched, request.getTo(), cityPois)) {
                    enrichedPois.add(enriched);
                }
            }

            int minPoiCount = request.getDays() <= 1 ? 5 : 4;
            for (PoiInfo candidate : cityPois) {
                if (enrichedPois.size() >= minPoiCount) {
                    break;
                }
                boolean exists = enrichedPois.stream()
                        .anyMatch(p -> Objects.equals(p.getName(), candidate.getName()));
                if (!exists && candidate.getLat() != null && candidate.getLng() != null) {
                    enrichedPois.add(enrichPoi(candidate, cityPois, request.getTo()));
                }
            }

            enrichedPois = optimizeDayRoute(enrichedPois);
            day.setPois(enrichedPois);
            day.setSegments(buildSegments(enrichedPois));
            if (day.getOptions() == null || day.getOptions().size() < 3) {
                day.setOptions(buildDayOptions(enrichedPois, request.getPreference()));
            }
            day.setDailyTransport(buildDailyTransport(day.getSegments()) + " 已按空间顺路性重新排序，减少同日景点间折返。");
            double optimizedDistance = calculatePathDistance(enrichedPois);
            day.setDistance(Math.round(optimizedDistance * 10.0) / 10.0);
            day.setDriveTime((int) Math.round(day.getDistance() / 40.0 * 60));
            if (day.getSchedule() == null || day.getSchedule().size() < 8 || !hasCoreScheduleTypes(day.getSchedule())) {
                day.setSchedule(buildDailySchedule(enrichedPois, day.getSegments(), request,
                        day.getDay() == null ? 1 : day.getDay()));
            }
            if (day.getStay() == null || day.getStay().getArea() == null || day.getStay().getArea().isBlank()) {
                day.setStay(buildStayPlan(enrichedPois, request, day.getDay() == null ? 1 : day.getDay()));
            }
            if (day.getClothingTips() == null || day.getClothingTips().isBlank()) {
                day.setClothingTips(buildClothingTips(request));
            }
            if (day.getBudgetHint() == null || day.getBudgetHint().isBlank()) {
                day.setBudgetHint("餐饮、门票、住宿和交通价格会随季节变化，预订前请用官方渠道复核。");
            }
        }

        if (result.getOptions() == null || result.getOptions().size() < 3) {
            result.setOptions(buildTripOptions(cityPois, request.getPreference()));
        }
        result.setRouteDecisionReport(buildRouteDecisionReport(result, request, cityPois));
    }

    private RouteDecisionReport buildRouteDecisionReport(TravelPlanResult result,
                                                         TravelPlanRequest request,
                                                         List<PoiInfo> cityPois) {
        List<DayPlan> selectedDays = result == null || result.getDays() == null
                ? List.of()
                : result.getDays();
        List<PoiInfo> basePois = collectDecisionPois(selectedDays, cityPois);
        int safeDays = Math.max(1, request.getDays());

        RouteCandidate selected = candidateFromDays(
                "当前采用方案",
                "地理聚类 + 日内顺路优化",
                selectedDays,
                "优先把相近点放在同一天，并对每天内部顺序做最近邻与局部优化，减少跨区域折返。",
                true);

        RouteCandidate compact = candidateFromGroups(
                "紧凑顺路线",
                "距离最短",
                splitIntoOptimizedDays(basePois, safeDays, safeDays <= 1 ? 4 : 3),
                "进一步减少每日点位，移动更短，但会牺牲部分代表性景点。",
                false);

        RouteCandidate coverage = candidateFromGroups(
                "覆盖优先线",
                "景点覆盖",
                splitIntoOptimizedDays(basePois, safeDays, safeDays <= 1 ? 6 : 5),
                "尽量保留更多核心点位，信息量更足，但移动和体力压力更高。",
                false);

        RouteCandidate relaxed = candidateFromGroups(
                "低强度慢游线",
                "舒适优先",
                splitIntoOptimizedDays(basePois, safeDays, safeDays <= 1 ? 3 : 2),
                "每天减少点位，把时间留给餐饮、休息和不确定路况缓冲。",
                false);

        List<RouteCandidate> candidates = List.of(selected, compact, coverage, relaxed);
        return RouteDecisionReport.builder()
                .selectedStrategy(selected.getStrategy())
                .summary("已比较顺路效率、景点覆盖和低强度三类候选方案，最终选择兼顾代表性与移动成本的聚类顺路方案。")
                .totalDistance(selected.getTotalDistance())
                .totalDriveTime(selected.getTotalDriveTime())
                .routeScore(selected.getScore())
                .optimizationNotes("评分综合总里程、每日移动时间、点位密度和跨区域折返风险；餐饮、住宿、交通与天气提醒会在每天执行时间线中落地。")
                .candidates(candidates)
                .build();
    }

    private List<PoiInfo> collectDecisionPois(List<DayPlan> selectedDays, List<PoiInfo> cityPois) {
        List<PoiInfo> pois = new ArrayList<>();
        if (selectedDays != null) {
            selectedDays.stream()
                    .filter(Objects::nonNull)
                    .flatMap(day -> day.getPois() == null ? java.util.stream.Stream.empty() : day.getPois().stream())
                    .filter(p -> p.getName() != null && p.getLat() != null && p.getLng() != null)
                    .forEach(pois::add);
        }
        if (cityPois != null) {
            cityPois.stream()
                    .filter(p -> p.getName() != null && p.getLat() != null && p.getLng() != null)
                    .forEach(pois::add);
        }
        return pois.stream()
                .collect(Collectors.collectingAndThen(
                        Collectors.toMap(PoiInfo::getName, p -> p, (a, b) -> a, java.util.LinkedHashMap::new),
                        map -> new ArrayList<>(map.values())));
    }

    private RouteCandidate candidateFromGroups(String name,
                                               String strategy,
                                               List<List<PoiInfo>> groups,
                                               String tradeOff,
                                               boolean selected) {
        List<DayPlan> days = new ArrayList<>();
        for (int i = 0; i < groups.size(); i++) {
            List<PoiInfo> pois = optimizeDayRoute(groups.get(i));
            double distance = calculatePathDistance(pois);
            days.add(DayPlan.builder()
                    .day(i + 1)
                    .pois(pois)
                    .distance(Math.round(distance * 10.0) / 10.0)
                    .driveTime((int) Math.round(distance / 40.0 * 60))
                    .build());
        }
        return candidateFromDays(name, strategy, days, tradeOff, selected);
    }

    private RouteCandidate candidateFromDays(String name,
                                             String strategy,
                                             List<DayPlan> days,
                                             String tradeOff,
                                             boolean selected) {
        double totalDistance = days == null ? 0 : days.stream()
                .filter(Objects::nonNull)
                .mapToDouble(day -> day.getDistance() != null
                        ? day.getDistance()
                        : calculatePathDistance(day.getPois() == null ? List.of() : day.getPois()))
                .sum();
        int totalDriveTime = days == null ? 0 : days.stream()
                .filter(Objects::nonNull)
                .mapToInt(day -> day.getDriveTime() != null
                        ? day.getDriveTime()
                        : (int) Math.round(calculatePathDistance(day.getPois() == null ? List.of() : day.getPois()) / 40.0 * 60))
                .sum();
        int poiCount = days == null ? 0 : days.stream()
                .filter(Objects::nonNull)
                .mapToInt(day -> day.getPois() == null ? 0 : day.getPois().size())
                .sum();
        int score = routeScore(totalDistance, totalDriveTime, poiCount, selected);
        return RouteCandidate.builder()
                .name(name)
                .strategy(strategy)
                .daysSummary(daysSummary(days))
                .totalDistance(Math.round(totalDistance * 10.0) / 10.0)
                .totalDriveTime(totalDriveTime)
                .score(score)
                .tradeOff(tradeOff)
                .selected(selected)
                .build();
    }

    private int routeScore(double totalDistance, int totalDriveTime, int poiCount, boolean selected) {
        int score = 96;
        score -= Math.min(24, (int) Math.round(totalDistance / 40.0));
        score -= Math.min(18, totalDriveTime / 90);
        if (poiCount < 4) {
            score -= 8;
        }
        if (selected) {
            score += 3;
        }
        return Math.max(62, Math.min(98, score));
    }

    private String daysSummary(List<DayPlan> days) {
        if (days == null || days.isEmpty()) {
            return "暂无可用点位";
        }
        return days.stream()
                .filter(Objects::nonNull)
                .map(day -> "D" + (day.getDay() == null ? "" : day.getDay()) + " "
                        + joinPoiNames(day.getPois() == null ? List.of() : day.getPois()))
                .collect(Collectors.joining("；"));
    }

    private void rebalanceDaysByGeography(TravelPlanResult result, TravelPlanRequest request, List<PoiInfo> cityPois) {
        if (result.getDays() == null || result.getDays().isEmpty()) {
            return;
        }
        if (cityPois == null || cityPois.isEmpty()) {
            List<PoiInfo> externalPois = result.getDays().stream()
                    .filter(Objects::nonNull)
                    .flatMap(day -> day.getPois() == null ? java.util.stream.Stream.empty() : day.getPois().stream())
                    .filter(p -> acceptableForDestination(p, request.getTo(), List.of()))
                    .filter(p -> p.getName() != null && p.getLat() != null && p.getLng() != null)
                    .collect(Collectors.collectingAndThen(
                            Collectors.toMap(PoiInfo::getName, p -> p, (a, b) -> a, java.util.LinkedHashMap::new),
                            map -> new ArrayList<>(map.values())));
            if (!externalPois.isEmpty()) {
                List<List<PoiInfo>> groups = splitIntoOptimizedDays(externalPois, request.getDays(), request.getDays() <= 1 ? 5 : 4);
                for (int i = 0; i < result.getDays().size() && i < groups.size(); i++) {
                    DayPlan day = result.getDays().get(i);
                    day.setPois(groups.get(i));
                    day.setSegments(buildSegments(groups.get(i)));
                    day.setSchedule(null);
                    day.setStay(null);
                    day.setDistance(null);
                    day.setDriveTime(null);
                    day.setDailyTransport(null);
                }
                return;
            }
            for (DayPlan day : result.getDays()) {
                day.setPois(new ArrayList<>());
                day.setSegments(new ArrayList<>());
                day.setSchedule(buildDailySchedule(List.of(), List.of(), request,
                        day.getDay() == null ? 1 : day.getDay()));
                day.setStay(buildStayPlan(List.of(), request, day.getDay() == null ? 1 : day.getDay()));
                day.setDistance(0.0);
                day.setDriveTime(0);
                day.setDailyTransport("目的地暂无本地可验证 POI 数据；出发地只作为交通起点，不能混入景点路线。请开启可用地图/搜索服务后重新生成。");
            }
            return;
        }
        List<PoiInfo> allPois = result.getDays().stream()
                .filter(Objects::nonNull)
                .flatMap(day -> day.getPois() == null ? java.util.stream.Stream.empty() : day.getPois().stream())
                .map(poi -> enrichPoi(poi, cityPois, request.getTo()))
                .filter(p -> acceptableForDestination(p, request.getTo(), cityPois))
                .filter(p -> p.getName() != null && p.getLat() != null && p.getLng() != null)
                .collect(Collectors.collectingAndThen(
                        Collectors.toMap(PoiInfo::getName, p -> p, (a, b) -> a, java.util.LinkedHashMap::new),
                        map -> new ArrayList<>(map.values())));
        int minPoiCount = request.getDays() <= 1 ? 5 : 4;
        for (PoiInfo candidate : cityPois) {
            if (allPois.size() >= request.getDays() * minPoiCount) {
                break;
            }
            if (candidate.getName() == null || candidate.getLat() == null || candidate.getLng() == null) {
                continue;
            }
            boolean exists = allPois.stream().anyMatch(p -> Objects.equals(p.getName(), candidate.getName()));
            if (!exists) {
                allPois.add(enrichPoi(candidate, cityPois, request.getTo()));
            }
        }
        List<List<PoiInfo>> groups = splitIntoOptimizedDays(allPois, request.getDays(), minPoiCount);
        for (int i = 0; i < result.getDays().size() && i < groups.size(); i++) {
            DayPlan day = result.getDays().get(i);
            day.setPois(groups.get(i));
            day.setSegments(buildSegments(groups.get(i)));
            day.setSchedule(null);
            day.setStay(null);
            day.setDistance(null);
            day.setDriveTime(null);
            day.setDailyTransport(null);
        }
    }

    private PoiInfo enrichPoi(PoiInfo poi, List<PoiInfo> cityPois, String destination) {
        if (poi == null) {
            return PoiInfo.builder().build();
        }

        PoiInfo reference = cityPois.stream()
                .filter(p -> p.getName() != null && poi.getName() != null
                        && (p.getName().equals(poi.getName())
                        || p.getName().contains(poi.getName())
                        || poi.getName().contains(p.getName())))
                .findFirst()
                .orElse(null);

        if (reference == null && destinationCovered(cityPois)) {
            List<PoiInfo> matched = poiDataStore.searchAll(poi.getName());
            reference = matched.stream()
                    .filter(match -> belongsToDestination(match, destination))
                    .findFirst()
                    .orElse(null);
        }

        return PoiInfo.builder()
                .name(firstNonBlank(poi.getName(), reference == null ? null : reference.getName()))
                .lat(poi.getLat() != null ? poi.getLat() : reference == null ? null : reference.getLat())
                .lng(poi.getLng() != null ? poi.getLng() : reference == null ? null : reference.getLng())
                .stayTime(poi.getStayTime() != null ? poi.getStayTime() : reference == null ? 90 : reference.getStayTime())
                .description(firstNonBlank(poi.getDescription(), reference == null ? null : reference.getDescription()))
                .imageUrl(firstNonBlank(poi.getImageUrl(), reference == null ? null : reference.getImageUrl()))
                .reason(firstNonBlank(poi.getReason(), reference == null ? null : reference.getReason()))
                .tags(firstNonBlank(poi.getTags(), reference == null ? null : reference.getTags()))
                .bestFor(firstNonBlank(poi.getBestFor(), reference == null ? null : reference.getBestFor()))
                .tips(firstNonBlank(poi.getTips(), reference == null ? null : reference.getTips()))
                .city(firstNonBlank(poi.getCity(), reference == null ? null : reference.getCity()))
                .category(firstNonBlank(poi.getCategory(), reference == null ? null : reference.getCategory()))
                .build();
    }

    private List<PoiInfo> destinationPois(List<PoiInfo> pois, String destination) {
        if (pois == null) {
            return List.of();
        }
        return pois.stream()
                .filter(p -> belongsToDestination(p, destination))
                .toList();
    }

    private boolean destinationCovered(List<PoiInfo> cityPois) {
        return cityPois != null && !cityPois.isEmpty();
    }

    private boolean belongsToDestination(PoiInfo poi, String destination) {
        if (poi == null || destination == null || destination.isBlank()) {
            return false;
        }
        String city = normalizePlaceName(poi.getCity());
        String target = normalizePlaceName(destination);
        if (city.isBlank()) {
            return false;
        }
        return city.contains(target) || target.contains(city);
    }

    private boolean acceptableForDestination(PoiInfo poi, String destination, List<PoiInfo> cityPois) {
        if (poi == null || poi.getName() == null || poi.getLat() == null || poi.getLng() == null) {
            return false;
        }
        if (belongsToDestination(poi, destination)) {
            return true;
        }
        if (destinationCovered(cityPois)) {
            return false;
        }
        List<PoiInfo> knownMatches = poiDataStore.searchAll(poi.getName());
        if (knownMatches.stream().anyMatch(match -> !belongsToDestination(match, destination))) {
            return false;
        }
        if (knownMatches.isEmpty()) {
            return true;
        }
        String name = normalizePlaceName(poi.getName());
        String target = normalizePlaceName(destination);
        String description = normalizePlaceName(firstNonBlank(poi.getDescription(), "") + firstNonBlank(poi.getReason(), ""));
        return name.contains(target) || description.contains(target);
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

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second;
    }

    private List<RouteSegment> buildSegments(List<PoiInfo> pois) {
        List<RouteSegment> segments = new ArrayList<>();
        for (int i = 0; i < pois.size() - 1; i++) {
            PoiInfo from = pois.get(i);
            PoiInfo to = pois.get(i + 1);
            double distance = haversine(from.getLat(), from.getLng(), to.getLat(), to.getLng());
            int driveTime = (int) Math.max(10, Math.round(distance / 40.0 * 60));
            segments.add(RouteSegment.builder()
                    .index(i + 1)
                    .from(from.getName())
                    .to(to.getName())
                    .distance(Math.round(distance * 10.0) / 10.0)
                    .driveTime(driveTime)
                    .summary(String.format("第%d段：%s → %s，建议驾车或打车衔接", i + 1, from.getName(), to.getName()))
                    .build());
        }
        return segments;
    }

    private List<List<PoiInfo>> splitIntoOptimizedDays(List<PoiInfo> candidates, int days, int maxPerDay) {
        List<PoiInfo> unique = candidates.stream()
                .filter(p -> p.getName() != null && p.getLat() != null && p.getLng() != null)
                .collect(Collectors.collectingAndThen(
                        Collectors.toMap(PoiInfo::getName, p -> p, (a, b) -> a, java.util.LinkedHashMap::new),
                        map -> new ArrayList<>(map.values())));
        if (unique.isEmpty()) {
            return List.of();
        }

        if (days <= 1 || unique.size() <= Math.max(2, maxPerDay)) {
            return List.of(limitCompactRoute(optimizeDayRoute(unique), maxPerDay));
        }

        List<List<PoiInfo>> clusters = clusterByGeography(unique, Math.min(days, unique.size()));
        clusters = clusters.stream()
                .filter(group -> !group.isEmpty())
                .sorted(Comparator.comparingDouble(this::clusterCenterLat))
                .map(group -> limitCompactRoute(optimizeDayRoute(group), maxPerDay))
                .collect(Collectors.toCollection(ArrayList::new));

        if (clusters.size() >= days) {
            return clusters.stream().limit(days).toList();
        }

        List<PoiInfo> spatialOrder = nearestNeighborRoute(unique);
        List<List<PoiInfo>> groups = new ArrayList<>();
        int safeDays = Math.max(1, days);
        int safeMax = Math.max(2, maxPerDay);
        int index = 0;
        for (int day = 0; day < safeDays; day++) {
            int remainingPois = spatialOrder.size() - index;
            int remainingDays = safeDays - day;
            int count = Math.min(safeMax, Math.max(2, (int) Math.ceil(remainingPois / (double) remainingDays)));
            List<PoiInfo> group = new ArrayList<>();
            for (int i = 0; i < count && index < spatialOrder.size(); i++) {
                group.add(spatialOrder.get(index++));
            }
            if (group.isEmpty()) {
                group.add(spatialOrder.get(day % spatialOrder.size()));
            }
            groups.add(optimizeDayRoute(group));
        }
        return groups;
    }

    private List<List<PoiInfo>> clusterByGeography(List<PoiInfo> pois, int clusterCount) {
        List<PoiInfo> centers = initialCenters(pois, clusterCount);
        List<List<PoiInfo>> clusters = new ArrayList<>();
        for (int iter = 0; iter < 8; iter++) {
            clusters = new ArrayList<>();
            for (int i = 0; i < centers.size(); i++) {
                clusters.add(new ArrayList<>());
            }
            for (PoiInfo poi : pois) {
                int bestIndex = 0;
                double bestDistance = Double.MAX_VALUE;
                for (int i = 0; i < centers.size(); i++) {
                    PoiInfo center = centers.get(i);
                    double distance = haversine(poi.getLat(), poi.getLng(), center.getLat(), center.getLng());
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        bestIndex = i;
                    }
                }
                clusters.get(bestIndex).add(poi);
            }
            centers = recomputeCenters(clusters, centers);
        }
        return clusters;
    }

    private List<PoiInfo> initialCenters(List<PoiInfo> pois, int clusterCount) {
        List<PoiInfo> centers = new ArrayList<>();
        PoiInfo first = pois.stream()
                .min(Comparator.comparing(PoiInfo::getLat).thenComparing(PoiInfo::getLng))
                .orElse(pois.get(0));
        centers.add(first);
        while (centers.size() < clusterCount) {
            PoiInfo farthest = pois.stream()
                    .filter(p -> centers.stream().noneMatch(c -> Objects.equals(c.getName(), p.getName())))
                    .max(Comparator.comparingDouble(p -> centers.stream()
                            .mapToDouble(c -> haversine(p.getLat(), p.getLng(), c.getLat(), c.getLng()))
                            .min()
                            .orElse(0)))
                    .orElse(pois.get(centers.size() % pois.size()));
            centers.add(farthest);
        }
        return centers;
    }

    private List<PoiInfo> recomputeCenters(List<List<PoiInfo>> clusters, List<PoiInfo> oldCenters) {
        List<PoiInfo> centers = new ArrayList<>();
        for (int i = 0; i < clusters.size(); i++) {
            List<PoiInfo> group = clusters.get(i);
            if (group.isEmpty()) {
                centers.add(oldCenters.get(i));
                continue;
            }
            double avgLat = group.stream().mapToDouble(PoiInfo::getLat).average().orElse(oldCenters.get(i).getLat());
            double avgLng = group.stream().mapToDouble(PoiInfo::getLng).average().orElse(oldCenters.get(i).getLng());
            PoiInfo nearest = group.stream()
                    .min(Comparator.comparingDouble(p -> haversine(avgLat, avgLng, p.getLat(), p.getLng())))
                    .orElse(group.get(0));
            centers.add(nearest);
        }
        return centers;
    }

    private List<PoiInfo> limitCompactRoute(List<PoiInfo> route, int maxPerDay) {
        if (route.size() <= maxPerDay) {
            return route;
        }
        List<PoiInfo> best = route.subList(0, maxPerDay);
        double bestDistance = calculatePathDistance(best);
        for (int start = 1; start <= route.size() - maxPerDay; start++) {
            List<PoiInfo> window = route.subList(start, start + maxPerDay);
            double distance = calculatePathDistance(window);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = window;
            }
        }
        return new ArrayList<>(best);
    }

    private double clusterCenterLat(List<PoiInfo> group) {
        return group.stream().mapToDouble(PoiInfo::getLat).average().orElse(0);
    }

    private List<PoiInfo> optimizeDayRoute(List<PoiInfo> pois) {
        if (pois == null || pois.size() <= 2) {
            return pois == null ? new ArrayList<>() : new ArrayList<>(pois);
        }
        List<PoiInfo> route = nearestNeighborRoute(pois);
        boolean improved = true;
        while (improved) {
            improved = false;
            for (int i = 1; i < route.size() - 2; i++) {
                for (int j = i + 1; j < route.size() - 1; j++) {
                    double current = edgeDistance(route, i - 1, i) + edgeDistance(route, j, j + 1);
                    double swapped = edgeDistance(route, i - 1, j) + edgeDistance(route, i, j + 1);
                    if (swapped + 0.1 < current) {
                        java.util.Collections.reverse(route.subList(i, j + 1));
                        improved = true;
                    }
                }
            }
        }
        return route;
    }

    private List<PoiInfo> nearestNeighborRoute(List<PoiInfo> pois) {
        List<PoiInfo> remaining = pois.stream()
                .filter(p -> p.getLat() != null && p.getLng() != null)
                .collect(Collectors.toCollection(ArrayList::new));
        if (remaining.size() <= 1) {
            return remaining;
        }

        PoiInfo start = remaining.stream()
                .min(Comparator.comparing(PoiInfo::getLat).thenComparing(PoiInfo::getLng))
                .orElse(remaining.get(0));
        List<PoiInfo> route = new ArrayList<>();
        route.add(start);
        remaining.remove(start);

        while (!remaining.isEmpty()) {
            PoiInfo current = route.get(route.size() - 1);
            PoiInfo next = remaining.stream()
                    .min(Comparator.comparingDouble(p -> haversine(current.getLat(), current.getLng(), p.getLat(), p.getLng())))
                    .orElse(remaining.get(0));
            route.add(next);
            remaining.remove(next);
        }
        return route;
    }

    private double edgeDistance(List<PoiInfo> route, int a, int b) {
        PoiInfo from = route.get(a);
        PoiInfo to = route.get(b);
        return haversine(from.getLat(), from.getLng(), to.getLat(), to.getLng());
    }

    private List<DailyScheduleItem> buildDailySchedule(List<PoiInfo> pois,
                                                       List<RouteSegment> segments,
                                                       TravelPlanRequest request,
                                                       int dayIndex) {
        List<DailyScheduleItem> schedule = new ArrayList<>();
        PoiInfo first = getPoiAt(pois, 0);
        PoiInfo second = getPoiAt(pois, 1);
        PoiInfo third = getPoiAt(pois, 2);
        PoiInfo last = pois == null || pois.isEmpty() ? null : pois.get(pois.size() - 1);
        String firstName = poiName(first, request.getTo() + "核心景点");
        String secondName = poiName(second, firstName);
        String thirdName = poiName(third, secondName);
        String lastName = poiName(last, thirdName);
        String stayArea = buildStayArea(last, request);
        AmapRecommendation breakfast = findAmapRecommendation(request.getTo(), "早餐", first);
        AmapRecommendation lunch = findAmapRecommendation(request.getTo(), request.getPreference() + " 午餐 特色餐厅", first);
        AmapRecommendation dinner = findAmapRecommendation(request.getTo(), "晚餐 特色餐厅", last);
        AmapRecommendation hotel = findAmapRecommendation(request.getTo(), "酒店 住宿", last);
        if (hotel != null) {
            stayArea = hotel.name;
        }
        String segmentToFirst = segments == null || segments.isEmpty()
                ? "根据实时路况选择打车、自驾或公共交通"
                : firstNonBlank(segments.get(0).getSummary(), "根据实时路况前往第一站");

        schedule.add(DailyScheduleItem.builder()
                .timeRange("07:30-08:10")
                .period("早上")
                .type("早餐")
                .title(recommendationTitle("早餐与当日准备", breakfast))
                .location(recommendationLocation(stayArea + "周边", breakfast))
                .description(recommendationDescription("选择住宿周边早餐，补充饮水并确认门票、预约、天气和导航路线。", breakfast))
                .transport("步行")
                .durationMinutes(40)
                .costHint("约20-40元/人")
                .tips("穿舒适鞋，证件、充电宝和雨具按天气准备。")
                .lat(recommendationLat(breakfast))
                .lng(recommendationLng(breakfast))
                .build());
        schedule.add(DailyScheduleItem.builder()
                .timeRange("08:10-09:00")
                .period("早上")
                .type("交通")
                .title("前往上午第一站")
                .location(firstName)
                .description(segmentToFirst)
                .transport("打车/自驾/公共交通择优")
                .durationMinutes(50)
                .costHint("按实时路况和平台价格为准")
                .tips("跨城或山区路线要提前预留堵车和停车时间。")
                .lat(first == null ? null : first.getLat())
                .lng(first == null ? null : first.getLng())
                .build());
        schedule.add(DailyScheduleItem.builder()
                .timeRange("09:00-11:30")
                .period("上午")
                .type("景点")
                .title("上午游览：" + firstName)
                .location(firstName)
                .description(firstNonBlank(first == null ? null : first.getReason(),
                        first == null ? null : first.getDescription()))
                .transport("景区内步行")
                .durationMinutes(first == null || first.getStayTime() == null ? 120 : Math.min(150, Math.max(90, first.getStayTime())))
                .costHint("门票以官方渠道实时价格为准")
                .tips(first == null ? "到达后先确认闭园时间和返程出口。" : first.getTips())
                .lat(first == null ? null : first.getLat())
                .lng(first == null ? null : first.getLng())
                .build());
        schedule.add(DailyScheduleItem.builder()
                .timeRange("11:30-12:30")
                .period("中午")
                .type("午餐")
                .title(recommendationTitle("午餐与短休", lunch))
                .location(recommendationLocation(firstName + "或前往" + secondName + "途中", lunch))
                .description(recommendationDescription("优先选择评价稳定、出餐快的本地餐馆，午餐后安排10-15分钟休整。", lunch))
                .transport("步行或短途打车")
                .durationMinutes(60)
                .costHint("约50-100元/人")
                .tips("热门景区周边餐厅容易排队，可错峰或提前收藏备选。")
                .lat(recommendationLat(lunch))
                .lng(recommendationLng(lunch))
                .build());
        schedule.add(DailyScheduleItem.builder()
                .timeRange("12:30-13:20")
                .period("下午")
                .type("交通")
                .title("衔接下午景点")
                .location(secondName)
                .description(routeSummary(segments, 0, firstName, secondName))
                .transport("打车/自驾为主")
                .durationMinutes(routeMinutes(segments, 0, 40))
                .costHint("按实时路况和平台价格为准")
                .tips("如果上午游览超时，可压缩下午第一站停留。")
                .lat(second == null ? null : second.getLat())
                .lng(second == null ? null : second.getLng())
                .build());
        schedule.add(DailyScheduleItem.builder()
                .timeRange("13:20-16:20")
                .period("下午")
                .type("景点")
                .title("下午游览：" + secondName)
                .location(secondName)
                .description(firstNonBlank(second == null ? null : second.getReason(),
                        second == null ? null : second.getDescription()))
                .transport("景区内步行")
                .durationMinutes(second == null || second.getStayTime() == null ? 150 : Math.min(180, Math.max(90, second.getStayTime())))
                .costHint("门票以官方渠道实时价格为准")
                .tips(second == null ? "下午注意补水，避免连续高强度步行。" : second.getTips())
                .lat(second == null ? null : second.getLat())
                .lng(second == null ? null : second.getLng())
                .build());
        schedule.add(DailyScheduleItem.builder()
                .timeRange("16:20-17:40")
                .period("傍晚")
                .type("景点")
                .title("傍晚补充：" + thirdName)
                .location(thirdName)
                .description(firstNonBlank(third == null ? null : third.getReason(),
                        "根据体力选择轻量打卡、拍照或周边街区散步。"))
                .transport(routeSummary(segments, 1, secondName, thirdName))
                .durationMinutes(third == null || third.getStayTime() == null ? 80 : Math.min(120, Math.max(60, third.getStayTime())))
                .costHint("按景点实时价格为准")
                .tips(third == null ? "若遇闭园或天气变化，可改为咖啡休息或商圈补给。" : third.getTips())
                .lat(third == null ? null : third.getLat())
                .lng(third == null ? null : third.getLng())
                .build());
        schedule.add(DailyScheduleItem.builder()
                .timeRange("18:00-19:10")
                .period("晚上")
                .type("晚餐")
                .title(recommendationTitle("晚餐与恢复体力", dinner))
                .location(recommendationLocation(lastName + "或住宿区域", dinner))
                .description(recommendationDescription("选择目的地代表性餐饮，晚餐后根据体力决定是否安排夜游。", dinner))
                .transport("短途打车或步行")
                .durationMinutes(70)
                .costHint("约80-150元/人")
                .tips("晚餐尽量靠近住宿方向，减少夜间折返。")
                .lat(recommendationLat(dinner))
                .lng(recommendationLng(dinner))
                .build());
        schedule.add(DailyScheduleItem.builder()
                .timeRange("19:10-20:40")
                .period("夜间")
                .type("休息")
                .title("夜间轻量活动")
                .location(stayArea)
                .description("可选择夜景街区散步、补给、整理照片，或直接回酒店休息。")
                .transport("步行或短途打车")
                .durationMinutes(90)
                .costHint("按个人消费为准")
                .tips("夜间不要安排过远景点，保留次日体力。")
                .build());
        schedule.add(DailyScheduleItem.builder()
                .timeRange("20:40-21:30")
                .period("夜间")
                .type("住宿")
                .title(recommendationTitle("入住与次日准备", hotel))
                .location(recommendationLocation(stayArea, hotel))
                .description(recommendationDescription("办理入住、洗漱休整，复核次日天气、预约、交通和行李。", hotel))
                .transport("返回酒店")
                .durationMinutes(50)
                .costHint("住宿价格按实时预订平台为准")
                .tips(dayIndex < request.getDays() ? "如果次日跨城，提前确认退房和出发时间。" : "最后一晚注意返程交通和发票行李。")
                .lat(recommendationLat(hotel))
                .lng(recommendationLng(hotel))
                .build());
        return schedule;
    }

    private boolean hasCoreScheduleTypes(List<DailyScheduleItem> schedule) {
        String joinedTypes = schedule.stream()
                .map(DailyScheduleItem::getType)
                .filter(Objects::nonNull)
                .collect(Collectors.joining("、"));
        return joinedTypes.contains("早餐")
                && joinedTypes.contains("午餐")
                && joinedTypes.contains("晚餐")
                && joinedTypes.contains("交通")
                && joinedTypes.contains("住宿")
                && joinedTypes.contains("景点");
    }

    private StayPlan buildStayPlan(List<PoiInfo> pois, TravelPlanRequest request, int dayIndex) {
        PoiInfo anchor = pois == null || pois.isEmpty() ? null : pois.get(pois.size() - 1);
        AmapRecommendation hotel = findAmapRecommendation(request.getTo(), "酒店 住宿", anchor);
        String area = hotel == null ? buildStayArea(anchor, request) : hotel.name;
        return StayPlan.builder()
                .area(area)
                .hotelType("舒适型酒店或交通便利民宿")
                .reason(hotel == null
                        ? "靠近当日最后一站或次日出发方向，减少晚间折返和次日早高峰压力。"
                        : stayReason(hotel))
                .checkInTip(dayIndex < request.getDays()
                        ? "建议确认早餐时间、停车条件、最晚入住和次日退房寄存。"
                        : "建议确认退房时间、行李寄存、发票和返程交通。")
                .transportTip("优先选择主路、地铁/公交或打车上车点清晰的位置。")
                .build();
    }

    private String buildStayArea(PoiInfo anchor, TravelPlanRequest request) {
        if (anchor != null && anchor.getName() != null && !anchor.getName().isBlank()) {
            return anchor.getName() + "附近";
        }
        return request.getTo() + "市区交通便利区域";
    }

    private String buildClothingTips(TravelPlanRequest request) {
        String weather = amapPlaceTool.searchWeather(request.getTo());
        String preference = request.getPreference() == null ? "" : request.getPreference();
        String prefix = weather != null && !weather.isBlank() ? weather + " " : "";
        if (preference.contains("自然") || preference.contains("徒步") || preference.contains("山")) {
            return prefix + "穿防滑舒适鞋，准备薄外套、防晒、雨具和足量饮水。";
        }
        return prefix + "穿舒适步行鞋，按天气准备外套、防晒或雨具，夜间温差需出发前复核。";
    }

    private String buildDailyTransport(List<RouteSegment> segments) {
        int totalMinutes = segments == null ? 0 : segments.stream()
                .map(RouteSegment::getDriveTime)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();
        if (totalMinutes >= 120) {
            return "当日移动时间较长，建议自驾或包车，午后预留休息和堵车缓冲。";
        }
        return "当日以打车/自驾衔接为主，景区内部步行，短距离可使用公共交通。";
    }

    private PoiInfo getPoiAt(List<PoiInfo> pois, int index) {
        if (pois == null || pois.size() <= index) {
            return null;
        }
        return pois.get(index);
    }

    private String poiName(PoiInfo poi, String fallback) {
        return poi == null || poi.getName() == null || poi.getName().isBlank() ? fallback : poi.getName();
    }

    private String routeSummary(List<RouteSegment> segments, int index, String from, String to) {
        if (segments != null && segments.size() > index) {
            RouteSegment segment = segments.get(index);
            if (segment.getSummary() != null && !segment.getSummary().isBlank()) {
                return segment.getSummary();
            }
        }
        return String.format("从%s前往%s，按实时路况选择打车、自驾或公共交通。", from, to);
    }

    private Integer routeMinutes(List<RouteSegment> segments, int index, int fallback) {
        if (segments != null && segments.size() > index && segments.get(index).getDriveTime() != null) {
            return Math.max(15, segments.get(index).getDriveTime());
        }
        return fallback;
    }

    private AmapRecommendation findAmapRecommendation(String city, String keyword, PoiInfo anchor) {
        try {
            String text = amapPlaceTool.searchPlace(city, keyword,
                    anchor == null ? null : anchor.getLat(),
                    anchor == null ? null : anchor.getLng());
            if (text == null || !text.contains("- ")) {
                return localKnownRecommendation(city, keyword, anchor);
            }
            String line = text.lines()
                    .filter(value -> value.startsWith("- "))
                    .findFirst()
                    .orElse("");
            if (line.isBlank()) {
                return null;
            }
            String[] parts = line.substring(2).split("\\|");
            String name = parts.length > 0 ? parts[0].trim() : "";
            String address = extractPart(parts, "地址:");
            String location = extractPart(parts, "坐标:");
            Double lat = null;
            Double lng = null;
            if (location.contains(",")) {
                String[] coords = location.split(",");
                lng = Double.parseDouble(coords[0].trim());
                lat = Double.parseDouble(coords[1].trim());
            }
            return name.isBlank() ? localKnownRecommendation(city, keyword, anchor) : new AmapRecommendation(name, address, lat, lng, "高德地点查询");
        } catch (Exception e) {
            return localKnownRecommendation(city, keyword, anchor);
        }
    }

    private AmapRecommendation localKnownRecommendation(String city, String keyword, PoiInfo anchor) {
        String anchorName = anchor == null ? "" : firstNonBlank(anchor.getName(), "");
        String kw = keyword == null ? "" : keyword;
        boolean shanxiAnchor = isShanxiAnchor(anchorName);
        if (!shanxiAnchor) {
            return genericDestinationRecommendation(city, keyword, anchor);
        }
        if (anchorName.contains("长治") || anchorName.contains("八泉峡") || anchorName.contains("上党")
                || anchorName.contains("潞安") || anchorName.contains("观音堂") || anchorName.contains("漳泽湖")
                || anchorName.contains("通天峡") || anchorName.contains("太行纪念馆")) {
            return changzhiRecommendation(city, keyword, anchor);
        }
        if (kw.contains("酒店") || kw.contains("住宿")) {
            if (anchorName.contains("平遥") || anchorName.contains("日升昌") || anchorName.contains("王家") || anchorName.contains("乔家")) {
                return new AmapRecommendation("平遥德盛楼客栈", "平遥古城内，适合古城夜游后步行返回", 37.2015, 112.1775, "本地已知候选");
            }
            if (anchorName.contains("云冈") || anchorName.contains("大同") || anchorName.contains("华严") || anchorName.contains("善化")) {
                return new AmapRecommendation("大同云冈建国宾馆", "大同市区，便于衔接云冈石窟和古城线", 40.0820, 113.3000, "本地已知候选");
            }
            if (anchorName.contains("晋祠")) {
                return new AmapRecommendation("太原迎泽宾馆", "太原市区，便于返程和市内交通", 37.8630, 112.5620, "本地已知候选");
            }
            return new AmapRecommendation("目的地市区交通便利酒店", "优先选择靠近当日最后一站或次日出发方向的位置", null, null, "规则候选");
        }

        if (anchorName.contains("平遥") || anchorName.contains("日升昌") || anchorName.contains("王家") || anchorName.contains("乔家")) {
            if (kw.contains("早餐")) {
                return new AmapRecommendation("平遥古城早餐铺候选", "平遥古城内或客栈周边，出行前按实时评价选择", null, null, "规则候选");
            }
            if (kw.contains("晚餐")) {
                return new AmapRecommendation("天元奎饭店", "平遥古城内，晋菜与地方菜候选，旺季需提前排队复核", 37.2018, 112.1777, "本地已知候选");
            }
            return new AmapRecommendation("洪武记饭店", "平遥古城内，晋菜候选，出行前复核营业时间和排队情况", 37.2020, 112.1780, "本地已知候选");
        }

        if (anchorName.contains("云冈") || anchorName.contains("大同") || anchorName.contains("华严") || anchorName.contains("善化")) {
            if (kw.contains("早餐")) {
                return new AmapRecommendation("大同市区早餐候选", "大同古城或酒店周边，优先选择刀削面、羊杂等本地早餐", null, null, "规则候选");
            }
            return new AmapRecommendation("凤临阁", "大同古城内晋北风味餐饮候选，热门时段需复核排队", 40.0850, 113.2960, "本地已知候选");
        }

        if (anchorName.contains("晋祠")) {
            return new AmapRecommendation("紫泥369粗粮季", "太原本地菜候选，适合晋祠返程后用餐", 37.8600, 112.5500, "本地已知候选");
        }

        return null;
    }

    private boolean isShanxiAnchor(String anchorName) {
        if (anchorName == null) {
            return false;
        }
        return anchorName.contains("平遥")
                || anchorName.contains("日升昌")
                || anchorName.contains("王家")
                || anchorName.contains("乔家")
                || anchorName.contains("云冈")
                || anchorName.contains("大同")
                || anchorName.contains("华严")
                || anchorName.contains("善化")
                || anchorName.contains("晋祠")
                || anchorName.contains("长治")
                || anchorName.contains("八泉峡")
                || anchorName.contains("上党")
                || anchorName.contains("潞安")
                || anchorName.contains("观音堂")
                || anchorName.contains("漳泽湖")
                || anchorName.contains("通天峡")
                || anchorName.contains("太行纪念馆");
    }

    private AmapRecommendation changzhiRecommendation(String city, String keyword, PoiInfo anchor) {
        String anchorName = anchor == null ? "" : firstNonBlank(anchor.getName(), "");
        String kw = keyword == null ? "" : keyword;
        boolean remoteScenic = anchorName.contains("八泉峡") || anchorName.contains("通天峡");
        if (kw.contains("酒店") || kw.contains("住宿")) {
            if (remoteScenic) {
                return new AmapRecommendation("壶关太行山大峡谷周边住宿候选",
                        "靠近峡谷景区出入口，适合减少次日山路往返；具体店名和价格需按 OTA 实时复核",
                        35.9580, 113.5770, "本地已知候选");
            }
            return new AmapRecommendation("长治潞州区市中心住宿候选",
                    "建议选择上党门、潞安府城隍庙或长治东站之间交通便利区域，便于市区古建和返程",
                    36.1950, 113.1160, "本地已知候选");
        }
        if (kw.contains("早餐")) {
            return new AmapRecommendation("长治潞州区早餐候选",
                    "优先复核甩饼、羊汤、豆腐脑等本地早餐；以住宿点附近实时营业餐厅为准",
                    36.1950, 113.1160, "本地已知候选");
        }
        if (kw.contains("晚餐")) {
            return new AmapRecommendation("长治本地晋菜晚餐候选",
                    "优先选择潞州区老城或住宿区周边评分稳定的晋菜/上党菜餐厅，热门时段提前排队复核",
                    36.1950, 113.1160, "本地已知候选");
        }
        if (remoteScenic) {
            return new AmapRecommendation("太行山大峡谷景区周边午餐候选",
                    "以景区出入口或壶关县城实时营业餐厅为准，山路景区日不建议跨城折返用餐",
                    35.9580, 113.5770, "本地已知候选");
        }
        return new AmapRecommendation("长治老城周边午餐候选",
                "上党门、潞安府城隍庙周边筛选真实营业餐厅，出行前按地图实时状态复核",
                36.1950, 113.1160, "本地已知候选");
    }

    private AmapRecommendation genericDestinationRecommendation(String city, String keyword, PoiInfo anchor) {
        String destination = city == null || city.isBlank() ? "目的地" : city;
        String anchorName = anchor == null || anchor.getName() == null || anchor.getName().isBlank()
                ? destination + "核心区域"
                : anchor.getName();
        String kw = keyword == null ? "" : keyword;
        if (kw.contains("酒店") || kw.contains("住宿")) {
            return new AmapRecommendation(destination + "交通便利住宿候选",
                    anchorName + "附近或次日出发方向；高德地点不可用，必须在预订平台按实时评分、位置和价格复核",
                    null, null, "规则候选");
        }
        if (kw.contains("早餐")) {
            return new AmapRecommendation(destination + "早餐候选",
                    anchorName + "或住宿区域周边；高德地点不可用，出行前按地图实时营业状态复核",
                    null, null, "规则候选");
        }
        if (kw.contains("晚餐")) {
            return new AmapRecommendation(destination + "晚餐餐厅候选",
                    anchorName + "或住宿区域周边；优先选择当地菜和评分稳定餐厅，出行前按地图实时复核",
                    null, null, "规则候选");
        }
        return new AmapRecommendation(destination + "午餐餐厅候选",
                anchorName + "附近或下一站途中；高德地点不可用，需按实时地图筛选真实餐厅",
                null, null, "规则候选");
    }

    private String extractPart(String[] parts, String prefix) {
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.startsWith(prefix)) {
                return trimmed.substring(prefix.length()).trim();
            }
        }
        return "";
    }

    private String recommendationTitle(String fallback, AmapRecommendation recommendation) {
        return recommendation == null ? fallback : fallback + "：" + recommendation.name;
    }

    private String recommendationLocation(String fallback, AmapRecommendation recommendation) {
        return recommendation == null ? fallback : recommendation.name;
    }

    private String recommendationDescription(String fallback, AmapRecommendation recommendation) {
        if (recommendation == null) {
            return fallback;
        }
        String address = recommendation.address == null || recommendation.address.isBlank()
                ? "地址以高德实时结果为准"
                : "地址：" + recommendation.address;
        String source = "高德地点查询".equals(recommendation.source)
                ? "已通过高德地点查询补充真实推荐"
                : "高德 WebService 不可用时使用本地已知候选补充，出行前需按实时平台复核";
        return fallback + " " + source + "，" + address + "。";
    }

    private String stayReason(AmapRecommendation hotel) {
        String address = hotel.address == null || hotel.address.isBlank()
                ? "地址需按实时平台复核"
                : "地址：" + hotel.address;
        if ("高德地点查询".equals(hotel.source)) {
            return "高德地点查询到的住宿点，靠近当日路线锚点，减少晚间折返。" + address;
        }
        return "高德 WebService 当前不可用，使用本地已知住宿候选补充；靠近当日路线锚点，出行前需按实时平台复核。" + address;
    }

    private Double recommendationLat(AmapRecommendation recommendation) {
        return recommendation == null ? null : recommendation.lat;
    }

    private Double recommendationLng(AmapRecommendation recommendation) {
        return recommendation == null ? null : recommendation.lng;
    }

    private record AmapRecommendation(String name, String address, Double lat, Double lng, String source) {
    }

    private List<TripOption> buildDayOptions(List<PoiInfo> pois, String preference) {
        List<TripOption> options = new ArrayList<>();
        if (pois.isEmpty()) {
            return options;
        }

        options.add(TripOption.builder()
                .name("主路线")
                .style("均衡")
                .summary("按地理顺序串联核心景点，兼顾代表性和执行性。")
                .tradeOff("景点覆盖更完整，但跨点移动时间较长。")
                .suitableFor("第一次到访、希望少做功课的用户")
                .poiNames(joinPoiNames(pois))
                .build());

        List<PoiInfo> relaxed = pois.subList(0, Math.min(3, pois.size()));
        options.add(TripOption.builder()
                .name("轻松版")
                .style("少赶路")
                .summary("减少景点数量，把停留时间留给讲解、拍照和用餐。")
                .tradeOff("放弃部分远距离景点，但体力压力明显更低。")
                .suitableFor("亲子、长辈、慢旅行")
                .poiNames(joinPoiNames(relaxed))
                .build());

        List<PoiInfo> deep = pois.stream()
                .filter(p -> p.getCategory() != null && p.getCategory().contains(preference))
                .limit(4)
                .toList();
        if (deep.size() >= 2) {
            options.add(TripOption.builder()
                    .name("偏好强化版")
                    .style(preference)
                    .summary("优先保留与偏好最匹配的景点。")
                    .tradeOff("主题更集中，但综合体验会少一些。")
                    .suitableFor(preference + "爱好者")
                    .poiNames(joinPoiNames(deep))
                    .build());
        }
        if (options.size() < 3 && pois.size() >= 2) {
            List<PoiInfo> compact = pois.stream()
                    .skip(Math.max(0, pois.size() - 2))
                    .toList();
            options.add(TripOption.builder()
                    .name("紧凑打卡版")
                    .style("省时间")
                    .summary("保留辨识度最高的点，适合只想快速打卡。")
                    .tradeOff("体验更短平快，深度讲解和慢游时间较少。")
                    .suitableFor("时间紧、只停留半天到一天的用户")
                    .poiNames(joinPoiNames(compact))
                    .build());
        }
        return options;
    }

    private List<TripOption> buildTripOptions(List<PoiInfo> candidates, String preference) {
        List<PoiInfo> limited = candidates.stream().limit(6).toList();
        List<PoiInfo> relaxed = candidates.stream().limit(3).toList();
        List<PoiInfo> nature = candidates.stream()
                .filter(p -> p.getCategory() != null && p.getCategory().contains("自然"))
                .limit(3)
                .toList();

        List<TripOption> options = new ArrayList<>();
        options.add(TripOption.builder()
                .name("经典全景线")
                .style("覆盖优先")
                .summary("尽量覆盖目的地最具代表性的景点，适合第一次去。")
                .tradeOff("路程更长，需要接受较多城市间移动。")
                .suitableFor("第一次到访、想快速建立整体印象")
                .poiNames(joinPoiNames(limited))
                .build());
        options.add(TripOption.builder()
                .name("轻松慢游线")
                .style("体验优先")
                .summary("减少跨城移动，把时间留给讲解、餐饮和休息。")
                .tradeOff("景点数量更少，但舒适度更高。")
                .suitableFor("亲子、长辈、周末短途")
                .poiNames(joinPoiNames(relaxed))
                .build());
        if (!nature.isEmpty()) {
            options.add(TripOption.builder()
                    .name("自然补充线")
                    .style("风景优先")
                    .summary("在文化路线之外加入山水景观，避免全程古建审美疲劳。")
                    .tradeOff("自然景点通常距离更远，需要更强自驾条件。")
                    .suitableFor("自驾、摄影、自然风光爱好者")
                    .poiNames(joinPoiNames(nature))
                    .build());
        }
        if (options.size() < 3) {
            List<PoiInfo> deep = candidates.stream()
                    .filter(p -> p.getCategory() != null && p.getCategory().contains(preference))
                    .limit(4)
                    .toList();
            if (deep.isEmpty()) {
                deep = candidates.stream().skip(Math.min(2, candidates.size())).limit(4).toList();
            }
            options.add(TripOption.builder()
                    .name("主题强化线")
                    .style(preference)
                    .summary("围绕用户偏好集中选择景点，减少主题跳跃。")
                    .tradeOff("主题更鲜明，但自然风光或美食补充会变少。")
                    .suitableFor(preference + "爱好者")
                    .poiNames(joinPoiNames(deep))
                    .build());
        }
        return options;
    }

    private String joinPoiNames(List<PoiInfo> pois) {
        return pois.stream()
                .map(PoiInfo::getName)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.joining("、"));
    }

    private double calculatePathDistance(List<PoiInfo> pois) {
        double total = 0;
        for (int i = 0; i < pois.size() - 1; i++) {
            PoiInfo current = pois.get(i);
            PoiInfo next = pois.get(i + 1);
            total += haversine(current.getLat(), current.getLng(), next.getLat(), next.getLng());
        }
        return total;
    }

    private double haversine(double lat1, double lng1, double lat2, double lng2) {
        double radius = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return radius * c;
    }
}
