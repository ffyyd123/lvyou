package com.lvyou.agent.tool;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Tool 2: calc_distance — 计算两个经纬度之间的直线距离
 * <p>
 * 使用 Haversine 公式计算球面距离（地球半径 6371km）。
 * 同时估算驾车时间（按城市道路平均速度 40km/h 计算）。
 */
@Slf4j
@Component
public class CalcDistanceTool {

    private static final double EARTH_RADIUS_KM = 6371.0;
    private static final double AVG_SPEED_KMH = 40.0; // 城市平均车速

    @Tool(name = "calc_distance", description = "计算两个经纬度坐标之间的直线距离（km）和估算驾车时间（分钟）。用于规划路线时评估景点间的距离是否合理")
    public String calcDistance(
            @ToolParam(name = "lat1", description = "第一个点的纬度")
            double lat1,
            @ToolParam(name = "lng1", description = "第一个点的经度")
            double lng1,
            @ToolParam(name = "lat2", description = "第二个点的纬度")
            double lat2,
            @ToolParam(name = "lng2", description = "第二个点的经度")
            double lng2) {

        double distance = haversine(lat1, lng1, lat2, lng2);
        double driveMinutes = (distance / AVG_SPEED_KMH) * 60;

        log.info("📏 [calc_distance] ({:.4f},{:.4f}) -> ({:.4f},{:.4f}) = {:.1f}km, ~{:.0f}min",
                lat1, lng1, lat2, lng2, distance, driveMinutes);

        return String.format(
                "两点间直线距离: %.1f 公里，估算驾车时间: %.0f 分钟（按城市道路均速%.0fkm/h）",
                distance, driveMinutes, AVG_SPEED_KMH);
    }

    /**
     * Haversine 公式计算球面距离
     */
    private double haversine(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_KM * c;
    }
}
