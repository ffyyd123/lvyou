package com.lvyou.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lvyou.model.response.ApiResult;
import com.lvyou.model.response.DistrictRegion;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * 行政区划数据接口。
 */
@Slf4j
@RestController
@RequestMapping("/api/districts")
@RequiredArgsConstructor
public class DistrictController {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${amap.web-service-key:${AMAP_WEBSERVICE_KEY:${VITE_AMAP_KEY:}}}")
    private String amapKey;

    private volatile List<DistrictRegion> cachedChinaDistricts;

    @GetMapping("/china")
    public ApiResult<List<DistrictRegion>> listChinaDistricts() {
        if (cachedChinaDistricts != null && !cachedChinaDistricts.isEmpty()) {
            return ApiResult.success(cachedChinaDistricts);
        }

        if (amapKey == null || amapKey.isBlank()) {
            return ApiResult.error(500, "未配置高德 WebService Key");
        }

        try {
            String url = UriComponentsBuilder
                    .fromHttpUrl("https://restapi.amap.com/v3/config/district")
                    .queryParam("keywords", "中国")
                    .queryParam("subdistrict", 2)
                    .queryParam("extensions", "base")
                    .queryParam("key", amapKey)
                    .toUriString();

            String body = restTemplate.getForObject(url, String.class);
            JsonNode root = objectMapper.readTree(body);
            if (!"1".equals(root.path("status").asText())) {
                String info = root.path("info").asText("高德行政区接口调用失败");
                log.warn("高德行政区接口返回异常: {}", info);
                return ApiResult.error(502, info);
            }

            JsonNode country = root.path("districts").isArray() && !root.path("districts").isEmpty()
                    ? root.path("districts").get(0)
                    : null;
            if (country == null) {
                return ApiResult.error(502, "高德行政区接口未返回中国行政区数据");
            }

            List<DistrictRegion> regions = parseProvinces(country.path("districts"));
            cachedChinaDistricts = regions;
            return ApiResult.success(regions);
        } catch (Exception e) {
            log.warn("读取行政区划失败: {}", e.getMessage());
            return ApiResult.error(502, "读取行政区划失败，请稍后重试");
        }
    }

    private List<DistrictRegion> parseProvinces(JsonNode provincesNode) {
        List<DistrictRegion> provinces = new ArrayList<>();
        if (!provincesNode.isArray()) {
            return provinces;
        }

        for (JsonNode provinceNode : provincesNode) {
            DistrictRegion province = toRegion(provinceNode);
            List<DistrictRegion> cities = new ArrayList<>();
            JsonNode cityNodes = provinceNode.path("districts");
            if (cityNodes.isArray()) {
                for (JsonNode cityNode : cityNodes) {
                    String level = cityNode.path("level").asText();
                    if ("city".equals(level)) {
                        cities.add(toRegion(cityNode));
                    }
                }
            }
            province.setChildren(cities);
            provinces.add(province);
        }
        return provinces;
    }

    private DistrictRegion toRegion(JsonNode node) {
        return DistrictRegion.builder()
                .name(node.path("name").asText())
                .adcode(node.path("adcode").asText())
                .citycode(node.path("citycode").asText())
                .level(node.path("level").asText())
                .build();
    }
}
