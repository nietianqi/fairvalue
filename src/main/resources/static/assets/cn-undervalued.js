'use strict';

/**
 * cn-undervalued.js  —  Global Rankings / Discovery Page
 *
 * Modes:
 *   undervalued / overvalued  →  GET /v1/rankings/{market}/{mode}?page=1&size=30
 *   high52 / low52 / active / gainers / losers  →  GET /v1/discovery/{market}?page=1&size=30
 *
 * For US market rows in ranking mode, ticker is rendered as a link to /us-stock-detail.html
 */

// ─── State ───────────────────────────────────────────────────────────────────

const state = {
  market: 'CN',
  mode: 'undervalued',
  rows: [],
  page: 1,
  total: 0,
  pageSize: 30,
  usingFallback: false,
  sortKey: 'upside',
  sortDir: 'desc',
};

// ─── DOM refs ─────────────────────────────────────────────────────────────────

const tableBody     = document.getElementById('tableBody');
const countrySelect = document.getElementById('country');
const tabs          = [...document.querySelectorAll('.tab')];
const headers       = [...document.querySelectorAll('thead th')];
const footnote      = document.querySelector('.footnote');
const tableWrap     = document.querySelector('.table-wrap');

// Disclaimer banner (injected dynamically; sits above the table)
let disclaimerEl = null;
let pagerEl = null;
let pagerStatusEl = null;
let pagerButtonEl = null;

// ─── Event Listeners ──────────────────────────────────────────────────────────

countrySelect.addEventListener('change', async (e) => {
  state.market = e.target.value;
  resetRows();
  await loadRows();
});

tabs.forEach((tab) => {
  tab.addEventListener('click', () => {
    tabs.forEach((t) => t.classList.remove('active'));
    tab.classList.add('active');
    state.mode = tab.dataset.mode;
    applyModeSort();
    resetRows();
    loadRows();
  });
});

headers.forEach((header) => {
  const key = header.dataset.sort;
  if (!key) return;
  header.addEventListener('click', () => {
    if (state.sortKey === key) {
      state.sortDir = state.sortDir === 'asc' ? 'desc' : 'asc';
    } else {
      state.sortKey = key;
      state.sortDir = 'desc';
    }
    renderRows();
  });
});

// ─── Fetch Layer ──────────────────────────────────────────────────────────────

/** Rankings API — undervalued / overvalued modes */
async function fetchRankings(market, mode, page = 1, size = 30) {
  const url = `/v1/rankings/${encodeURIComponent(market)}/${encodeURIComponent(mode)}?page=${page}&size=${size}`;
  if (window.FairvaluePlatformApi && typeof window.FairvaluePlatformApi.fetchJson === 'function') {
    return deepCamelCase(await window.FairvaluePlatformApi.fetchJson(url));
  }
  const res = await fetch(url);
  if (!res.ok) throw new Error(`Rankings API failed: ${res.status}`);
  return deepCamelCase(unwrapApiEnvelope(await res.json()));
}

/** Discovery API — 52w high/low, active, gainers, losers */
async function fetchDiscovery(market, page = 1, size = 30) {
  const url = `/v1/discovery/${encodeURIComponent(market)}?page=${page}&size=${size}`;
  if (window.FairvaluePlatformApi && typeof window.FairvaluePlatformApi.fetchJson === 'function') {
    return deepCamelCase(await window.FairvaluePlatformApi.fetchJson(url));
  }
  const res = await fetch(url);
  if (!res.ok) throw new Error(`Discovery API failed: ${res.status}`);
  return deepCamelCase(unwrapApiEnvelope(await res.json()));
}

function unwrapApiEnvelope(payload) {
  if (window.FairvaluePlatformApi && typeof window.FairvaluePlatformApi.unwrapApiEnvelope === 'function') {
    return window.FairvaluePlatformApi.unwrapApiEnvelope(payload);
  }
  if (payload && typeof payload === 'object' && 'success' in payload) {
    return payload.success === false ? (payload.error || payload) : payload.data;
  }
  return payload;
}

/**
 * Recursively converts all snake_case object keys to camelCase.
 * Jackson SNAKE_CASE strategy produces e.g. company_name → companyName after this call.
 */
function deepCamelCase(obj) {
  if (Array.isArray(obj)) return obj.map(deepCamelCase);
  if (obj !== null && typeof obj === 'object') {
    return Object.fromEntries(
      Object.entries(obj).map(([k, v]) => [snakeToCamel(k), deepCamelCase(v)])
    );
  }
  return obj;
}

function snakeToCamel(s) {
  return s.replace(/_([a-z])/g, (_, c) => c.toUpperCase());
}

// ─── Row Mappers ──────────────────────────────────────────────────────────────

/** Map MarketRankingItem → normalised row for renderRows() */
function rowFromRankingItem(item) {
  const verdict = (item.verdict || '').toUpperCase();
  return {
    ticker:           item.ticker          || '',
    name:             item.companyName      || item.ticker || '',
    price:            item.currentPrice     ?? 0,
    upside:           item.upsidePct        ?? 0,
    fairValue:        item.fairValue        ?? 0,
    // Quality columns mapped from ranking fields
    financial:        { label: verdictLabel(verdict), cls: verdictCls(verdict) },
    financialScore:   verdictScore(verdict),
    cashflow:         { label: item.confidenceScore != null ? (item.confidenceScore * 100).toFixed(0) + '%' : '—', cls: '' },
    cashflowScore:    item.confidenceScore  ?? 0,
    growth:           { label: item.growthMetric != null ? (item.growthMetric * 100).toFixed(1) + '%' : '—', cls: '' },
    growthScore:      item.growthMetric     ?? 0,
    profitability:    { label: '—', cls: '' },
    profitabilityScore: 0,
    rating:           { label: '—', cls: '' },
    ratingScore:      0,
    pe:               item.peTtm            ?? 0,
    evEbitda:         item.evEbitda         ?? 0,
    marketCap:        item.marketCap        ?? 0,
    eps5y:            item.growthMetric     ?? 0,
    // Extra fields for ranking mode
    isRankingMode:    true,
    verdict:          verdict,
    confidenceScore:  item.confidenceScore  ?? null,
    dailyChange:      item.dailyChangePct   ?? 0,
    turnover:         item.activityMetric   ?? 0,
    high52:           null,
    low52:            null,
  };
}

/** Map CnDiscoveryItem → normalised row (existing mapping) */
function rowFromDiscovery(item) {
  const financialLabel      = pickValue(item, 'financial_health', 'financialHealth')         || '估值合理';
  const cashflowLabel       = pickValue(item, 'cashflow_rating', 'cashflowRating')           || '估值合理';
  const growthLabel         = pickValue(item, 'growth_rating', 'growthRating')               || '估值合理';
  const profitabilityLabel  = pickValue(item, 'profitability_rating', 'profitabilityRating') || '估值合理';
  const analystLabel        = pickValue(item, 'analyst_rating', 'analystRating')             || '中性';

  return {
    ticker:          pickValue(item, 'ticker') || '',
    name:            pickValue(item, 'name')   || '',
    price:           Number(pickValue(item, 'price')                        || 0),
    upside:          Number(pickValue(item, 'upside')                       || 0),
    fairValue:       Number(pickValue(item, 'fair_value', 'fairValue')      || 0),
    financial:       { label: financialLabel,     cls: gradeClass(financialLabel) },
    financialScore:  gradeScore(financialLabel),
    cashflow:        { label: cashflowLabel,      cls: gradeClass(cashflowLabel) },
    cashflowScore:   gradeScore(cashflowLabel),
    growth:          { label: growthLabel,        cls: gradeClass(growthLabel) },
    growthScore:     gradeScore(growthLabel),
    profitability:   { label: profitabilityLabel, cls: gradeClass(profitabilityLabel) },
    profitabilityScore: gradeScore(profitabilityLabel),
    rating:          { label: analystLabel,       cls: ratingClass(analystLabel) },
    ratingScore:     ratingScore(analystLabel),
    pe:              Number(pickValue(item, 'pe')                           || 0),
    evEbitda:        Number(pickValue(item, 'ev_ebitda', 'evEbitda')       || 0),
    marketCap:       Number(pickValue(item, 'market_cap', 'marketCap')     || 0),
    eps5y:           Number(pickValue(item, 'eps5y')                       || 0),
    isRankingMode:   false,
    dailyChange:     Number(pickValue(item, 'daily_change', 'dailyChange') || 0),
    turnover:        Number(pickValue(item, 'turnover')                    || 0),
    high52:          pickValue(item, 'high_52_week', 'high52'),
    low52:           pickValue(item, 'low_52_week', 'low52'),
  };
}

// ─── Load Orchestrator ────────────────────────────────────────────────────────

async function loadRows(append = false) {
  const market   = state.market;
  const mode     = state.mode;
  const pageSize = market === 'CN' ? 30 : 50;
  const nextPage = append ? state.page + 1 : 1;

  state.pageSize = pageSize;
  if (!append) {
    tableBody.innerHTML = `<tr><td class="loading" colspan="13">正在载入数据…</td></tr>`;
    setDisclaimer('');
  } else {
    setLoadMoreState(true, '加载中…');
  }

  const isRankingMode = mode === 'undervalued' || mode === 'overvalued';

  try {
    let payload;
    let mappedRows;
    state.usingFallback = false;

    if (isRankingMode) {
      try {
        payload = await fetchRankings(market, mode, nextPage, pageSize);
        mappedRows = (payload.items || []).map(rowFromRankingItem);
        // Show disclaimer for CN page-scoped rankings
        const disc = payload.disclaimer || '';
        setDisclaimer(disc || (market === 'CN' ? '⚠️ 当前中国市场榜单基于已加载数据子集（page-scoped），非全市场完整排名。' : ''));
      } catch (rankingErr) {
        console.warn('[fairvalue] rankings fallback to discovery:', rankingErr);
        payload = await fetchDiscovery(market, nextPage, pageSize);
        mappedRows = (payload.items || []).map(rowFromDiscovery);
        state.usingFallback = true;
        setDisclaimer(`⚠️ ${market} ${mode} 榜单接口暂时不可用，当前已自动回退为 discovery 结果。`);
      }
    } else {
      payload = await fetchDiscovery(market, nextPage, pageSize);
      mappedRows = (payload.items || []).map(rowFromDiscovery);
      setDisclaimer('');
    }

    state.total = Number(payload.total || 0);
    state.page = nextPage;
    state.rows = append ? mergeRows(state.rows, mappedRows) : mappedRows;
    footnote.textContent = buildFootnote(payload);
    applyModeSort();
    renderRows();
    renderPager();
  } catch (err) {
    if (!append) {
      state.rows = [];
      state.total = 0;
      state.page = 1;
      footnote.textContent = '数据来源：接口请求失败';
      tableBody.innerHTML = `<tr><td class="loading" colspan="13">接口请求失败，请稍后重试</td></tr>`;
    }
    renderPager();
    console.error('[fairvalue] loadRows error:', err);
  } finally {
    if (append) {
      setLoadMoreState(false);
    }
  }
}

// ─── Render ───────────────────────────────────────────────────────────────────

function renderRows() {
  if (!state.rows.length) {
    tableBody.innerHTML = `<tr><td class="loading" colspan="13">暂无数据</td></tr>`;
    return;
  }

  const sorted = sortRows(state.rows);
  const isUS   = state.market === 'US';
  const isCN   = state.market === 'CN';

  tableBody.innerHTML = sorted.map((row) => {
    // Name / ticker cell
    let nameCell;
    if (isUS && row.ticker) {
      nameCell = `<div class="name-cell">
        <a href="/us-stock-detail.html?ticker=${esc(row.ticker)}" class="ticker-link">${esc(row.ticker)}</a>
        <span class="company-name">${esc(row.name)}</span>
      </div>`;
    } else if (isCN && row.ticker) {
      nameCell = `<div class="name-cell">
        <a href="/cn-stock-detail.html?ticker=${esc(row.ticker)}" class="ticker-link">${esc(row.ticker)}</a>
        <span class="company-name">${esc(row.name)}</span>
      </div>`;
    } else {
      nameCell = `<div class="name-cell">
        <span class="lock">&#128274;</span>
        <span>${esc(row.name || row.ticker)}</span>
      </div>`;
    }

    const up = row.upside >= 0 ? 'up' : 'down';

    if (row.isRankingMode) {
      // Ranking item — show verdict badge, confidence, growth, pe, evEbitda, marketCap, growthMetric
      const verdictBadge = row.verdict
        ? `<span class="verdict-pill verdict-${row.verdict.toLowerCase()}">${verdictLabel(row.verdict)}</span>`
        : '—';
      const confText = row.confidenceScore != null
        ? (row.confidenceScore * 100).toFixed(0) + '%'
        : '—';

      return `<tr>
        <td>${nameCell}</td>
        <td>${fmtPrice(row.price, isUS)}</td>
        <td class="${up}">${formatPct(row.upside)}</td>
        <td>${fmtPrice(row.fairValue, isUS)}</td>
        <td colspan="1">${verdictBadge}</td>
        <td>${confText}</td>
        <td>${row.growth.label}</td>
        <td>—</td>
        <td>—</td>
        <td>${row.pe ? row.pe.toFixed(1) : '—'}</td>
        <td>${row.evEbitda ? row.evEbitda.toFixed(1) : '—'}</td>
        <td>${fmtMarketCap(row.marketCap, isUS)}</td>
        <td>${row.eps5y ? formatPct(row.eps5y) : '—'}</td>
      </tr>`;
    } else {
      // Discovery item — original format
      return `<tr>
        <td>${nameCell}</td>
        <td>${fmtPrice(row.price, isUS)}</td>
        <td class="${up}">${formatPct(row.upside)}</td>
        <td>${fmtPrice(row.fairValue, isUS)}</td>
        <td class="${row.financial.cls}">${esc(row.financial.label)}</td>
        <td class="${row.cashflow.cls}">${esc(row.cashflow.label)}</td>
        <td class="${row.growth.cls}">${esc(row.growth.label)}</td>
        <td class="${row.profitability.cls}">${esc(row.profitability.label)}</td>
        <td class="${row.rating.cls}">${esc(row.rating.label)}</td>
        <td>${row.pe ? row.pe.toFixed(2) : '—'}</td>
        <td>${row.evEbitda ? row.evEbitda.toFixed(2) : '—'}</td>
        <td>${fmtMarketCap(row.marketCap, isUS)}</td>
        <td>${row.eps5y ? row.eps5y.toFixed(2) : '—'}</td>
      </tr>`;
    }
  }).join('');
}

// ─── Disclaimer Banner ────────────────────────────────────────────────────────

function setDisclaimer(text) {
  if (!disclaimerEl) {
    disclaimerEl = document.createElement('div');
    disclaimerEl.className = 'disclaimer-banner';
    const tableWrap = document.querySelector('.table-wrap');
    if (tableWrap) tableWrap.before(disclaimerEl);
  }
  if (text) {
    disclaimerEl.textContent = text;
    disclaimerEl.style.display = 'block';
  } else {
    disclaimerEl.style.display = 'none';
  }
}

function ensurePager() {
  if (pagerEl) return;
  pagerEl = document.createElement('div');
  pagerEl.className = 'pager-wrap';
  pagerStatusEl = document.createElement('div');
  pagerStatusEl.className = 'pager-status';
  pagerButtonEl = document.createElement('button');
  pagerButtonEl.className = 'load-more-btn';
  pagerButtonEl.type = 'button';
  pagerButtonEl.textContent = '加载更多';
  pagerButtonEl.addEventListener('click', () => loadRows(true));
  pagerEl.appendChild(pagerStatusEl);
  pagerEl.appendChild(pagerButtonEl);
  if (tableWrap) {
    tableWrap.after(pagerEl);
  }
}

function renderPager() {
  ensurePager();
  const total = state.total || state.rows.length;
  const loaded = state.rows.length;
  if (pagerStatusEl) {
    pagerStatusEl.textContent = total > 0
      ? `已显示 ${loaded} / ${total} 条`
      : `已显示 ${loaded} 条`;
  }
  if (pagerButtonEl) {
    const hasMore = total > loaded;
    pagerButtonEl.style.display = hasMore ? 'inline-flex' : 'none';
    pagerButtonEl.disabled = false;
    if (!hasMore) {
      pagerButtonEl.textContent = '已全部加载';
    } else {
      pagerButtonEl.textContent = '加载更多';
    }
  }
  if (pagerEl) {
    pagerEl.style.display = loaded > 0 ? 'flex' : 'none';
  }
}

function setLoadMoreState(loading, label = '加载更多') {
  ensurePager();
  if (!pagerButtonEl) return;
  pagerButtonEl.disabled = loading;
  pagerButtonEl.textContent = loading ? label : '加载更多';
}

function resetRows() {
  state.rows = [];
  state.page = 1;
  state.total = 0;
  state.usingFallback = false;
  renderPager();
}

function mergeRows(existingRows, nextRows) {
  const seen = new Set(existingRows.map(row => `${row.ticker}::${row.name}`));
  const merged = [...existingRows];
  nextRows.forEach(row => {
    const key = `${row.ticker}::${row.name}`;
    if (!seen.has(key)) {
      seen.add(key);
      merged.push(row);
    }
  });
  return merged;
}

// ─── Sort ─────────────────────────────────────────────────────────────────────

function applyModeSort() {
  const map = {
    undervalued: { key: 'upside',      dir: 'desc' },
    overvalued:  { key: 'upside',      dir: 'asc'  },
    high52:      { key: 'high52',      dir: 'desc' },
    low52:       { key: 'low52',       dir: 'asc'  },
    active:      { key: 'turnover',    dir: 'desc' },
    gainers:     { key: 'dailyChange', dir: 'desc' },
    losers:      { key: 'dailyChange', dir: 'asc'  },
  };
  const target = map[state.mode] || map.undervalued;
  state.sortKey = target.key;
  state.sortDir = target.dir;
}

function sortRows(rows) {
  const direction = state.sortDir === 'asc' ? 1 : -1;
  const key = state.sortKey;
  return [...rows].sort((a, b) => {
    const av = a[key];
    const bv = b[key];
    if (typeof av === 'number' && typeof bv === 'number') return (av - bv) * direction;
    if (av == null && bv == null) return 0;
    if (av == null) return 1;
    if (bv == null) return -1;
    return String(av).localeCompare(String(bv), 'zh-CN') * direction;
  });
}

// ─── Formatting Helpers ───────────────────────────────────────────────────────

function formatPct(value) {
  if (value == null) return '—';
  const pct = (value * 100).toFixed(2);
  return `${value >= 0 ? '+' : ''}${pct}%`;
}

function fmtPrice(v, isUSD) {
  if (v == null || v === 0) return '—';
  if (isUSD) {
    return v >= 100 ? `$${v.toFixed(1)}` : `$${v.toFixed(2)}`;
  }
  return v.toFixed(2);
}

function fmtMarketCap(v, isUSD) {
  if (!v) return '—';
  if (isUSD) {
    if (v >= 1e12) return `$${(v / 1e12).toFixed(1)}T`;
    if (v >= 1e9)  return `$${(v / 1e9).toFixed(1)}B`;
    if (v >= 1e6)  return `$${(v / 1e6).toFixed(1)}M`;
    return `$${v.toFixed(0)}`;
  }
  return `${v.toFixed(2)}亿`;
}

function buildFootnote(payload) {
  const parts = ['数据来源：Fairvalue Engine'];
  if (payload.generatedAt) parts.push(`生成于 ${payload.generatedAt.slice(0, 10)}`);
  if (payload.dataAsOf)    parts.push(`数据截至 ${payload.dataAsOf}`);
  if (payload.rankingBasis) parts.push(payload.rankingBasis);
  return parts.join('　|　');
}

// ─── Grade / Rating Helpers (for discovery items) ────────────────────────────

function gradeClass(label) {
  if (label === '优秀') return 'grade-bad';
  if (label === '良好') return 'grade-mid';
  return 'grade-good';
}

function gradeScore(label) {
  if (label === '优秀') return 3;
  if (label === '良好') return 2;
  return 1;
}

function ratingClass(label) {
  if (label === '强力买入') return 'rating-strong-buy';
  if (label === '买入')     return 'rating-buy';
  if (label === '卖出')     return 'rating-sell';
  return 'rating-neutral';
}

function ratingScore(label) {
  if (label === '强力买入') return 4;
  if (label === '买入')     return 3;
  if (label === '中性')     return 2;
  if (label === '卖出')     return 1;
  return 0;
}

// ─── Verdict Helpers (for ranking items) ─────────────────────────────────────

function verdictLabel(v) {
  const map = { UNDERVALUED: '低估', FAIRLY_VALUED: '合理', FAIR: '合理', OVERVALUED: '高估' };
  return map[v] || v || '—';
}

function verdictCls(v) {
  if (v === 'UNDERVALUED')  return 'grade-good';
  if (v === 'OVERVALUED')   return 'grade-bad';
  return 'grade-mid';
}

function verdictScore(v) {
  if (v === 'UNDERVALUED')  return 3;
  if (v === 'OVERVALUED')   return 1;
  return 2;
}

// ─── Utility ─────────────────────────────────────────────────────────────────

function pickValue(item, ...keys) {
  for (const key of keys) {
    if (item && item[key] !== undefined && item[key] !== null) return item[key];
  }
  return undefined;
}

function esc(str) {
  return String(str || '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

// ─── Boot ─────────────────────────────────────────────────────────────────────

loadRows();
