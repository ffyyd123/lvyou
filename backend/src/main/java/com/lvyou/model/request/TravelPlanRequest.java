package com.lvyou.model.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 旅行计划请求DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TravelPlanRequest {

    /** 出发城市 */
    @NotBlank(message = "出发城市不能为空")
    private String from;

    /** 目的城市/省份 */
    @NotBlank(message = "目的城市不能为空")
    private String to;

    /** 旅行天数 */
    @NotNull(message = "天数不能为空")
    @Min(value = 1, message = "至少1天")
    @Max(value = 30, message = "最多30天")
    private Integer days;

    /** 偏好: 历史文化 / 自然风光 / 美食 / 综合 */
    @NotBlank(message = "偏好不能为空")
    private String preference;

    /** 用户自定义想法（可选） */
    private String userIdea;

    /** 是否启用联网调研 */
    private Boolean onlineResearch;

    /** 联网调研上下文，由后端生成并传给 Agent */
    private String researchContext;
}
