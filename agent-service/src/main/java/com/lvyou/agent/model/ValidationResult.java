package com.lvyou.agent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 路线校验结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidationResult {
    private boolean valid;
    private String message;
    private Double totalDistance;
    private Integer totalDriveTime;
    private Integer totalStayTime;
}
