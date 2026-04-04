'use strict';

/* ════════════════════════════════════════════════
   Constants
════════════════════════════════════════════════ */
const STRATEGIES = [
  { id: 'tech-bull',  flag: '🇺🇸', label: '技术面大牛股',           pct: '+60.6%', params: { minUndervalued: 20, minRoe: 18, maxPb: 15 } },
  { id: 'momentum',   flag: '🇺🇸', label: '动能大师',               pct: '+45.4%', params: { minUndervalued: 15, minRoe: 15 } },
  { id: 'sub10',      flag: '🇺🇸', label: '股价低于每股US$10的股票',  pct: '+38.1%', params: { minUndervalued: 5, minRoe: 8 } },
  { id: 'insiders',   flag: '🇺🇸', label: '内行之选',               pct: '+37.5%', params: { minUndervalued: 25, minRoe: 20 } },
  { id: 'buffett',    flag: '🇺🇸', label: '高贝塔大牛股',           pct: '+16.7%', params: { minUndervalued: 10, minRoe: 15, maxPb: 4 } },
  { id: 'roar',       flag: '🇺🇸', label: '牛气哄哄',              pct: '+13.8%', params: { minUndervalued: 8,  minRoe: 10 } },
  { id: 'near52h',    flag: '🇺🇸', label: '接近52周高点',           pct: '',       params: { minUndervalued: 5 } },
  { id: 'dividend',   flag: '🇺🇸', label: '高股息精选',            pct: '+11.2%', params: { minDividendYield: 3, requirePositiveFcf: true } },
  { id: 'cn-value',   flag: '🇨🇳', label: 'A股价值优选',           pct: '+22.4%', params: { minUndervalued: 15, minRoe: 12, market: 'CN' } },
];

const MARKETS = [
  { id: 'US', flag: '🇺🇸', label: 'US' },
  { id: 'CN', flag: '🇨🇳', label: 'CN' },
  { id: 'JP', flag: '🇯🇵', label: 'JP' },
  { id: 'HK', flag: '🇭🇰', label: 'HK' },
];

const VIEWS = [
  { id: 'overview',  label: '概览' },
  { id: 'insight',   label: '洞察' },
  { id: 'valuation', label: '估值' },
  { id: 'return',    label: '回报' },
  { id: 'tech',      label: '技术' },
  { id: 'finance',   label: '财务' },
  { id: 'growth',    label: '增长' },
  { id: 'risk',      label: '风险' },
  { id: 'custom',    label: '自定义', pro: true },
];

const FILTER_CATS = [
  { id: 'hot',        label: '热门',  fields: ['market', 'minUndervalued', 'minRoe', 'limit'] },
  { id: 'price',      label: '价格',  fields: [] },
  { id: 'valuation',  label: '估值',  fields: ['minUndervalued', 'maxPb'] },
  { id: 'insight',    label: '洞察',  fields: [] },
  { id: 'finance',    label: '财务',  fields: ['minRoe', 'requirePositiveFcf'] },
  { id: 'dividend',   label: '股息',  fields: ['minDividendYield'] },
  { id: 'growth',     label: '增长',  fields: [] },
  { id: 'return',     label: '回报',  fields: [] },
  { id: 'risk',       label: '风险',  fields: [] },
  { id: 'tech',       label: '技术',  fields: [] },
  { id: 'efficiency', label: '效率',  fields: [] },
  { id: 'profile',    label: '简介',  fields: ['limit'] },
];

const FIELD_DEFS = {
  market: {
    label: '市场', type: 'market',
  },
  minUndervalued: {
    label: '最低低估幅度', type: 'pct', unit: '%',
    placeholder: '10', min: 0, max: 100,
  },
  minRoe: {
    label: '最低 ROE', type: 'pct', unit: '%',
    placeholder: '12', min: 0, max: 100,
  },
  maxPb: {
    label: '最高 PB 比率', type: 'num',
    placeholder: '25', min: 0,
  },
  minDividendYield: {
    label: '最低股息率', type: 'pct', unit: '%',
    placeholder: '0', min: 0, max: 30,
  },
  requirePositiveFcf: {
    label: '仅显示正自由现金流', type: 'bool',
  },
  limit: {
    label: '结果上限', type: 'num',
    placeholder: '30', min: 1, max: 50,
  },
};

const DEFAULTS = {
  minUndervalued: 10,
  minRoe: 12,
  maxPb: 25,
  minDividendYield: 0,
  limit: 30,
  requirePositiveFcf: true,
};

/* Table columns per view */
const COLS = {
  overview: [
    { key: 'company',   label: '公司',         cls: '',        sort: 'symbol' },
    { key: 'name',      label: '名称',         cls: '',        sort: null },
    { key: 'exchange',  label: '交易所',       cls: '',        sort: null },
    { key: 'sector',    label: '板块',         cls: '',        sort: null },
    { key: 'industry',  label: '行业',         cls: '',        sort: null },
    { key: 'mktcap',    label: '市值',         cls: 'col-num', sort: null },
    { key: 'pe',        label: '市盈率',       cls: 'col-num', sort: null },
    { key: 'peg',       label: '市盈增长比率', cls: 'col-num', sort: null },
    { key: 'price',     label: '最近成交价',   cls: 'col-num', sort: 'price' },
    { key: 'chg',       label: '日涨跌幅(%)',  cls: 'col-num', sort: null },
    { key: 'fairValue', label: '公允价值',     cls: 'col-num', sort: 'tradable_fair_value' },
    { key: 'upside',    label: '公允价值上行边际', cls: 'col-num', sort: 'upside' },
    { key: 'status',    label: '公允价值评级', cls: '',        sort: 'valuation_status' },
  ],
  valuation: [
    { key: 'company',    label: '公司',     cls: '',        sort: 'symbol' },
    { key: 'price',      label: '当前价格', cls: 'col-num', sort: 'price' },
    { key: 'fairValue',  label: '公允价值', cls: 'col-num', sort: 'tradable_fair_value' },
    { key: 'upside',     label: '上行空间', cls: 'col-num', sort: 'upside' },
    { key: 'confidence', label: '置信度',   cls: 'col-num', sort: 'confidence' },
    { key: 'status',     label: '估值评级', cls: '',        sort: 'valuation_status' },
  ],
  finance: [
    { key: 'company',   label: '公司',     cls: '',        sort: 'symbol' },
    { key: 'price',     label: '当前价格', cls: 'col-num', sort: 'price' },
    { key: 'fairValue', label: '公允价值', cls: 'col-num', sort: 'tradable_fair_value' },
    { key: 'upside',    label: '上行空间', cls: 'col-num', sort: 'upside' },
    { key: 'status',    label: '评级',     cls: '',        sort: 'valuation_status' },
    { key: 'version',   label: '数据版本', cls: '',        sort: null },
  ],
};

// Reuse overview for unspecified views
['insight','return','tech','growth','risk','custom'].forEach(v => { COLS[v] = COLS.overview; });

/* Avatar colors */
const AVATAR_COLORS = [
  '#4f46e5','#0891b2','#059669','#d97706','#dc2626',
  '#7c3aed','#db2777','#2563eb','#ea580c','#0d9488',
];

/* ════════════════════════════════════════════════
   State
════════════════════════════════════════════════ */
const state = {
  markets: new Set(['US']),
  filters: { ...DEFAULTS },
  activeCat: null,
  activeView: 'overview',
  activeStrategy: null,
  sortKey: null,
  sortDir: 'desc',
  candidates: [],
};

/* ════════════════════════════════════════════════
   DOM refs
════════════════════════════════════════════════ */
const $ = (id) => document.getElementById(id);

const strategiesScroll  = $('strategiesScroll');
const appliedPill       = $('appliedPill');
const appliedCount      = $('appliedCount');
const resetAllBtn       = $('resetAllBtn');
const filterCatsEl      = $('filterCats');
const filterPanelWrap   = $('filterPanelWrap');
const filterPanel       = $('filterPanel');
const activeChipsRow    = $('activeChipsRow');
const activeChips       = $('activeChips');
const marketTabsEl      = $('marketTabs');
const viewTabsEl        = $('viewTabs');
const tableContainer    = $('tableContainer');
const resultsFooter     = $('resultsFooter');
const footerCount       = $('footerCount');
const footerTime        = $('footerTime');

/* ════════════════════════════════════════════════
   Bootstrap
════════════════════════════════════════════════ */
document.addEventListener('DOMContentLoaded', () => {
  buildStrategies();
  buildFilterCats();
  buildMarketTabs();
  buildViewTabs();
  updateAppliedBadge();

  $('applyFilterBtn').addEventListener('click', commitFilterPanel);
  $('cancelFilterBtn').addEventListener('click', closeFilterPanel);
  $('resetAllBtn').addEventListener('click', resetAll);
  $('applyBtn').addEventListener('click', runScreener);
  $('saveBtn').addEventListener('click', () => alert('保存功能即将推出'));
  $('dlBtn').addEventListener('click', exportCSV);

  runScreener();
});

/* ════════════════════════════════════════════════
   Strategies
════════════════════════════════════════════════ */
function buildStrategies() {
  strategiesScroll.innerHTML = STRATEGIES.map(s => `
    <button class="strategy-chip${state.activeStrategy === s.id ? ' is-active' : ''}"
            data-sid="${esc(s.id)}">
      <span class="chip-flag">${s.flag}</span>
      <span>${esc(s.label)}</span>
      ${s.pct ? `<span class="chip-pct">${esc(s.pct)}</span>` : ''}
    </button>
  `).join('');

  strategiesScroll.addEventListener('click', onStrategyClick);
}

function onStrategyClick(e) {
  const chip = e.target.closest('.strategy-chip');
  if (!chip) return;
  const id = chip.dataset.sid;
  if (state.activeStrategy === id) {
    state.activeStrategy = null;
  } else {
    state.activeStrategy = id;
    applyStrategy(id);
  }
  // re-render chip active states
  strategiesScroll.querySelectorAll('.strategy-chip').forEach(c => {
    c.classList.toggle('is-active', c.dataset.sid === state.activeStrategy);
  });
}

function applyStrategy(id) {
  const s = STRATEGIES.find(x => x.id === id);
  if (!s) return;
  // reset filters first, then apply strategy params
  state.filters = { ...DEFAULTS };
  if (s.params.market) {
    state.markets = new Set([s.params.market]);
  }
  Object.entries(s.params).forEach(([k, v]) => {
    if (k !== 'market' && k in state.filters) state.filters[k] = v;
  });
  updateAppliedBadge();
  renderActiveChips();
  buildMarketTabs();
  buildFilterCats();
  runScreener();
}

/* ════════════════════════════════════════════════
   Filter categories
════════════════════════════════════════════════ */
function buildFilterCats() {
  filterCatsEl.innerHTML = FILTER_CATS.map(cat => {
    const cnt = countCatActive(cat);
    return `<button class="cat-tab${state.activeCat === cat.id ? ' is-active' : ''}"
                    data-cid="${esc(cat.id)}">
      ${esc(cat.label)}
      ${cnt > 0 ? `<span class="cat-badge">${cnt}</span>` : ''}
    </button>`;
  }).join('');

  filterCatsEl.addEventListener('click', onCatClick);
}

function onCatClick(e) {
  const tab = e.target.closest('.cat-tab');
  if (!tab) return;
  const cid = tab.dataset.cid;
  if (state.activeCat === cid) {
    closeFilterPanel();
    return;
  }
  state.activeCat = cid;
  filterCatsEl.querySelectorAll('.cat-tab').forEach(t => {
    t.classList.toggle('is-active', t.dataset.cid === cid);
  });
  openFilterPanel(cid);
}

function countCatActive(cat) {
  return cat.fields.filter(f => {
    if (f === 'market') return false;
    return f in DEFAULTS && state.filters[f] !== DEFAULTS[f];
  }).length;
}

function openFilterPanel(catId) {
  const cat = FILTER_CATS.find(c => c.id === catId);
  if (!cat) return;

  filterPanelWrap.style.display = 'block';

  if (!cat.fields.length) {
    filterPanel.innerHTML = `<p style="color:var(--muted);font-size:13px;margin:0">该分类暂无可用筛选条件。</p>`;
    return;
  }

  filterPanel.innerHTML = cat.fields.map(fid => {
    const def = FIELD_DEFS[fid];
    if (!def) return '';

    if (def.type === 'market') {
      return `<div class="filter-field">
        <div class="filter-field-label">市场</div>
        <div class="filter-market-grid" id="fpMktGrid">
          ${MARKETS.map(m =>
            `<button class="filter-mkt-pill${state.markets.has(m.id) ? ' is-active' : ''}"
                     data-mid="${esc(m.id)}">${m.flag} ${m.label}</button>`
          ).join('')}
        </div>
      </div>`;
    }

    if (def.type === 'bool') {
      return `<div class="filter-field">
        <label class="filter-bool-label">
          <input type="checkbox" data-fkey="${esc(fid)}" ${state.filters[fid] ? 'checked' : ''} />
          <span>${esc(def.label)}</span>
        </label>
      </div>`;
    }

    // num / pct
    return `<div class="filter-field">
      <div class="filter-field-label">${esc(def.label)}</div>
      <div class="filter-num-input">
        <input type="number"
               data-fkey="${esc(fid)}"
               value="${state.filters[fid] ?? ''}"
               min="${def.min ?? 0}"
               ${def.max != null ? `max="${def.max}"` : ''}
               step="1"
               placeholder="${def.placeholder || ''}" />
        ${def.unit ? `<span class="unit">${esc(def.unit)}</span>` : ''}
      </div>
    </div>`;
  }).join('');

  // Market pill toggle (immediate — no need to wait for Apply)
  const grid = filterPanel.querySelector('#fpMktGrid');
  if (grid) {
    grid.addEventListener('click', e => {
      const pill = e.target.closest('.filter-mkt-pill');
      if (!pill) return;
      const mid = pill.dataset.mid;
      if (state.markets.has(mid) && state.markets.size > 1) {
        state.markets.delete(mid);
        pill.classList.remove('is-active');
      } else if (!state.markets.has(mid)) {
        state.markets.add(mid);
        pill.classList.add('is-active');
      }
    });
  }
}

function closeFilterPanel() {
  state.activeCat = null;
  filterPanelWrap.style.display = 'none';
  filterCatsEl.querySelectorAll('.cat-tab').forEach(t => t.classList.remove('is-active'));
}

function commitFilterPanel() {
  // Collect numeric / bool inputs from panel
  filterPanel.querySelectorAll('input[data-fkey]').forEach(input => {
    const key = input.dataset.fkey;
    if (input.type === 'checkbox') {
      state.filters[key] = input.checked;
    } else {
      const v = parseFloat(input.value);
      if (!isNaN(v)) state.filters[key] = v;
    }
  });
  closeFilterPanel();
  updateAppliedBadge();
  buildFilterCats();
  renderActiveChips();
  runScreener();
}

/* ════════════════════════════════════════════════
   Active filter chips
════════════════════════════════════════════════ */
const CHIP_LABELS = {
  minUndervalued:   v => `低估幅度 ≥ ${v}%`,
  minRoe:           v => `ROE ≥ ${v}%`,
  maxPb:            v => `PB ≤ ${v}`,
  minDividendYield: v => `股息率 ≥ ${v}%`,
  requirePositiveFcf: v => v ? '正自由现金流' : null,
  limit:            v => `最多 ${v} 条`,
};

function renderActiveChips() {
  const chips = [];
  Object.entries(state.filters).forEach(([k, v]) => {
    if (v === DEFAULTS[k]) return;
    const fn = CHIP_LABELS[k];
    if (!fn) return;
    const label = fn(v);
    if (!label) return;
    chips.push({ key: k, label });
  });

  if (!chips.length) {
    activeChipsRow.style.display = 'none';
    return;
  }

  activeChipsRow.style.display = 'block';
  activeChips.innerHTML = chips.map(c => `
    <span class="active-chip">
      ${esc(c.label)}
      <button class="chip-remove" data-rkey="${esc(c.key)}" title="移除此筛选">×</button>
    </span>
  `).join('');

  activeChips.querySelectorAll('.chip-remove').forEach(btn => {
    btn.addEventListener('click', () => {
      state.filters[btn.dataset.rkey] = DEFAULTS[btn.dataset.rkey];
      updateAppliedBadge();
      buildFilterCats();
      renderActiveChips();
      runScreener();
    });
  });
}

function updateAppliedBadge() {
  const cnt = Object.entries(state.filters).filter(([k, v]) => v !== DEFAULTS[k]).length;
  if (cnt > 0) {
    appliedCount.textContent = cnt;
    appliedPill.style.display = 'inline-flex';
  } else {
    appliedPill.style.display = 'none';
  }
}

/* ════════════════════════════════════════════════
   Market tabs (toolbar)
════════════════════════════════════════════════ */
function buildMarketTabs() {
  marketTabsEl.innerHTML = MARKETS.map(m => `
    <button class="mkt-tab${state.markets.has(m.id) ? ' is-active' : ''}" data-mid="${esc(m.id)}">
      <span class="mkt-flag">${m.flag}</span>${m.label}
    </button>
  `).join('');

  marketTabsEl.querySelectorAll('.mkt-tab').forEach(btn => {
    btn.addEventListener('click', () => {
      const mid = btn.dataset.mid;
      if (state.markets.has(mid) && state.markets.size > 1) {
        state.markets.delete(mid);
      } else if (!state.markets.has(mid)) {
        state.markets.add(mid);
      }
      buildMarketTabs();
      runScreener();
    });
  });
}

/* ════════════════════════════════════════════════
   View tabs
════════════════════════════════════════════════ */
function buildViewTabs() {
  viewTabsEl.innerHTML = VIEWS.map(v => `
    <button class="view-tab${state.activeView === v.id ? ' is-active' : ''}" data-vid="${esc(v.id)}">
      ${v.pro ? `<span class="view-pro-icon">🛡</span>` : ''}${esc(v.label)}
    </button>
  `).join('');

  viewTabsEl.querySelectorAll('.view-tab').forEach(btn => {
    btn.addEventListener('click', () => {
      state.activeView = btn.dataset.vid;
      viewTabsEl.querySelectorAll('.view-tab').forEach(t =>
        t.classList.toggle('is-active', t.dataset.vid === state.activeView)
      );
      if (state.candidates.length) renderTable(state.candidates);
    });
  });
}

/* ════════════════════════════════════════════════
   API + screener
════════════════════════════════════════════════ */
async function runScreener() {
  showLoading();
  const payload = buildPayload();
  try {
    const res = await fetch('/v1/screener/valuation', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    });
    const raw = await res.json();
    if (!res.ok) throw new Error(raw.message || `HTTP ${res.status}`);
    // handle API envelope wrapper ({ success, data }) if present
    const data = (raw && typeof raw === 'object' && 'success' in raw) ? raw.data : raw;
    state.candidates = data.candidates || [];
    renderTable(state.candidates);
    showFooter(data.count ?? state.candidates.length);
  } catch (err) {
    showError(err.message);
  }
}

function buildPayload() {
  const f = state.filters;
  return {
    markets:              [...state.markets],
    min_undervalued:      f.minUndervalued / 100,
    min_roe:              f.minRoe / 100,
    max_pb:               f.maxPb,
    min_dividend_yield:   f.minDividendYield / 100,
    require_positive_fcf: f.requirePositiveFcf,
    limit:                Math.round(f.limit),
  };
}

/* ════════════════════════════════════════════════
   Table
════════════════════════════════════════════════ */
function showLoading() {
  tableContainer.innerHTML = `<div class="placeholder-state"><div class="loading-ring"></div><span>正在筛选...</span></div>`;
  resultsFooter.style.display = 'none';
}

function showError(msg) {
  tableContainer.innerHTML = `<div class="empty-state">⚠️ 筛选请求失败：${esc(msg)}<br><small style="color:var(--muted)">请检查后端服务是否运行</small></div>`;
  resultsFooter.style.display = 'none';
}

function showFooter(count) {
  footerCount.textContent = `共 ${count} 条结果`;
  footerTime.textContent  = `更新于 ${new Date().toLocaleTimeString('zh-CN', { hour12: false })}`;
  resultsFooter.style.display = 'flex';
}

function renderTable(candidates) {
  if (!candidates.length) {
    tableContainer.innerHTML = `<div class="empty-state">当前筛选条件下暂无匹配股票，请调整参数。</div>`;
    return;
  }

  const cols   = COLS[state.activeView] || COLS.overview;
  const sorted = sortCandidates([...candidates]);

  // Header
  const thChk  = `<th class="col-chk"><input type="checkbox" id="chkAll" /></th>`;
  const thRank = `<th class="col-rank">#</th>`;
  const thCols = cols.map(c => {
    const hasSk  = !!c.sort;
    const isSort = state.sortKey === c.sort && hasSk;
    const dir    = state.sortDir;
    return `<th class="${c.cls}${hasSk ? ' sortable' : ''}${isSort ? ` sort-${dir}` : ''}"
                data-sk="${c.sort || ''}">
      ${esc(c.label)}
      ${hasSk ? `<span class="s-icon">${isSort ? (dir === 'asc' ? '▲' : '▼') : '⇅'}</span>` : ''}
    </th>`;
  });

  // Rows
  const rows = sorted.map((item, i) => {
    const tds = cols.map(c => renderCell(c.key, item)).join('');
    return `<tr>
      <td class="col-chk"><input type="checkbox" /></td>
      <td class="col-rank">${i + 1}</td>
      ${tds}
    </tr>`;
  }).join('');

  tableContainer.innerHTML = `
    <table class="screen-table">
      <thead><tr>${thChk}${thRank}${thCols.join('')}</tr></thead>
      <tbody>${rows}</tbody>
    </table>`;

  // Sort handlers
  tableContainer.querySelectorAll('th[data-sk]').forEach(th => {
    if (!th.dataset.sk) return;
    th.addEventListener('click', () => {
      const sk = th.dataset.sk;
      if (state.sortKey === sk) {
        state.sortDir = state.sortDir === 'asc' ? 'desc' : 'asc';
      } else {
        state.sortKey = sk;
        state.sortDir = 'desc';
      }
      renderTable(state.candidates);
    });
  });

  // Select-all
  const chkAll = document.getElementById('chkAll');
  if (chkAll) {
    chkAll.addEventListener('change', () => {
      tableContainer.querySelectorAll('tbody input[type=checkbox]').forEach(c => {
        c.checked = chkAll.checked;
      });
    });
  }
}

function renderCell(key, item) {
  const td    = (content, cls = '')  => `<td class="${cls}">${content}</td>`;
  const numTd = (content)            => td(content, 'col-num');
  const dash  = ()                   => numTd(`<span class="dash">—</span>`);
  const pro   = ()                   => numTd(`<span class="pro-lock">升级至Pro+</span>`);

  switch (key) {

    case 'company': {
      const sym = item.symbol || '?';
      const mkt = item.market || 'US';
      const col = avatarColor(sym);
      const abbr = sym.replace(/\..+$/, '').substring(0, 4);
      const href = mkt === 'US'
        ? `/us-stock-detail.html?ticker=${encodeURIComponent(sym)}`
        : mkt === 'CN'
        ? `/cn-stock-detail.html?ticker=${encodeURIComponent(sym)}`
        : mkt === 'JP'
        ? `/jp-stock-detail.html?code=${encodeURIComponent(sym)}`
        : '#';
      return td(`<div class="co-cell">
        <div class="ticker-avatar" style="background:${col}">${esc(abbr)}</div>
        <div class="co-info">
          <a class="ticker-link" href="${esc(href)}">${esc(sym)}</a>
          <span class="co-name-text">${esc(mkt)}</span>
        </div>
      </div>`);
    }

    case 'name':     return td(`<span class="dash">—</span>`);
    case 'exchange': return td(`<span class="dash">—</span>`);
    case 'sector':   return td(`<span class="dash">—</span>`);
    case 'industry': return td(`<span class="dash">—</span>`);
    case 'mktcap':   return pro();
    case 'pe':       return pro();
    case 'peg':      return pro();
    case 'chg':      return dash();

    case 'price': {
      if (item.price == null) return dash();
      return numTd(fmtMoney(item.price, item.market));
    }

    case 'fairValue': {
      if (item.tradable_fair_value == null) return dash();
      return numTd(fmtMoney(item.tradable_fair_value, item.market));
    }

    case 'upside': {
      if (item.upside == null) return dash();
      const pct = (item.upside * 100).toFixed(1);
      const cls = item.upside > 0.005 ? 'n-up' : item.upside < -0.005 ? 'n-down' : 'n-flat';
      return numTd(`<span class="${cls}">${item.upside > 0 ? '+' : ''}${pct}%</span>`);
    }

    case 'confidence': {
      if (item.confidence == null) return dash();
      return numTd(`${(item.confidence * 100).toFixed(0)}%`);
    }

    case 'status': {
      return td(statusPill(item.valuation_status));
    }

    case 'version': {
      return td(`<code style="font-size:11px;color:var(--muted)">${esc(item.data_version || '—')}</code>`);
    }

    default: return dash();
  }
}

function sortCandidates(arr) {
  if (!state.sortKey) return arr;
  const dir = state.sortDir === 'asc' ? 1 : -1;
  return arr.sort((a, b) => {
    let va = a[state.sortKey];
    let vb = b[state.sortKey];
    if (va == null) return 1;
    if (vb == null) return -1;
    if (typeof va === 'string') va = va.toLowerCase();
    if (typeof vb === 'string') vb = vb.toLowerCase();
    return va < vb ? -dir : va > vb ? dir : 0;
  });
}

/* ════════════════════════════════════════════════
   Reset all
════════════════════════════════════════════════ */
function resetAll() {
  state.filters        = { ...DEFAULTS };
  state.markets        = new Set(['US']);
  state.activeStrategy = null;
  state.activeCat      = null;
  filterPanelWrap.style.display = 'none';
  activeChipsRow.style.display  = 'none';

  strategiesScroll.querySelectorAll('.strategy-chip').forEach(c => c.classList.remove('is-active'));
  updateAppliedBadge();
  buildFilterCats();
  buildMarketTabs();
  runScreener();
}

/* ════════════════════════════════════════════════
   CSV export
════════════════════════════════════════════════ */
function exportCSV() {
  if (!state.candidates.length) return;
  const header = ['市场','股票代码','价格','公允价值','上行空间','估值状态','置信度','数据版本'];
  const rows = state.candidates.map(c => [
    c.market, c.symbol,
    c.price != null ? c.price.toFixed(2) : '',
    c.tradable_fair_value != null ? c.tradable_fair_value.toFixed(2) : '',
    c.upside != null ? (c.upside * 100).toFixed(1) + '%' : '',
    c.valuation_status || '',
    c.confidence != null ? (c.confidence * 100).toFixed(0) + '%' : '',
    c.data_version || '',
  ].map(v => `"${String(v).replace(/"/g, '""')}"`));

  const csv = [header.join(','), ...rows.map(r => r.join(','))].join('\n');
  const blob = new Blob(['\uFEFF' + csv], { type: 'text/csv;charset=utf-8' });
  const a = document.createElement('a');
  a.href = URL.createObjectURL(blob);
  a.download = `fairvalue-screener-${new Date().toISOString().slice(0,10)}.csv`;
  a.click();
}

/* ════════════════════════════════════════════════
   Helpers
════════════════════════════════════════════════ */
function statusPill(status) {
  const s = String(status || '').toUpperCase();
  let cls = 's-fair', label = '合理';
  if (s.includes('UNDER')) {
    cls = 's-underval';
    label = s.includes('DEEP') ? '深度低估' : '低估';
  } else if (s.includes('OVER')) {
    cls = 's-overval';
    label = s.includes('DEEP') ? '深度高估' : '高估';
  }
  return `<span class="s-pill ${cls}">${label}</span>`;
}

function fmtMoney(v, market) {
  if (v == null) return '—';
  const sym = (market === 'JP' || market === 'CN' || market === 'HK') ? '¥' : '$';
  const fmt = market === 'JP'
    ? Math.round(v).toLocaleString()
    : v >= 1000 ? v.toFixed(0) : v >= 100 ? v.toFixed(1) : v.toFixed(2);
  return `${sym}${fmt}`;
}

function avatarColor(ticker) {
  let h = 0;
  for (const c of ticker) h = (h * 31 + c.charCodeAt(0)) & 0xffff;
  return AVATAR_COLORS[h % AVATAR_COLORS.length];
}

function esc(s) {
  return String(s ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}
