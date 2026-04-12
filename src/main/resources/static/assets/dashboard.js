'use strict';

document.addEventListener('DOMContentLoaded', () => {
  initDashboard().catch((error) => {
    console.error(error);
    renderFatal(error);
  });
});

const DASHBOARD_STATE = {
  market: 'US',
  bootstrap: null,
  coverage: null,
  latestUndervalued: [],
  latestOvervalued: [],
  latestDiscovery: [],
};

const MARKET_LABELS = {
  US: '美国股票',
  CN: '中国股票',
  JP: '日本股票',
  HK: '香港股票',
};

async function initDashboard() {
  bindDashboardEvents();
  await (window.FairvalueTracker?.ready || Promise.resolve());
  DASHBOARD_STATE.bootstrap = await safeFetch('/platform/bootstrap');
  renderPlanPill(DASHBOARD_STATE.bootstrap);
  renderMarketTabs();
  await Promise.all([
    loadDashboardMarket(DASHBOARD_STATE.market),
    renderWatchlistPulse(),
  ]);
  renderDashboardActionGrid();
  renderFlowSummary();
  window.addEventListener('fairvalue:workspace-change', () => {
    void renderWatchlistPulse();
    renderDashboardActionGrid();
    renderFlowSummary();
  });
}

function bindDashboardEvents() {
  document.getElementById('refreshDashboardBtn')?.addEventListener('click', () => {
    void loadDashboardMarket(DASHBOARD_STATE.market);
    void renderWatchlistPulse();
  });
}

function renderMarketTabs() {
  const container = document.getElementById('dashboardMarketTabs');
  if (!container) {
    return;
  }
  container.innerHTML = Object.entries(MARKET_LABELS).map(([market, label]) => {
    const active = market === DASHBOARD_STATE.market ? ' is-active' : '';
    return `<button class="market-tab${active}" data-market-tab="${market}" type="button">${escapeHtml(label)}</button>`;
  }).join('');

  container.querySelectorAll('[data-market-tab]').forEach((button) => {
    button.addEventListener('click', async () => {
      const market = button.getAttribute('data-market-tab') || 'US';
      if (market === DASHBOARD_STATE.market) {
        return;
      }
      DASHBOARD_STATE.market = market;
      renderMarketTabs();
      await loadDashboardMarket(market);
      await renderWatchlistPulse();
    });
  });
}

async function loadDashboardMarket(market) {
  setListLoading('todayOpportunityList', '正在读取低估榜...');
  setListLoading('trendCandidateList', '正在读取趋势候选...');
  setListLoading('resonanceList', '正在组合双因子机会...');
  setListLoading('eventRadarList', '正在读取事件代理...');
  setListLoading('sectorHeatList', '正在计算行业热度...');

  const [undervalued, overvalued, discovery, coverage] = await Promise.all([
    safeFetch(`/v1/rankings/${market}/undervalued?page=1&size=8`),
    safeFetch(`/v1/rankings/${market}/overvalued?page=1&size=8`),
    safeFetch(`/v1/discovery/${market}?page=1&size=60`),
    market === 'US' ? safeFetch('/v1/us-equities-admin/ranking-coverage') : Promise.resolve(null),
  ]);

  DASHBOARD_STATE.coverage = coverage;
  DASHBOARD_STATE.latestUndervalued = undervalued?.items || [];
  DASHBOARD_STATE.latestOvervalued = overvalued?.items || [];
  DASHBOARD_STATE.latestDiscovery = discovery?.items || [];

  renderHeroSummary(market, undervalued, discovery, coverage);
  renderDisclaimer(market, undervalued, coverage);
  renderTodayOpportunityList(market, DASHBOARD_STATE.latestUndervalued);
  renderTrendCandidateList(market, DASHBOARD_STATE.latestDiscovery);
  renderResonanceList(market, DASHBOARD_STATE.latestUndervalued, DASHBOARD_STATE.latestDiscovery);
  renderEventRadarList(market, DASHBOARD_STATE.latestDiscovery, DASHBOARD_STATE.latestOvervalued);
  renderSectorHeatList(DASHBOARD_STATE.latestDiscovery);
  renderDashboardActionGrid();
  renderFlowSummary();
}

function renderHeroSummary(market, undervalued, discovery, coverage) {
  const title = document.getElementById('heroSummaryTitle');
  const grid = document.getElementById('heroSummaryGrid');
  const foot = document.getElementById('heroSummaryFoot');
  if (!title || !grid || !foot) {
    return;
  }

  const rankingItems = undervalued?.items || [];
  const discoveryItems = discovery?.items || [];
  const averageUpside = average(rankingItems.map((item) => asNumber(item.upside)));
  const highConvictionCount = rankingItems.filter((item) => asNumber(item.confidence) >= 0.6).length;
  const actionableCount = discoveryItems.filter((item) => asNumber(item.upside) > 0.12).length;
  const strictCoverage = asNumber(coverage?.rankableCoverage);
  title.textContent = `${MARKET_LABELS[market]} · 今日优先看 ${rankingItems.length || actionableCount || 0} 只`;

  const cards = [
    {
      label: '榜单样本',
      value: formatCount(rankingItems.length || discoveryItems.length),
      note: '首页第一眼先看低估与共振机会',
      cls: 'is-info',
    },
    {
      label: '平均上行空间',
      value: averageUpside == null ? '—' : formatPct(averageUpside),
      note: '来自当前加载榜单的真实估值空间',
      cls: averageUpside != null && averageUpside > 0 ? 'is-good' : 'is-info',
    },
    {
      label: '高置信候选',
      value: formatCount(highConvictionCount),
      note: '优先帮助你判断“值不值得继续打开”',
      cls: highConvictionCount > 0 ? 'is-good' : 'is-warn',
    },
    {
      label: market === 'US' ? '严格快照覆盖' : '候选池规模',
      value: market === 'US' ? formatPct(strictCoverage) : formatCount(discoveryItems.length),
      note: market === 'US' ? '覆盖越高，首页越接近严格排序' : '当前仍以候选池聚合承接',
      cls: market === 'US' && strictCoverage < 0.5 ? 'is-warn' : 'is-info',
    },
  ];

  grid.innerHTML = cards.map((card) => `
    <div class="hero-summary-item ${card.cls}">
      <span>${escapeHtml(card.label)}</span>
      <strong>${escapeHtml(card.value)}</strong>
      <small>${escapeHtml(card.note)}</small>
    </div>
  `).join('');

  foot.textContent = coverage?.disclaimer
    || `${MARKET_LABELS[market]} 首页当前以机会分发为先，趋势、事件和行业模块都基于真实结果做前端代理映射。`;
}

function renderDisclaimer(market, ranking, coverage) {
  const host = document.getElementById('dashboardDisclaimer');
  if (!host) {
    return;
  }
  const disclaimer = ranking?.disclaimer || coverage?.disclaimer || '';
  host.innerHTML = disclaimer ? `<div class="warning-banner">${escapeHtml(disclaimer)}</div>` : '';
}

function renderTodayOpportunityList(market, items) {
  const container = document.getElementById('todayOpportunityList');
  if (!container) {
    return;
  }
  if (!items.length) {
    container.innerHTML = emptyCard('当前没有拿到可展示的榜单数据，我们稍后再试。');
    return;
  }
  const rows = items.slice(0, 5).map((item) => renderCompactTickerRow(market, item, {
    signal: `公允 ${formatPrice(asNumber(item.fairValue || item.fairValueMid))}`,
    note: `上行 ${formatPct(asNumber(item.upside))} · ${verdictLabel(item.verdict)}`,
    actionText: '详情',
  }));
  container.innerHTML = renderListWithMore(rows, '/cn-undervalued-stocks.html', '查看更多标的');
  bindTickerCardActions(container, market);
}

function renderTrendCandidateList(market, items) {
  const container = document.getElementById('trendCandidateList');
  if (!container) {
    return;
  }
  const sorted = items.slice().sort((left, right) => {
    const changeDiff = Math.abs(asNumber(right.dailyChange)) - Math.abs(asNumber(left.dailyChange));
    if (Math.abs(changeDiff) > 0.0001) {
      return changeDiff;
    }
    return asNumber(right.turnover) - asNumber(left.turnover);
  });
  if (!sorted.length) {
    container.innerHTML = emptyCard('趋势代理候选暂时为空。');
    return;
  }
  const rows = sorted.slice(0, 6).map((item, index) => `
    <article class="dashboard-flow-card">
      <span>${index + 1}</span>
      <strong>${escapeHtml(item.companyName || item.ticker || '未命名标的')}</strong>
      <small>${escapeHtml(String(item.ticker || ''))} · ${escapeHtml(formatPct(asNumber(item.dailyChange)))} · ${escapeHtml(item.industry || item.sector || '未分类')}</small>
    </article>
  `);
  container.innerHTML = rows.join('');
}

function renderResonanceList(market, rankingItems, discoveryItems) {
  const container = document.getElementById('resonanceList');
  if (!container) {
    return;
  }
  const discoveryMap = new Map(discoveryItems.map((item) => [String(item.ticker || '').toUpperCase(), item]));
  const candidates = rankingItems.map((item) => {
    const ref = discoveryMap.get(String(item.ticker || '').toUpperCase()) || {};
    return {
      ...item,
      resonanceScore: asNumber(item.upside) * 0.55 + asNumber(item.confidence) * 0.30 + Math.max(asNumber(ref.dailyChange), 0) * 0.15,
      dailyChange: ref.dailyChange,
    };
  }).filter((item) => asNumber(item.upside) > 0.1);

  const sorted = candidates.sort((left, right) => right.resonanceScore - left.resonanceScore).slice(0, 5);
  if (!sorted.length) {
    container.innerHTML = emptyCard('当前没有形成明显“双因子共振”的股票，可以先从低估榜进入。');
    return;
  }
  const rows = sorted.map((item) => renderCompactTickerRow(market, item, {
    signal: `共振分 ${item.resonanceScore.toFixed(2)}`,
    note: `估值 ${formatPct(asNumber(item.upside))} · 日变动 ${formatPct(asNumber(item.dailyChange))}`,
    badge: '共振',
    actionText: '详情',
  }));
  container.innerHTML = renderListWithMore(rows, '/valuation-screener.html', '查看更多共振');
  bindTickerCardActions(container, market);
}

function renderEventRadarList(market, discoveryItems, overvaluedItems) {
  const container = document.getElementById('eventRadarList');
  if (!container) {
    return;
  }
  const movers = discoveryItems.slice().sort((left, right) => Math.abs(asNumber(right.dailyChange)) - Math.abs(asNumber(left.dailyChange)));
  const risks = (overvaluedItems || []).slice(0, 2).map((item) => ({ ...item, radarType: '风险挤兑' }));
  const opportunities = movers.slice(0, 3).map((item) => ({ ...item, radarType: '异动窗口' }));
  const merged = [...opportunities, ...risks].slice(0, 5);
  if (!merged.length) {
    container.innerHTML = emptyCard('事件代理模块还在逐步接后端日历，当前没有可展示候选。');
    return;
  }
  const rows = merged.map((item) => renderCompactTickerRow(market, item, {
    signal: item.radarType || '事件代理',
    note: item.radarType === '风险挤兑'
      ? `高估压力 ${formatPct(asNumber(item.upside))}`
      : `当日异动 ${formatPct(asNumber(item.dailyChange))}`,
    badge: item.radarType || '事件',
    actionText: '详情',
  }));
  container.innerHTML = renderListWithMore(rows, '/valuation-screener.html', '查看更多事件');
  bindTickerCardActions(container, market);
}

function renderSectorHeatList(items) {
  const container = document.getElementById('sectorHeatList');
  if (!container) {
    return;
  }
  if (!items.length) {
    container.innerHTML = emptyCard('行业热度需要基于候选池聚合，当前数据不足。');
    return;
  }

  const buckets = new Map();
  items.forEach((item) => {
    const sector = String(item.industry || item.sector || '未分类').trim() || '未分类';
    if (!buckets.has(sector)) {
      buckets.set(sector, []);
    }
    buckets.get(sector).push(item);
  });

  const rows = [...buckets.entries()].map(([sector, list]) => ({
    sector,
    count: list.length,
    avgUpside: average(list.map((item) => asNumber(item.upside))),
    avgConfidence: average(list.map((item) => asNumber(item.confidence) * 100)),
  })).sort((left, right) => (right.avgUpside || 0) - (left.avgUpside || 0)).slice(0, 6);

  container.innerHTML = rows.map((row) => `
    <article class="dashboard-sector-row">
      <strong>${escapeHtml(row.sector)}</strong>
      <small>${escapeHtml(`候选 ${formatCount(row.count)} · 置信 ${formatPct((row.avgConfidence || 0) / 100)}`)}</small>
      <div class="dashboard-sector-stats">
        <span class="dashboard-data-pill ${row.avgUpside > 0 ? 'is-positive' : 'is-negative'}">${escapeHtml(formatPct(row.avgUpside))}</span>
      </div>
    </article>
  `).join('');
}

async function renderWatchlistPulse() {
  const container = document.getElementById('watchlistPulse');
  if (!container) {
    return;
  }
  const watchlist = (window.FairvalueTracker?.getWatchlist?.() || []).slice(0, 4);
  if (!watchlist.length) {
    container.innerHTML = `
      <div class="empty-card">
        还没有自选股。先把今天值得继续跟踪的股票加入工作区，首页才会形成稳定回流。
        <div class="dashboard-ticker-actions" style="margin-top:12px; justify-content:center;">
          <a class="ticker-action" href="/valuation-screener.html">先去筛选器</a>
          <a class="ticker-action is-active" href="/user-center.html">创建观察清单</a>
        </div>
      </div>
    `;
    return;
  }
  container.innerHTML = '<div class="empty-card">正在刷新你的自选脉冲...</div>';

  const rows = await Promise.all(watchlist.map(async (item) => {
    if (item.market !== 'US') {
      return renderWatchlistRow(item, null, null);
    }
    const summary = await safeFetch(`/v1/us-equities/${item.ticker}/valuation/summary`);
    const report = await safeFetch(`/v1/us-equities/${item.ticker}/valuation/report`);
    return renderWatchlistRow(item, summary, report);
  }));

  container.innerHTML = renderListWithMore(rows, '/user-center.html', '管理我的自选');
  bindTickerCardActions(container, 'US');
}

function renderWatchlistRow(item, summary, report) {
  const market = item.market || 'US';
  const ticker = item.ticker || '';
  const currentPrice = asNumber(summary?.currentPrice);
  const confidence = asNumber(summary?.confidenceLevel);
  const riskCount = Array.isArray(report?.riskMatrix) ? report.riskMatrix.length : 0;
  return `
    <article class="dashboard-watch-row">
      <div class="dashboard-watch-grid">
        <div class="dashboard-watch-name">
          <a href="${detailHref(market, ticker, 'watchlist-pulse')}">${escapeHtml(item.companyName || ticker)}</a>
          <small>${escapeHtml(ticker)}</small>
        </div>
        <div class="dashboard-watch-price">
          <strong>${formatPrice(currentPrice)}</strong>
          <span>${Number.isFinite(confidence) ? `置信 ${Math.round(confidence * 100)}%` : '待跟踪'}</span>
        </div>
        <div class="dashboard-watch-signal">
          <strong>${escapeHtml(verdictLabel(summary?.verdict || '待判断'))}</strong>
          <small>${escapeHtml(`风险 ${formatCount(riskCount)} · 公允 ${formatPrice(asNumber(summary?.fairValueRange?.mid))}`)}</small>
        </div>
        <div class="dashboard-watch-actions">
          <a class="ticker-action is-active" href="${detailHref(market, ticker, 'watchlist-pulse')}">详情</a>
          <button class="ticker-action" type="button" data-create-alert="${escapeHtml(ticker)}" data-market="${escapeHtml(market)}" data-company-name="${escapeHtml(item.companyName || ticker)}">提醒</button>
        </div>
      </div>
    </article>
  `;
}

function renderCompactTickerRow(market, item, options) {
  const ticker = String(item.ticker || '').toUpperCase();
  const name = item.companyName || item.name || ticker;
  const currentPrice = asNumber(item.currentPrice || item.price);
  const dailyChange = asNumber(item.dailyChange);
  const confidence = asNumber(item.confidence);
  const inWatchlist = Boolean(window.FairvalueTracker?.hasInWatchlist?.(ticker, market));
  const subline = Number.isFinite(dailyChange)
    ? formatPct(dailyChange)
    : Number.isFinite(confidence)
      ? `置信 ${Math.round(confidence * 100)}%`
      : '待更新';
  const tone = Number.isFinite(dailyChange)
    ? (dailyChange >= 0 ? 'is-positive' : 'is-negative')
    : '';
  return `
    <article class="dashboard-ticker-row">
      <div class="dashboard-ticker-grid">
        <div class="dashboard-ticker-name">
          <a href="${detailHref(market, ticker, options.badge || 'dashboard')}">${escapeHtml(name)}</a>
          <small>${escapeHtml(ticker)}</small>
        </div>
        <div class="dashboard-ticker-price">
          <strong>${formatPrice(currentPrice)}</strong>
          <span class="${tone}">${escapeHtml(subline)}</span>
        </div>
        <div class="dashboard-ticker-signal">
          <strong>${escapeHtml(options.signal || '继续进入详情判断')}</strong>
          <small>${escapeHtml(options.note || '')}</small>
        </div>
        <div class="dashboard-ticker-actions">
          <a class="ticker-action is-active" href="${detailHref(market, ticker, options.badge || 'dashboard')}">${escapeHtml(options.actionText || '详情')}</a>
          <button class="ticker-action" type="button" data-watch-toggle="${escapeHtml(ticker)}" data-market="${escapeHtml(market)}" data-company-name="${escapeHtml(name)}">${inWatchlist ? '已自选' : '自选'}</button>
          <button class="ticker-action" type="button" data-create-alert="${escapeHtml(ticker)}" data-market="${escapeHtml(market)}" data-company-name="${escapeHtml(name)}">提醒</button>
        </div>
      </div>
    </article>
  `;
}

function renderListWithMore(rows, href, label) {
  const footer = href ? `<a class="subtle-link" href="${escapeHtml(href)}">${escapeHtml(label || '查看更多')}</a>` : '';
  return `${rows.join('')}<div style="padding-top:4px;">${footer}</div>`;
}

function bindTickerCardActions(container, defaultMarket) {
  container.querySelectorAll('[data-watch-toggle]').forEach((button) => {
    button.addEventListener('click', () => {
      const ticker = button.getAttribute('data-watch-toggle') || '';
      const market = button.getAttribute('data-market') || defaultMarket || 'US';
      const companyName = button.getAttribute('data-company-name') || ticker;
      const active = window.FairvalueTracker?.toggleWatchlist?.({ ticker, market, companyName });
      window.FairvalueTracker?.saveFlowContext?.({
        lastSourcePage: 'dashboard',
        lastSourceLabel: '机会总览',
        lastActionLabel: active ? '从首页加入自选' : '从首页移出自选',
        lastViewedTicker: ticker,
        lastViewedMarket: market,
      });
      button.textContent = active ? '已自选' : '自选';
      window.FairvalueUi?.toast?.(active ? `${ticker} 已加入自选` : `${ticker} 已移出自选`, 'success');
      void renderWatchlistPulse();
      renderDashboardActionGrid();
      renderFlowSummary();
    });
  });

  container.querySelectorAll('[data-create-alert]').forEach((button) => {
    button.addEventListener('click', () => {
      const ticker = button.getAttribute('data-create-alert') || '';
      const market = button.getAttribute('data-market') || defaultMarket || 'US';
      const companyName = button.getAttribute('data-company-name') || ticker;
      window.FairvalueTracker?.upsertValuationAlert?.(ticker, market, companyName, {
        type: 'undervalue_zone',
        groupId: 'core-holdings',
        groupLabel: '核心底仓',
        sourceContext: 'dashboard-card',
      });
      window.FairvalueTracker?.saveFlowContext?.({
        lastSourcePage: 'dashboard',
        lastSourceLabel: '机会总览',
        lastActionLabel: '从首页创建提醒',
        lastAlertTemplate: 'undervalue_zone',
        lastViewedTicker: ticker,
        lastViewedMarket: market,
      });
      window.FairvalueUi?.toast?.(`${ticker} 已创建提醒`, 'success');
      renderDashboardActionGrid();
      renderFlowSummary();
    });
  });
}

function renderDashboardActionGrid() {
  const host = document.getElementById('dashboardActionGrid');
  if (!host) {
    return;
  }
  const meta = window.FairvalueTracker?.getWorkspaceMeta?.() || {};
  const summary = window.FairvalueTracker?.getWorkspaceSummary?.() || {};
  const recentTicker = meta.lastViewedTicker || '';
  const recentMarket = meta.lastViewedMarket || 'US';
  const cards = [
    {
      label: '最近动作',
      title: recentTicker ? `继续看 ${recentTicker}` : '先创建第一组观察清单',
      note: recentTicker ? '你最近已经打开过详情，回去做判断会更顺。' : '把今天值得继续跟踪的股票沉淀到工作区。',
      href: recentTicker ? detailHref(recentMarket, recentTicker, 'upgrade-strip') : '/user-center.html',
      cta: recentTicker ? '继续查看' : '创建清单',
    },
    {
      label: '工作区同步',
      title: `${formatCount(summary.watchlistCount || 0)} 只自选 / ${formatCount(summary.enabledAlertCount || 0)} 条提醒`,
      note: summary.storageMode === 'server_sync' ? '当前工作区已同步到后端，可跨页面回流。' : '当前以浏览器工作区为主，也会保留本地缓存回退。',
      href: '/user-center.html',
      cta: '打开提醒中心',
    },
  ];
  host.innerHTML = cards.map((card) => `
    <article class="action-chip">
      <span>${escapeHtml(card.label)}</span>
      <strong>${escapeHtml(card.title)}</strong>
      <small>${escapeHtml(card.note)}</small>
      <a class="ticker-action ${card.label === '最近动作' ? 'is-active' : ''}" href="${escapeHtml(card.href)}">${escapeHtml(card.cta)}</a>
    </article>
  `).join('');
}

function renderFlowSummary() {
  const host = document.getElementById('dashboardFlowSummary');
  if (!host) {
    return;
  }
  const meta = window.FairvalueTracker?.getWorkspaceMeta?.() || {};
  const trendCount = DASHBOARD_STATE.latestDiscovery.slice(0, 6).length;
  const bullCount = DASHBOARD_STATE.latestDiscovery.filter((item) => asNumber(item.upside) > 0.12).length;
  const bearCount = DASHBOARD_STATE.latestOvervalued.filter((item) => asNumber(item.upside) < -0.05).length;
  const recentSource = meta.lastSourceLabel || '首页机会';
  const recentAction = meta.lastActionLabel || '先从低估榜或共振榜打开一只股票';
  host.innerHTML = `
    <article class="dashboard-flow-card">
      <span>今日趋势研判</span>
      <strong>${escapeHtml(`当前候选池中有 ${trendCount} 只可继续跟踪标的，优先看上行空间和工作区回流最明确的股票。`)}</strong>
      <small>${escapeHtml(`最近动作来源：${recentSource} · ${recentAction}`)}</small>
      <div class="dashboard-flow-metrics">
        <div class="dashboard-flow-metric">
          <em>多头候选</em>
          <strong>${escapeHtml(formatCount(bullCount))}</strong>
        </div>
        <div class="dashboard-flow-metric">
          <em>风险样本</em>
          <strong>${escapeHtml(formatCount(bearCount))}</strong>
        </div>
      </div>
      <a class="ticker-action is-active" href="/valuation-screener.html">进入趋势实验室</a>
    </article>
  `;
}

function detailHref(market, ticker, source) {
  const from = source ? `&from=${encodeURIComponent(String(source).toLowerCase())}` : '&from=dashboard';
  if (market === 'CN') {
    return `/cn-stock-detail.html?ticker=${encodeURIComponent(ticker)}${from}`;
  }
  if (market === 'JP') {
    return `/jp-stock-detail.html?code=${encodeURIComponent(ticker)}${from}`;
  }
  return `/us-stock-detail.html?ticker=${encodeURIComponent(ticker)}${from}`;
}

function renderPlanPill(bootstrap) {
  const pill = document.getElementById('dashboardPlanPill');
  if (!pill) {
    return;
  }
  const plan = bootstrap?.planCode || bootstrap?.plan_code || '免费试用';
  pill.textContent = plan === 'starter' ? 'Starter' : String(plan).toUpperCase();
}

async function safeFetch(url) {
  try {
    return await apiFetch(url);
  } catch (error) {
    console.warn('dashboard fetch failed', url, error);
    return null;
  }
}

async function apiFetch(url, options = {}) {
  if (window.FairvaluePlatformApi && typeof window.FairvaluePlatformApi.fetchJson === 'function') {
    return deepCamelCase(await window.FairvaluePlatformApi.fetchJson(url, options));
  }
  const response = await fetch(url, options);
  const payload = await response.json();
  if (!response.ok) {
    const message = payload?.message || payload?.error?.message || `HTTP ${response.status}`;
    throw new Error(message);
  }
  return deepCamelCase(unwrapEnvelope(payload));
}

function unwrapEnvelope(payload) {
  if (payload && typeof payload === 'object' && 'success' in payload) {
    return payload.success === false ? (payload.error || payload) : payload.data;
  }
  return payload;
}

function deepCamelCase(value) {
  if (Array.isArray(value)) {
    return value.map(deepCamelCase);
  }
  if (value && typeof value === 'object') {
    return Object.fromEntries(Object.entries(value).map(([key, entry]) => [toCamel(key), deepCamelCase(entry)]));
  }
  return value;
}

function toCamel(key) {
  return String(key).replace(/_([a-z])/g, (_, char) => char.toUpperCase());
}

function setListLoading(id, text) {
  const element = document.getElementById(id);
  if (element) {
    element.innerHTML = emptyCard(text);
  }
}

function emptyCard(text) {
  return `<div class="empty-card">${escapeHtml(text)}</div>`;
}

function renderFatal(error) {
  const host = document.getElementById('dashboardGrid');
  if (host) {
    host.innerHTML = `<div class="app-panel empty-card">首页机会总览加载失败：${escapeHtml(error.message || '未知错误')}</div>`;
  }
}

function verdictLabel(value) {
  const raw = String(value || '').toUpperCase();
  if (raw.includes('UNDER')) return '低估';
  if (raw.includes('OVER')) return '高估';
  if (raw.includes('FAIR')) return '合理';
  if (raw.includes('STALE')) return '价格陈旧';
  return value || '待判断';
}

function formatPrice(value) {
  const amount = asNumber(value);
  if (!Number.isFinite(amount) || amount <= 0) {
    return '—';
  }
  if (amount >= 1000) {
    return `$${amount.toLocaleString('en-US', { maximumFractionDigits: 0 })}`;
  }
  if (amount >= 100) {
    return `$${amount.toFixed(1)}`;
  }
  return `$${amount.toFixed(2)}`;
}

function formatPct(value) {
  const amount = asNumber(value);
  if (!Number.isFinite(amount)) {
    return '—';
  }
  const pct = Math.abs(amount) > 1 ? amount : amount * 100;
  return `${pct >= 0 ? '+' : ''}${pct.toFixed(1)}%`;
}

function formatCount(value) {
  const amount = asNumber(value);
  if (!Number.isFinite(amount)) {
    return '—';
  }
  return Math.round(amount).toLocaleString('en-US');
}

function asNumber(value) {
  if (typeof value === 'number') {
    return value;
  }
  if (value == null || value === '') {
    return NaN;
  }
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : NaN;
}

function average(values) {
  const clean = values.filter((value) => Number.isFinite(value));
  if (!clean.length) {
    return null;
  }
  return clean.reduce((sum, value) => sum + value, 0) / clean.length;
}

function escapeHtml(value) {
  return String(value || '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}
