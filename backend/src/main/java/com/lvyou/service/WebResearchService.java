package com.lvyou.service;

import com.lvyou.model.request.TravelPlanRequest;
import com.lvyou.model.response.ResearchReport;

public interface WebResearchService {

    ResearchReport research(TravelPlanRequest request);

    String formatForPrompt(ResearchReport report);
}
