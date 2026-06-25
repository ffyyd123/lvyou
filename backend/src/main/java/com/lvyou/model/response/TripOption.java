package com.lvyou.model.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 备选玩法方案。
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
