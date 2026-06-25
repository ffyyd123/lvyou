package com.lvyou.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lvyou.agent.service.ResearchKeywordPlanner;
import com.lvyou.model.request.TravelPlanRequest;
import com.lvyou.model.response.ResearchSource;
import com.lvyou.service.research.ToolGateway;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class WebResearchServiceImplTest {

    @Test
    @SuppressWarnings("unchecked")
    void cleanAndRankShouldDropSourcesThatDoNotMentionDestination() throws Exception {
        WebResearchServiceImpl service = new WebResearchServiceImpl(
                mock(RestTemplate.class),
                new ObjectMapper(),
                mock(ToolGateway.class),
                mock(ResearchKeywordPlanner.class)
        );

        TravelPlanRequest request = new TravelPlanRequest();
        request.setTo("厦门");
        request.setPreference("历史文化");

        ResearchSource irrelevant = ResearchSource.builder()
                .platform("网页")
                .title("山西平遥古城两日游攻略")
                .snippet("包含平遥古城、王家大院、云冈石窟。")
                .url("https://example.com/shanxi")
                .query("厦门 历史文化 攻略")
                .searchRound("路线框架")
                .evidenceType("public_search_result")
                .build();
        ResearchSource relevant = ResearchSource.builder()
                .platform("网页")
                .title("厦门鼓浪屿历史文化路线与开放时间")
                .snippet("厦门鼓浪屿、沙坡尾和中山路路线安排，提醒复核预约和开放状态。")
                .url("https://example.com/xiamen")
                .query("厦门 历史文化 攻略")
                .searchRound("路线框架")
                .evidenceType("public_search_result")
                .build();

        Method method = WebResearchServiceImpl.class
                .getDeclaredMethod("cleanAndRank", List.class, TravelPlanRequest.class);
        method.setAccessible(true);
        List<ResearchSource> cleaned = (List<ResearchSource>) method.invoke(service, List.of(irrelevant, relevant), request);

        assertThat(cleaned)
                .extracting(ResearchSource::getTitle)
                .containsExactly("厦门鼓浪屿历史文化路线与开放时间");
        assertThat(cleaned.get(0).getScore()).isGreaterThanOrEqualTo(45);
    }

    @Test
    @SuppressWarnings("unchecked")
    void cleanAndRankShouldDropGenericCityPagesForFoodQueries() throws Exception {
        WebResearchServiceImpl service = new WebResearchServiceImpl(
                mock(RestTemplate.class),
                new ObjectMapper(),
                mock(ToolGateway.class),
                mock(ResearchKeywordPlanner.class)
        );

        TravelPlanRequest request = new TravelPlanRequest();
        request.setTo("长治");
        request.setPreference("美食");

        ResearchSource baike = ResearchSource.builder()
                .platform("网页")
                .title("长治市_百度百科")
                .snippet("长治市，山西省辖地级市。")
                .url("https://baike.baidu.com/item/%E9%95%BF%E6%B2%BB%E5%B8%82/531740")
                .query("长治特色菜必吃餐厅推荐")
                .searchRound("食-真实餐厅")
                .evidenceType("public_search_result")
                .build();
        ResearchSource gov = ResearchSource.builder()
                .platform("网页")
                .title("Changzhi - 长治市人民政府门户网站")
                .snippet("长治市人民政府门户网站。")
                .url("https://www.changzhi.gov.cn/")
                .query("长治特色菜必吃餐厅推荐")
                .searchRound("食-真实餐厅")
                .evidenceType("public_search_result")
                .build();
        ResearchSource food = ResearchSource.builder()
                .platform("小红书")
                .title("长治本地人推荐的十家必吃餐厅和特色菜")
                .snippet("包含长治小吃、上党特色菜、老字号饭店和避坑建议。")
                .url("https://www.xiaohongshu.com/explore/changzhi-food")
                .query("长治特色菜必吃餐厅推荐")
                .searchRound("食-真实餐厅")
                .evidenceType("public_search_result")
                .build();

        Method method = WebResearchServiceImpl.class
                .getDeclaredMethod("cleanAndRank", List.class, TravelPlanRequest.class);
        method.setAccessible(true);
        List<ResearchSource> cleaned = (List<ResearchSource>) method.invoke(service, List.of(baike, gov, food), request);

        assertThat(cleaned)
                .extracting(ResearchSource::getTitle)
                .containsExactly("长治本地人推荐的十家必吃餐厅和特色菜");
        assertThat(cleaned.get(0).getScore()).isGreaterThanOrEqualTo(45);
    }
}
