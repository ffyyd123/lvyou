package com.lvyou.agent.tool;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Tool 3: validate_route — 校验一天行程的合理性
 * <p>
 * 判断标准：
 * - 总驾车时间不应超过 3 小时
 * - 总游玩时间（停留时间之和）不应超过 10 小时
 * - 景点数量 2-5 个为合理
 * <p>
 * 此工具在 AI 生成路线后被调用，用于校验路线是否合理。
 */
@Slf4j
@Component
public class ValidateRouteTool {

    private static final double MAX_DRIVE_MINUTES = 180;  // 最大驾车时间（分钟）
    private static final double MAX_STAY_MINUTES = 600;   // 最大游玩时间（分钟）
    private static final int MIN_POI_COUNT = 2;           // 最少景点数
    private static final int MAX_POI_COUNT = 5;           // 最多景点数

    @Tool(name = "validate_route", description = "校验一天的旅行路线是否合理。输入该天的总驾车时间（分钟）、总停留时间（分钟）、景点数量，返回合理性评估和建议")
    public String validateRoute(
            @ToolParam(name = "total_drive_minutes", description = "当天总驾车时间（分钟）")
            double totalDriveMinutes,
            @ToolParam(name = "total_stay_minutes", description = "当天总停留时间（分钟）")
            double totalStayMinutes,
            @ToolParam(name = "poi_count", description = "当天景点数量")
            int poiCount) {

        log.info("✅ [validate_route] drive={}min, stay={}min, pois={}",
                totalDriveMinutes, totalStayMinutes, poiCount);

        StringBuilder result = new StringBuilder();
        boolean isReasonable = true;

        // 检查驾车时间
        if (totalDriveMinutes > MAX_DRIVE_MINUTES) {
            result.append(String.format("⚠️ 驾车时间 %.0f 分钟超过建议上限 %.0f 分钟，建议减少景点或调整路线。\n",
                    totalDriveMinutes, MAX_DRIVE_MINUTES));
            isReasonable = false;
        }

        // 检查总游玩时间
        if (totalStayMinutes > MAX_STAY_MINUTES) {
            result.append(String.format("⚠️ 总游玩时间 %.0f 分钟超过建议上限 %.0f 分钟，建议减少景点数量。\n",
                    totalStayMinutes, MAX_STAY_MINUTES));
            isReasonable = false;
        }

        // 检查景点数量
        if (poiCount < MIN_POI_COUNT) {
            result.append(String.format("⚠️ 景点数量 %d 少于建议最小值 %d，建议增加景点。\n",
                    poiCount, MIN_POI_COUNT));
            isReasonable = false;
        } else if (poiCount > MAX_POI_COUNT) {
            result.append(String.format("⚠️ 景点数量 %d 超过建议最大值 %d，行程可能过于紧凑。\n",
                    poiCount, MAX_POI_COUNT));
            isReasonable = false;
        }

        if (isReasonable) {
            result.append(String.format(
                    "✅ 路线合理！驾车%.0f分钟 + 游玩%.0f分钟 = 总计%.0f分钟（约%.1f小时），景点%d个。",
                    totalDriveMinutes, totalStayMinutes,
                    totalDriveMinutes + totalStayMinutes,
                    (totalDriveMinutes + totalStayMinutes) / 60.0,
                    poiCount));
        }

        return result.toString();
    }
}
