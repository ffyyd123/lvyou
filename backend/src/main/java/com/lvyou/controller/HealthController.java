package com.lvyou.controller;

import com.lvyou.model.response.ApiResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 服务健康检查接口。
 */
@RestController
public class HealthController {

    @GetMapping("/api/health")
    public ApiResult<Map<String, Object>> health() {
        return ApiResult.success(Map.of(
                "status", "UP",
                "time", LocalDateTime.now()
        ));
    }
}
