package com.lvyou.service;

import com.lvyou.entity.TravelHistory;
import com.lvyou.model.response.TravelPlanResponse;

import java.util.List;

/**
 * 历史记录服务接口
 */
public interface HistoryService {

    TravelHistory saveHistory(TravelPlanResponse plan, String requestJson);

    List<TravelHistory> findAll();

    TravelHistory findById(Long id);

    void deleteById(Long id);
}
