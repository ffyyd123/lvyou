package com.lvyou.agent.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * POI（景点）信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PoiInfo {

    /** 景点名称 */
    private String name;

    /** 纬度 */
    private Double lat;

    /** 经度 */
    private Double lng;

    /** 建议停留时间（分钟） */
    @JsonProperty("stay_time")
    private Integer stayTime;

    /** 景点描述 */
    private String description;

    /** 景点图片地址 */
    private String imageUrl;

    /** 推荐理由，用于帮助用户判断为什么去 */
    private String reason;

    /** 体验标签，如世界遗产、亲子、古建、拍照 */
    private String tags;

    /** 适合人群或游玩方式 */
    private String bestFor;

    /** 出行提醒，如预约、开放时间复核 */
    private String tips;

    /** 所在城市（用于POI数据存储） */
    private String city;

    /** 分类标签（用于POI数据存储） */
    private String category;
}
