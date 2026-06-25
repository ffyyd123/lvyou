# Long-Term Memory

## Travel Planning and Tool Notes

- **Context**: Observed during planning historical and cultural trips from Beijing to Shanxi. User requests include multi-day and 1-day trips with a preference for relaxed pacing ("每天不要太累") and real, usable attractions ("优先真实可用景点"). Recent requests explicitly mention "历史文化" preferences, extra requirements to test progress and preserve history, verify interfaces before field testing ("网页实测前接口验证"), provide multiple options, images, and clear map routes, and occasionally disable online planning. Online planning is enabled for real-time route adjustment when specified. **Recent focus**: User requirements have expanded to include logistical planning: prioritize less backtracking ("少折返"), ensure a smooth route ("路线顺路"), and incorporate practical details like real restaurants, hotels, and weather suggestions. A specific request for a **2-day history and culture trip from Beijing to Shanxi** (online planning disabled) was received, emphasizing these expanded logistics.

- **Search Tool Capabilities & Limitations**: The search tool has high data coverage for Beijing and Xi'an but critically low and often inaccurate for the Shanxi region. Confirmed no results for many specific Shanxi landmarks (e.g., "晋祠", "云冈石窟", "华严寺", "平遥古城", "五台山", "山西博物院", "九龙壁", "显通寺") and broader keywords (e.g., "景点", "博物院", "华严寺", "古城墙"). Searches for "历史文化" or "历史" in Shanxi cities (Datong, Taiyuan, Pingyao) sometimes return limited Shanxi POIs but frequently mix in irrelevant results from other major cities, indicating persistent data inaccuracy and poor geographic filtering. When results are found, they may contain inconsistencies, underscoring the need for verification. **Tool applications for logistics**: Search tools (`search_amap_place`, `search_amap_weather`) are used to source practical travel logistics. **Limitation noted**: The `search_amap_weather` tool for Datong has returned a "USERKEY_PLAT_NOMATCH" error, indicating unreliable weather data retrieval for that location.

- **Key POI Coordinates and Stay Times**:
  - **Beijing (Verified Internal Data & Tool-Sourced)**:
    - Forbidden City: (39.9163, 116.3972), stay 90 minutes.
    - Tiananmen Square: (39.9087, 116.3975), stay 90 minutes.
    - Badaling Great Wall: (40.3597, 116.0200), stay 90 minutes.
    - Temple of Heaven: (39.8822, 116.4066), stay 90 minutes.
    - Summer Palace (颐和园): (39.9999, 116.2755), stay 90 minutes.
    - Nanluoguxiang (南锣鼓巷): (39.9380, 116.4034), stay 90 minutes.
  - **Shanxi (Verified Internal Data)**:
    - Datong Ancient Wall: (40.0758, 113.2975), stay 120 minutes.
    - Yungang Grottoes: (40.1126, 113.1259), stay 180 minutes.
    - Huayan Temple: (40.0885, 113.2885), stay 120 minutes.
    - Shanhua Temple: (40.0834, 113.2961), stay 90 minutes. [Note: Located near Datong Ancient Wall.]
    - Pingyao Ancient City center: (37.8706, 112.5489).
    - Rishengchang Exchange Shop: (37.2010, 112.1764), stay 90 minutes. [Note: Located within Pingyao Ancient City.]
    - Wutaishan: (39.0136, 113.5902), stay 90 minutes.
    - Hukou Waterfall: (36.1477, 110.4436), stay 90 minutes.
  - **Shanxi (Tool-Sourced Data, Potentially Inconsistent)**:
    - 悬空寺: (39.6689, 113.7136), 90 min.
    - 云冈石窟: (40.1106, 113.1320), 90 min. [Note: coordinate precision and stay time differ from verified internal data.]
    - 平遥古城: (37.2021, 112.1784), 90 min.
    - 乔家大院: (37.3542, 112.4311), 90 min.
    - 应县木塔: (39.5681, 113.1916), 90 min.
    - 晋祠: (37.7089, 112.4291), 90 min.

- **Inter-city Travel Segments**: Key calculated distances and drive times. Note: Distances may vary based on exact start/end coordinates and route; times are estimates.
  - **Beijing to Shanxi**:
    - Beijing to Datong Ancient Wall: 264.6 km (397 minutes).
    - Beijing to Yungang Grottoes: ~280.4 km (421 minutes) / ~279.9 km (420 minutes, tool data). [Validation Note: 420 minutes exceeds a 180-minute single-day drive limit, confirming the need for multi-day travel or intermediate stops.]
    - Beijing to Pingyao Ancient City: ~403.3 km (605 minutes).
    - Beijing to 悬空寺: ~231.6 km (347 minutes, tool data).
    - Beijing to 应县木塔: ~277.5 km (416 minutes, tool data).
    - Beijing to 晋祠: ~422.3 km (633 minutes, tool data).
    - Beijing to Badaling Great Wall: 60.4 km (91 minutes).
    - Badaling Great Wall to Yungang Grottoes: 246.7 km (370 minutes).
    - Forbidden City to Yungang Grottoes: 278.9 km (418 minutes).
    - Forbidden City to Tiananmen: 0.8 km (1 min); Tiananmen to Temple of Heaven: 3.0 km (5 min).
  - **Within Shanxi**:
    - Yungang Grottoes to Huayan Temple: 14.1 km (21 minutes).
    - Huayan Temple to Datong Ancient Wall: 1.6 km (2 minutes).
    - Datong Ancient Wall to Yungang Grottoes: 15.2 km (23 minutes).
    - 云冈石窟 to 悬空寺: ~69.8 km (105 minutes, tool data).
    - 云冈石窟 to 应县木塔: ~60.5 km (91 minutes, tool data).
    - 悬空寺 to 应县木塔: ~46.1 km (69 minutes, tool data).
    - Datong Ancient Wall to Xuankong Temple: 57.5 km (86 minutes).
    - Huayan Temple to coordinates (39.02, 113.58): 121.4 km (182 minutes). [Note: (39.02, 113.58) may be Wuye Temple area; coordinates not confirmed.]
    - Yungang Grottoes to Wuye Temple: ~220.5 km (210 minutes).
    - Yungang Grottoes to Wutai Mountain: 128.1 km (192 minutes).
    - Wutaishan to Pingyao: 236.3 km (354 minutes). [Note: Previous estimate was 301.2 km (270 minutes); data may vary.]
    - Pingyao to Beijing: ~474.9 km (712 minutes) / ~600.5 km (360 minutes).
    - 平遥古城 to 乔家大院: 28.0 km (42 minutes, tool data).
    - Yingxian Wooden Pagoda (应县木塔) to Wutai Mountain (五台山): 70.6 km, estimated 106 minutes.
    - Qiao Family Compound to Jinci Temple: 39.4 km (59 minutes).
    - Datong Ancient Wall to Shanhua Temple: 0.9 km (1 minute).
    - Shanhua Temple to Yungang Grottoes: 14.3 km (21 minutes).
    - 云冈石窟 to 大同古城墙: 14.6 km (22 minutes).

- **Fallback Planning & Detailed Itinerary Generation**: In response to tool limitations for Shanxi, the assistant generates detailed itineraries using internal knowledge.
  - For a **5-day plan**, themes include:
    - Day 1: "Beijing Royal History" (Forbidden City, Tiananmen Square).
    - Day 2: "Great Wall and Datong" (Badaling Great Wall, Datong Ancient Wall).
    - Day 3: "Datong Grottoes and Temples" (Yungang Grottoes, Huayan Temple).
    - Day 4: "Wutaishan Buddhism" (Wuye Temple, Xiantong Temple, Tayuan Temple).
    - Day 5: "Pingyao Merchant Culture" (Pingyao Ancient City, Rishengchang Exchange Shop, Pingyao Ancient Wall).
  - For **1-day and 2-day plans**, examples include:
    - "大同古都历史文化之旅" (1-day) itinerary: Yungang Grottoes, Huayan Temple, and Datong Ancient Wall. Validated route with these three POIs has total drive time 46 minutes and stay time 520 minutes, confirmed reasonable.
    - "山西历史文化一日游" itinerary: 云冈石窟 and 悬空寺, with drive time 105 minutes, stay time 180 minutes (total 285 minutes), confirmed reasonable.
    - Validated 1-day Datong routes with 3 or 4 POIs (e.g., 云冈石窟, 华严寺, 大同古城墙, 善化寺) have been confirmed reasonable with short drive times.
    - **2-day planning context**: For trips starting from Beijing, multi-day itineraries are necessary due to long drive times (e.g., >6 hours). Planning includes evaluating POIs within a region (e.g., Datong's Yungang Grottoes, Huayan Temple, Shanhua Temple, Datong Ancient Wall) and incorporating logistical stops for meals and accommodation.
  - Can generate multiple itinerary options tailored to preferences.
  - Always include a note to verify attraction status before visiting.

- **Travel Planning Output & Validation**:
  - The required output format for travel route planning is JSON, avoiding markdown code blocks.
  - Route validation examples: Verified 1-day and multi-day routes with POI counts, drive times, and stay times are used to confirm reasonableness. A key validation is that drive times should not exceed comfortable limits for a single day.
  - **Verification strategy**: Verify search results against known facts; validate route times before outputting; always note attraction status should be confirmed before visiting; **incorporate "网页实测前接口验证" (interface verification before field testing) when specified by user.**

- **Actionable Insights**:
  1.  **Data Gap & Inaccuracy**: Search tool data coverage is high for Beijing and Xi'an but critically low and often inaccurate for Shanxi; supplement tool-based searches with known internal data and verify tool-sourced POI data when possible.
  2.  **Geographic Filtering Failures**: Shanxi searches frequently return irrelevant results from other major cities; always cross-check returned POIs for geographic relevance.
  3.  **Fallback Success**: The assistant can construct feasible, detailed routes and itineraries using internal knowledge when search tools fail.
  4.  **User Preferences**: Plan for relaxed pacing, prioritize real, usable attractions, incorporate requirements to test progress, preserve history, verify interfaces, and provide multiple options, images, and clear map routes when specified. **Updated**: Also include logistical planning for smooth, low-backtrack routes and sourcing practical details (restaurants, hotels, weather) as needed for destination cities like Datong.
  5.  **Planning Method**: When planning trips involving regions with data limitations, use fallback strategies and internal knowledge. For long distances from Beijing to Shanxi, inherently plan for multi-day trips.
  6.  **Output Format**: Always use JSON for travel route planning output.
  7.  **New Beijing Discoveries**: Additional Beijing historical sites (Summer Palace, Nanluoguxiang) with coordinates and stay times have been identified and integrated into planning capabilities.
  8.  **Logistical Expansion**: User's extra requirements now encompass practical travel logistics. Use available search tools (`search_amap_place`, `search_amap_weather`) to source information on accommodations, dining, and weather for destination cities (e.g., Datong) to fulfill requests for "real restaurants, hotels, and weather suggestions."
  9.  **Tool Reliability Note**: Be aware of tool-specific failures, such as the `search_amap_weather` error for Datong, and have contingency plans (e.g., noting the limitation and suggesting manual checks).