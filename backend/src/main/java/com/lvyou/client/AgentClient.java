package com.lvyou.client;

import com.lvyou.agent.model.TravelPlanResult;
import com.lvyou.model.request.TravelPlanRequest;
import com.lvyou.model.response.DayPlan;
import com.lvyou.model.response.DailyScheduleItem;
import com.lvyou.model.response.POIInfo;
import com.lvyou.model.response.RouteCandidate;
import com.lvyou.model.response.RouteDecisionReport;
import com.lvyou.model.response.RouteSegment;
import com.lvyou.model.response.StayPlan;
import com.lvyou.model.response.TripOption;
import com.lvyou.model.response.TravelPlanResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.stream.Collectors;

/**
 * AI Agent 服务客户端
 * <p>
 * agent-service 作为库模块被 backend 依赖，运行在同一 JVM 进程中。
 * 业务层仍通过 /agent/plan HTTP 边界调用，避免直接耦合 AI 推理实现。
 * <p>
 * 职责：仅负责数据转换和调用编排，不包含任何 AI 逻辑。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentClient {

    private final RestTemplate restTemplate;

    @Value("${agent.endpoint:http://localhost:8080/agent/plan}")
    private String agentEndpoint;

    /**
     * 调用 Agent 服务生成旅行路线
     *
     * @param request 旅行请求
     * @return 旅行路线响应
     */
    public TravelPlanResponse generatePlan(TravelPlanRequest request) {
        log.info("🤖 [AgentClient] 调用AI Agent: from={}, to={}, days={}, preference={}",
                request.getFrom(), request.getTo(), request.getDays(), request.getPreference());

        // 转换请求
        com.lvyou.agent.model.TravelPlanRequest agentRequest =
                com.lvyou.agent.model.TravelPlanRequest.builder()
                        .from(request.getFrom())
                        .to(request.getTo())
                        .days(request.getDays())
                        .preference(request.getPreference())
                        .userIdea(request.getUserIdea())
                        .onlineResearch(request.getOnlineResearch())
                        .researchContext(request.getResearchContext())
                        .build();

        TravelPlanResult agentResult;
        try {
            agentResult = restTemplate.postForObject(agentEndpoint, agentRequest, TravelPlanResult.class);
        } catch (RestClientException e) {
            log.error("❌ [AgentClient] 调用 Agent HTTP 接口失败: {}", e.getMessage(), e);
            throw new RuntimeException("AI Agent 服务调用失败，请稍后重试");
        }

        // 转换响应
        TravelPlanResponse response = convertToResponse(agentResult, request);
        log.info("✅ [AgentClient] Agent返回成功: {} 天路线",
                response.getDays() != null ? response.getDays().size() : 0);
        return response;
    }

    /**
     * 将 Agent 层模型转换为 Backend 层响应模型
     */
    private TravelPlanResponse convertToResponse(TravelPlanResult agentResult, TravelPlanRequest request) {
        if (agentResult == null) {
            return TravelPlanResponse.builder()
                    .from(request.getFrom())
                    .to(request.getTo())
                    .totalDays(request.getDays())
                    .preference(request.getPreference())
                    .userIdea(request.getUserIdea())
                    .days(Collections.emptyList())
                    .build();
        }

        return TravelPlanResponse.builder()
                .from(request.getFrom())
                .to(request.getTo())
                .totalDays(request.getDays())
                .preference(request.getPreference())
                .userIdea(request.getUserIdea())
                .options(agentResult.getOptions() == null ? Collections.emptyList()
                        : agentResult.getOptions().stream().map(o -> TripOption.builder()
                                .name(o.getName())
                                .style(o.getStyle())
                                .summary(o.getSummary())
                                .tradeOff(o.getTradeOff())
                                .suitableFor(o.getSuitableFor())
                                .poiNames(o.getPoiNames())
                                .build())
                                .collect(Collectors.toList()))
                .routeDecisionReport(convertRouteDecisionReport(agentResult.getRouteDecisionReport()))
                .days(agentResult.getDays() == null ? Collections.emptyList()
                        : agentResult.getDays().stream().map(d -> DayPlan.builder()
                                .day(d.getDay())
                                .theme(d.getTheme())
                                .distance(d.getDistance())
                                .driveTime(d.getDriveTime())
                                .clothingTips(d.getClothingTips())
                                .dailyTransport(d.getDailyTransport())
                                .budgetHint(d.getBudgetHint())
                                .stay(d.getStay() == null ? null : StayPlan.builder()
                                        .area(d.getStay().getArea())
                                        .hotelType(d.getStay().getHotelType())
                                        .reason(d.getStay().getReason())
                                        .checkInTip(d.getStay().getCheckInTip())
                                        .transportTip(d.getStay().getTransportTip())
                                        .build())
                                .schedule(d.getSchedule() == null ? Collections.emptyList()
                                        : d.getSchedule().stream().map(item -> DailyScheduleItem.builder()
                                                .timeRange(item.getTimeRange())
                                                .period(item.getPeriod())
                                                .type(item.getType())
                                                .title(item.getTitle())
                                                .location(item.getLocation())
                                                .description(item.getDescription())
                                                .transport(item.getTransport())
                                                .durationMinutes(item.getDurationMinutes())
                                                .costHint(item.getCostHint())
                                                .tips(item.getTips())
                                                .lat(item.getLat())
                                                .lng(item.getLng())
                                                .build())
                                                .collect(Collectors.toList()))
                                .segments(d.getSegments() == null ? Collections.emptyList()
                                        : d.getSegments().stream().map(s -> RouteSegment.builder()
                                                .index(s.getIndex())
                                                .from(s.getFrom())
                                                .to(s.getTo())
                                                .distance(s.getDistance())
                                                .driveTime(s.getDriveTime())
                                                .summary(s.getSummary())
                                                .build())
                                                .collect(Collectors.toList()))
                                .options(d.getOptions() == null ? Collections.emptyList()
                                        : d.getOptions().stream().map(o -> TripOption.builder()
                                                .name(o.getName())
                                                .style(o.getStyle())
                                                .summary(o.getSummary())
                                                .tradeOff(o.getTradeOff())
                                                .suitableFor(o.getSuitableFor())
                                                .poiNames(o.getPoiNames())
                                                .build())
                                                .collect(Collectors.toList()))
                                .pois(d.getPois() == null ? Collections.emptyList()
                                        : d.getPois().stream().map(p -> POIInfo.builder()
                                                .name(p.getName())
                                                .lat(p.getLat())
                                                .lng(p.getLng())
                                                .stayTime(p.getStayTime())
                                                .description(p.getDescription())
                                                .imageUrl(p.getImageUrl())
                                                .reason(p.getReason())
                                                .tags(p.getTags())
                                                .bestFor(p.getBestFor())
                                                .tips(p.getTips())
                                                .build())
                                                .collect(Collectors.toList()))
                                .build())
                        .collect(Collectors.toList()))
                .build();
    }

    private RouteDecisionReport convertRouteDecisionReport(com.lvyou.agent.model.RouteDecisionReport report) {
        if (report == null) {
            return null;
        }
        return RouteDecisionReport.builder()
                .selectedStrategy(report.getSelectedStrategy())
                .summary(report.getSummary())
                .totalDistance(report.getTotalDistance())
                .totalDriveTime(report.getTotalDriveTime())
                .routeScore(report.getRouteScore())
                .optimizationNotes(report.getOptimizationNotes())
                .candidates(report.getCandidates() == null ? Collections.emptyList()
                        : report.getCandidates().stream().map(candidate -> RouteCandidate.builder()
                                .name(candidate.getName())
                                .strategy(candidate.getStrategy())
                                .daysSummary(candidate.getDaysSummary())
                                .totalDistance(candidate.getTotalDistance())
                                .totalDriveTime(candidate.getTotalDriveTime())
                                .score(candidate.getScore())
                                .tradeOff(candidate.getTradeOff())
                                .selected(candidate.getSelected())
                                .build())
                                .collect(Collectors.toList()))
                .build();
    }
}
