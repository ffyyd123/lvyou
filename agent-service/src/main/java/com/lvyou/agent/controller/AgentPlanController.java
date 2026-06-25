package com.lvyou.agent.controller;

import com.lvyou.agent.agent.TravelAgent;
import com.lvyou.agent.model.TravelPlanRequest;
import com.lvyou.agent.model.TravelPlanResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Agent HTTP 边界。
 *
 * 业务系统只能调用本接口，不直接参与 Prompt、ReAct 或工具编排。
 */
@Slf4j
@RestController
@RequestMapping("/agent")
@RequiredArgsConstructor
public class AgentPlanController {

    private final TravelAgent travelAgent;

    @PostMapping("/plan")
    public TravelPlanResult plan(@RequestBody TravelPlanRequest request) {
        log.info("[Agent接口] 收到规划请求: from={}, to={}, days={}, preference={}",
                request.getFrom(), request.getTo(), request.getDays(), request.getPreference());
        return travelAgent.generatePlan(request);
    }
}
