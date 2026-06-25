package com.lvyou.model.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 国内行政区选项。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DistrictRegion {

    private String name;
    private String adcode;
    private String citycode;
    private String level;

    @Builder.Default
    private List<DistrictRegion> children = new ArrayList<>();
}
