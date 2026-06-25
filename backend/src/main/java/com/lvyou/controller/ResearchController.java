package com.lvyou.controller;

import com.lvyou.agent.model.PlannedSearchQuery;
import com.lvyou.agent.service.ResearchKeywordPlanner;
import com.lvyou.model.request.TravelPlanRequest;
import com.lvyou.model.response.ApiResult;
import com.lvyou.model.response.ResearchReport;
import com.lvyou.service.WebResearchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 联网调研预览接口。
 */
@RestController
@RequestMapping("/api/research")
@RequiredArgsConstructor
public class ResearchController {

    private final WebResearchService webResearchService;
    private final ResearchKeywordPlanner researchKeywordPlanner;

    @PostMapping("/keywords")
    public ApiResult<ResearchReport> keywords(@Valid @RequestBody TravelPlanRequest request) {
        List<PlannedSearchQuery> queries = researchKeywordPlanner.plan(
                request.getFrom(),
                request.getTo(),
                request.getDays(),
                request.getPreference(),
                request.getUserIdea());
        return ApiResult.success(ResearchReport.builder()
                .enabled(true)
                .status("keywords_ready")
                .destination(request.getTo())
                .preference(request.getPreference())
                .generatedAt(LocalDateTime.now())
                .rawSourceCount(0)
                .cleanedSourceCount(0)
                .targetKeywordCount(15)
                .targetSourcesPerKeyword(20)
                .targetEffectiveSourceCount(300)
                .evidenceSummary("已生成搜索关键词，正在按食、住、行三类执行多源搜索；搜索结果返回后会继续展示原始结果、清洗判断和 RAG 保留证据。")
                .trustPolicy("关键词预览阶段只展示搜索计划，不作为事实证据；事实证据必须等待工具搜索与清洗完成。")
                .keywordGroups(keywordGroups(queries))
                .searchRounds(queries.stream()
                        .map(q -> q.getDirection() + "｜" + q.getRound() + "：" + q.getKeyword()
                                + (q.getPurpose() == null || q.getPurpose().isBlank() ? "" : "（" + q.getPurpose() + "）"))
                        .toList())
                .build());
    }

    @PostMapping("/preview")
    public ApiResult<ResearchReport> preview(@Valid @RequestBody TravelPlanRequest request) {
        request.setOnlineResearch(true);
        return ApiResult.success(webResearchService.research(request));
    }

    private Map<String, List<String>> keywordGroups(List<PlannedSearchQuery> queries) {
        Map<String, List<String>> groups = new LinkedHashMap<>();
        groups.put("食", new ArrayList<>());
        groups.put("住", new ArrayList<>());
        groups.put("行", new ArrayList<>());
        for (PlannedSearchQuery query : queries) {
            String direction = query.getDirection() == null || query.getDirection().isBlank() ? "行" : query.getDirection();
            groups.computeIfAbsent(direction, key -> new ArrayList<>()).add(query.getKeyword());
        }
        return groups;
    }
}
