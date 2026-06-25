package com.lvyou.agent.data;

import com.lvyou.agent.model.PoiInfo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PoiDataStoreTest {

    @Test
    void searchShouldNotFallbackToOtherCitiesWhenDestinationIsUnknown() {
        PoiDataStore store = new PoiDataStore();
        store.init();

        List<PoiInfo> pois = store.search("厦门", "历史文化");

        assertThat(pois).isEmpty();
    }

    @Test
    void searchShouldOnlyReturnDestinationPoisForCoveredCity() {
        PoiDataStore store = new PoiDataStore();
        store.init();

        List<PoiInfo> pois = store.search("北京", "历史文化");

        assertThat(pois).isNotEmpty();
        assertThat(pois).allMatch(poi -> "北京".equals(poi.getCity()));
    }
}
