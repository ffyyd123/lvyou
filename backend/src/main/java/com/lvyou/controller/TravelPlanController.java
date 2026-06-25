package com.lvyou.controller;

import com.lvyou.model.request.TravelPlanRequest;
import com.lvyou.model.response.ApiResult;
import com.lvyou.model.response.TravelPlanResponse;
import com.lvyou.service.TravelPlanService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 旅行计划 REST 控制器
 * <p>
 * 职责：接收前端请求，参数校验，调用业务层，返回统一响应。
 * 不包含任何 AI 逻辑。
 */
@Slf4j
@RestController
@RequestMapping("/api/travel")
@RequiredArgsConstructor
public class TravelPlanController {

    private final TravelPlanService travelPlanService;

    /**
     * 生成旅行路线计划
     * <p>
     * POST /api/travel/plan
     * <p>
     * 响应格式: { code: 200, message: "success", data: { from, to, totalDays, preference, days: [...] } }
     */
    @PostMapping("/plan")
    public ApiResult<TravelPlanResponse> generatePlan(@Valid @RequestBody TravelPlanRequest request) {
        log.info("📥 [Controller] 收到旅行计划请求: from={}, to={}, days={}, preference={}",
                request.getFrom(), request.getTo(), request.getDays(), request.getPreference());

        TravelPlanResponse response = travelPlanService.generatePlan(request);
        return ApiResult.success(response);
    }
}
