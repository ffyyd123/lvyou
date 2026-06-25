package com.lvyou.service;

import com.lvyou.model.request.TravelPlanRequest;
import com.lvyou.model.response.TravelPlanResponse;

/**
 * 旅行计划服务接口
 */
public interface TravelPlanService {

    /**
     * 生成旅行计划
     */
    TravelPlanResponse generatePlan(TravelPlanRequest request);
}
