package com.lvyou.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lvyou.client.AgentClient;
import com.lvyou.model.request.TravelPlanRequest;
import com.lvyou.model.response.ResearchReport;
import com.lvyou.model.response.TravelPlanResponse;
import com.lvyou.service.HistoryService;
import com.lvyou.service.TravelPlanService;
import com.lvyou.service.WebResearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;

/**
 * 旅行计划服务实现
 * <p>
 * 职责：纯业务编排，调用 AI Agent 服务，不包含任何 AI 逻辑。
 * <p>
 * 禁止：
 * - 写 ReAct 逻辑
 * - 写 Prompt
 * - 写工具调用逻辑
 * - 写 AI 推理代码
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TravelPlanServiceImpl implements TravelPlanService {

    private final AgentClient agentClient;
    private final HistoryService historyService;
    private final WebResearchService webResearchService;
    private final ObjectMapper objectMapper;

    @Override
    public TravelPlanResponse generatePlan(TravelPlanRequest request) {
        log.info("🏨 [业务层] 开始处理旅行计划: from={}, to={}, days={}, preference={}",
                request.getFrom(), request.getTo(), request.getDays(), request.getPreference());

        ResearchReport report = null;
        if (Boolean.TRUE.equals(request.getOnlineResearch())) {
            report = webResearchService.research(request);
            request.setResearchContext(webResearchService.formatForPrompt(report));
            log.info("🌐 [业务层] 联网调研完成: status={}, sources={}",
                    report.getStatus(), report.getSources() != null ? report.getSources().size() : 0);
        }

        // 调用 AI Agent 服务生成路线
        TravelPlanResponse response = agentClient.generatePlan(request);
        if (response != null) {
            response.setResearchReport(report);
        }

        // 业务层后处理：确保返回数据完整
        if (response == null) {
            log.warn("⚠️ [业务层] Agent 返回为空，使用空结果");
            return TravelPlanResponse.builder()
                    .from(request.getFrom())
                    .to(request.getTo())
                    .totalDays(request.getDays())
                    .preference(request.getPreference())
                    .researchReport(report)
                    .days(Collections.emptyList())
                    .build();
        }

        // 自动保存历史记录
        try {
            String requestJson = objectMapper.writeValueAsString(request);
            historyService.saveHistory(response, requestJson);
        } catch (Exception e) {
            log.warn("保存历史记录失败（不影响主流程）", e);
        }

        log.info("✅ [业务层] 旅行计划处理完成: {} 天路线",
                response.getDays() != null ? response.getDays().size() : 0);
        return response;
    }
}
