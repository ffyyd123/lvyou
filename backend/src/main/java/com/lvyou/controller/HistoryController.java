package com.lvyou.controller;

import com.lvyou.entity.TravelHistory;
import com.lvyou.model.response.ApiResult;
import com.lvyou.service.HistoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 历史记录 REST 控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/history")
@RequiredArgsConstructor
public class HistoryController {

    private final HistoryService historyService;

    /**
     * 查询所有历史记录（简要列表）
     */
    @GetMapping
    public ApiResult<List<TravelHistory>> listHistory() {
        List<TravelHistory> list = historyService.findAll();
        return ApiResult.success(list);
    }

    /**
     * 查询历史记录详情（含完整路线 JSON）
     */
    @GetMapping("/{id}")
    public ApiResult<TravelHistory> getHistory(@PathVariable Long id) {
        TravelHistory history = historyService.findById(id);
        if (history == null) {
            return ApiResult.error(404, "历史记录不存在");
        }
        return ApiResult.success(history);
    }

    /**
     * 删除一条历史记录
     */
    @DeleteMapping("/{id}")
    public ApiResult<Void> deleteHistory(@PathVariable Long id) {
        historyService.deleteById(id);
        return ApiResult.success(null);
    }
}
