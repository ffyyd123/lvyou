package com.lvyou.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 旅行路线历史记录。
 *
 * 这里不再使用数据库实体，只保留和前端一致的数据结构。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TravelHistory {

    private Long id;
    private String fromCity;
    private String toCity;
    private Integer days;
    private String preference;
    private String userIdea;
    private String resultJson;
    private String summary;
    private Integer poiCount;
    private LocalDateTime createdAt;
}
