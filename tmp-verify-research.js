const fs = require('fs');

(async () => {
  const payload = {
    from: '北京',
    to: '山西省长治市',
    days: 3,
    preference: '历史文化 美食',
    userIdea: '长治特色菜必吃餐厅推荐，住宿、交通、景点路线，需要真实点位，小红书抖音热门体验',
    onlineResearch: true
  };

  const res = await fetch('http://127.0.0.1:8080/api/research/preview', {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify(payload)
  });
  const json = await res.json();
  fs.writeFileSync('tmp-research-verification.json', JSON.stringify(json, null, 2));
  const data = json.data || json;
  const traces = data.traces || [];
  const sampleTrace = traces.find(t => (t.keyword || '').includes('特色菜') || (t.direction === '食' && t.provider === 'WebSearchService')) || traces[0];
  const allSources = traces.flatMap(t => t.rawSources || []);
  const filtered = allSources
    .filter(s => s.cleanStatus === 'filtered')
    .slice(0, 8)
    .map(s => ({ title: s.title, query: s.query, platform: s.platform, reason: s.rejectReason }));
  const retained = (data.sources || [])
    .slice(0, 16)
    .map(s => ({ title: s.title, platform: s.platform, type: s.evidenceType, query: s.query, score: s.score }));

  console.log(JSON.stringify({
    ok: json.success !== false,
    status: data.status,
    destination: data.destination,
    raw: data.rawSourceCount,
    cleaned: data.cleanedSourceCount,
    traceCount: traces.length,
    providers: Object.fromEntries(Object.entries(traces.reduce((m, t) => {
      m[t.provider] = (m[t.provider] || 0) + 1;
      return m;
    }, {})).sort()),
    sampleTrace: sampleTrace && {
      provider: sampleTrace.provider,
      keyword: sampleTrace.keyword,
      executedKeyword: sampleTrace.executedKeyword,
      rawCount: sampleTrace.rawCount,
      cleanedCount: sampleTrace.cleanedCount,
      message: sampleTrace.message,
      rawSources: (sampleTrace.rawSources || []).slice(0, 5).map(s => ({
        title: s.title,
        status: s.cleanStatus,
        retained: s.retained,
        reason: s.rejectReason
      }))
    },
    filtered,
    retained
  }, null, 2));
})();
