package com.lvyou.agent.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 可替换玩法方案，帮助用户在不同节奏和主题之间权衡。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TripOption {

    private String name;

    private String style;

    private String summary;

    private String tradeOff;

    private String suitableFor;

    private String poiNames;
}
