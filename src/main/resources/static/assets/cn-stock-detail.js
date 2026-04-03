'use strict';
/**
 * cn-stock-detail.js — A股/CN 个股估值详情页
 *
 * API endpoints:
 *   GET /v1/cn-equities/{ticker}/valuation/summary
 *   GET /v1/cn-equities/{ticker}/valuation/report
 *   GET /v1/valuation/history/CN/{ticker}?days=90
 *   GET /v1/peers/CN/{ticker}?limit=8
 *
 * CN DTO differences vs US:
 *   - summary.confidenceScore is int 0-100 (not float 0-1)
 *   - summary has fairValueMid/Low/High (not fairValueRange object)
 *   - report.valuationMethods[]: {methodName, methodValue, weight, keyAssumption}
 *   - report.scenarioMatrix[]:   {scenario, probability, fairValueLow, fairValueMid, fairValueHigh}
 *   - report.riskMatrix[]:       {factor, level, adjustment, note}
 *   - report.operationZones:     {zones: [{label, low, high, ...}]}
 *   - report.sections:           Map<String,String> — key/value analysis text
 *   - NO profile endpoint, NO buyZone/holdZone/avoidZone on summary
 */

let TICKER = '';
let _historyLoaded = false;
let _peersLoaded   = false;

// ─── Boot ────────────────────────────────────────────────────────────────────

document.addEventListener('DOMContentLoaded', () => {
  const params = new URLSearchParams(window.location.search);
  TICKER = (params.get('ticker') || '').trim();

  if (!TICKER) {
    showFatalError('缺少 ticker 参数。请从榜单页选择股票。');
    return;
  }

  document.title = `${TICKER} 估值 — A股`;
  loadAll();
});

// ─── Main Loader ──────────────────────────────────────────────────────────────

async function loadAll() {
  try {
    const [summary, report] = await Promise.all([
      apiFetch(`/v1/cn-equities/${encodeURIComponent(TICKER)}/valuation/summary`),
      apiFetch(`/v1/cn-equities/${encodeURIComponent(TICKER)}/valuation/report`),
    ]);

    renderHeader(summary);
    renderHero(summary, report);
    renderOverview(summary, report);
    renderValuation(report);
    renderRiskTable(report.riskMatrix || []);

    show('main');
    hide('loading');
  } catch (err) {
    showFatalError('数据加载失败：' + (err.message || '未知错误'));
  }
}

// ─── Header ──────────────────────────────────────────────────────────────────

function renderHeader(summary) {
  setText('hdr-ticker', TICKER);
  const market = summary?.market || 'CN';
  setText('hdr-exchange', market);
  setText('hdr-market', marketLabel(market));
}

// ─── Hero Band ───────────────────────────────────────────────────────────────

function renderHero(summary, report) {
  if (!summary) return;

  // Price
  setText('hero-price', summary.currentPrice != null ? fmtCNY(summary.currentPrice) : '—');

  // Fair value range (fairValueLow / fairValueMid / fairValueHigh)
  setText('hero-fv-low',  summary.fairValueLow  != null ? fmtCNY(summary.fairValueLow)  : '—');
  setText('hero-fv-mid',  summary.fairValueMid  != null ? fmtCNY(summary.fairValueMid)  : '—');
  setText('hero-fv-high', summary.fairValueHigh != null ? fmtCNY(summary.fairValueHigh) : '—');

  // Upside (decimal, e.g. 0.087 = 8.7%)
  const upsideEl = document.getElementById('hero-upside');
  if (upsideEl && summary.upside != null) {
    const pct = (summary.upside * 100).toFixed(1);
    upsideEl.textContent = (summary.upside >= 0 ? '▲ ' : '▼ ') + Math.abs(pct) + '%';
    upsideEl.className = 'upside-value ' + (summary.upside >= 0 ? 'up' : 'down');
  }

  // Verdict
  const verdictEl = document.getElementById('hero-verdict');
  if (verdictEl) {
    const v = summary.verdict || '';
    verdictEl.textContent = verdictLabel(v);
    verdictEl.className = 'verdict-badge ' + verdictClass(v);
  }

  // Confidence — CN uses int 0-100 directly
  const conf = summary.confidenceScore;
  if (conf != null) {
    const pct = Math.min(100, Math.max(0, conf));
    const fill = document.getElementById('hero-conf-fill');
    if (fill) fill.style.width = pct + '%';
    setText('hero-conf-pct', pct + '%');
  }

  // Operation zones from report (buyZone / holdZone / avoidZone equivalent)
  const zonesWrap = document.getElementById('hero-zones-wrap');
  if (zonesWrap && report?.operationZones?.zones?.length) {
    zonesWrap.innerHTML = report.operationZones.zones.map(z => {
      const label = z.label || z.name || '';
      const low   = z.low  != null ? fmtCNY(z.low)  : null;
      const high  = z.high != null ? fmtCNY(z.high) : null;
      const range = low && high ? `${low} – ${high}` : (low || high || '—');
      const cls   = zoneClass(label);
      return `<div class="zone-item">
        <div class="zone-label">${escHtml(label)}</div>
        <div class="zone-value ${cls}">${range}</div>
      </div>`;
    }).join('');
  }
}

// ─── Tab: Overview ────────────────────────────────────────────────────────────

function renderOverview(summary, report) {
  // Ticker info card
  const infoEl = document.getElementById('ticker-info-rows');
  if (infoEl) {
    const rows = [
      ['股票代码',  TICKER],
      ['市场',      marketLabel(summary?.market || 'CN')],
      ['当前价格',  summary?.currentPrice != null ? fmtCNY(summary.currentPrice) : '—'],
      ['公允价值',  summary?.fairValueMid != null ? fmtCNY(summary.fairValueMid) : '—'],
    ];
    infoEl.innerHTML = rows.map(([k, v]) =>
      `<div class="profile-row"><span class="profile-label">${k}</span><span class="profile-value">${escHtml(String(v))}</span></div>`
    ).join('');
  }

  // Verdict summary card
  const verdictEl = document.getElementById('verdict-summary-rows');
  if (verdictEl && summary) {
    const conf = summary.confidenceScore != null ? summary.confidenceScore + '%' : '—';
    const upside = summary.upside != null ? (summary.upside * 100).toFixed(1) + '%' : '—';
    const rows = [
      ['估值判断',  verdictLabel(summary.verdict || '')],
      ['上行空间',  upside],
      ['置信度',    conf],
    ];
    verdictEl.innerHTML = rows.map(([k, v]) =>
      `<div class="profile-row"><span class="profile-label">${k}</span><span class="profile-value">${escHtml(v)}</span></div>`
    ).join('');
  }

  // One-liner from report
  if (report?.oneLineVerdict) {
    setText('overview-one-liner', report.oneLineVerdict);
  }
}

// ─── Tab: Valuation ───────────────────────────────────────────────────────────

function renderValuation(report) {
  if (!report) return;
  renderMethodTable(report.valuationMethods || []);
  renderScenarioMatrix(report.scenarioMatrix || []);
  renderOperationZones(report.operationZones);
  renderSections(report.sections || {});
}

function renderMethodTable(methods) {
  const el = document.getElementById('method-table-wrap');
  if (!el) return;
  if (!methods.length) {
    el.innerHTML = '<div class="empty-state">暂无估值方法数据</div>';
    return;
  }
  const rows = methods.map(m => {
    const weight = m.weight != null ? (m.weight * 100).toFixed(0) + '%' : '—';
    return `<tr>
      <td><strong>${escHtml(m.methodName || '—')}</strong></td>
      <td>${weight}</td>
      <td>${m.methodValue != null ? fmtCNY(m.methodValue) : '—'}</td>
      <td style="font-size:12px;color:var(--muted)">${escHtml(m.keyAssumption || '—')}</td>
    </tr>`;
  }).join('');

  el.innerHTML = `<table class="data-table">
    <thead><tr><th>方法</th><th>权重</th><th>估值</th><th>关键假设</th></tr></thead>
    <tbody>${rows}</tbody>
  </table>`;
}

function renderScenarioMatrix(scenarios) {
  const el = document.getElementById('scenario-grid');
  if (!el) return;
  if (!scenarios.length) {
    el.innerHTML = '<div class="empty-state">暂无情景数据</div>';
    return;
  }

  const order = { bear: 0, base: 1, 基准: 1, bull: 2 };
  const sorted = [...scenarios].sort((a, b) => {
    const ka = (a.scenario || '').toLowerCase();
    const kb = (b.scenario || '').toLowerCase();
    return (order[ka] ?? 1) - (order[kb] ?? 1);
  });

  el.innerHTML = sorted.map(s => {
    const key   = scenarioKey(s.scenario || '');
    const label = scenarioLabel(s.scenario || '');
    const prob  = s.probability != null ? (s.probability * 100).toFixed(0) + '%' : '—';
    const mid   = s.fairValueMid  != null ? fmtCNY(s.fairValueMid)  : '—';
    const low   = s.fairValueLow  != null ? fmtCNY(s.fairValueLow)  : null;
    const high  = s.fairValueHigh != null ? fmtCNY(s.fairValueHigh) : null;
    const range = (low && high) ? `<div class="scenario-assumption">区间 ${low} – ${high}</div>` : '';

    return `<div class="scenario-card ${key}">
      <div class="scenario-label">${label}</div>
      <div class="scenario-prob">概率 ${prob}</div>
      <div class="scenario-price">${mid}</div>
      ${range}
    </div>`;
  }).join('');
}

function renderOperationZones(opZones) {
  const el    = document.getElementById('operation-zones-wrap');
  const title = document.getElementById('zones-title');
  if (!el) return;

  const zones = opZones?.zones || [];
  if (!zones.length) return;

  show(title);
  el.innerHTML = `<div style="display:flex;gap:16px;flex-wrap:wrap;margin-bottom:20px">` +
    zones.map(z => {
      const label = z.label || z.name || '区间';
      const low   = z.low  != null ? fmtCNY(z.low)  : null;
      const high  = z.high != null ? fmtCNY(z.high) : null;
      const range = low && high ? `${low} – ${high}` : (low || high || '—');
      const cls   = zoneClass(label);
      return `<div class="card" style="min-width:160px;flex:1">
        <div style="font-size:11px;text-transform:uppercase;letter-spacing:.6px;color:var(--muted);margin-bottom:8px">${escHtml(label)}</div>
        <div class="zone-value ${cls}" style="font-size:18px;font-weight:700">${range}</div>
      </div>`;
    }).join('') +
  `</div>`;
}

function renderSections(sections) {
  const el = document.getElementById('sections-wrap');
  if (!el) return;

  const entries = Object.entries(sections || {}).filter(([, v]) => v);
  if (!entries.length) {
    el.innerHTML = '<div class="empty-state">暂无分析详情</div>';
    return;
  }

  el.innerHTML = entries.map(([key, content], idx) => {
    const id    = `sec-${idx}`;
    const title = sectionLabel(key);
    return `<div class="accordion-item">
      <button class="accordion-header" onclick="toggleAccordion('${id}')">
        <span class="accordion-title">${escHtml(title)}</span>
        <span class="accordion-arrow" id="arr-${id}">▶</span>
      </button>
      <div class="accordion-body" id="${id}" style="display:none">
        <p style="margin:0;white-space:pre-line">${escHtml(content)}</p>
      </div>
    </div>`;
  }).join('');
}

// ─── Tab: History (SVG Chart) ─────────────────────────────────────────────────

async function loadHistory() {
  if (_historyLoaded) return;
  _historyLoaded = true;

  const wrap = document.getElementById('chart-wrap');
  if (!wrap) return;
  wrap.innerHTML = '<div class="empty-state">加载中…</div>';

  try {
    const data = await apiFetch(`/v1/valuation/history/CN/${encodeURIComponent(TICKER)}?days=90`);
    const points = data?.points || data || [];
    if (!points.length) {
      wrap.innerHTML = '<div class="empty-state">暂无历史数据</div>';
      return;
    }
    renderHistoryChart(points, wrap);
  } catch (e) {
    wrap.innerHTML = `<div class="empty-state">历史数据加载失败：${escHtml(e.message)}</div>`;
  }
}

function renderHistoryChart(points, container) {
  const pts = points
    .map(p => ({ date: p.date, close: p.closePrice, fv: p.tradableFairValue, runDate: p.valuationRunDate || null }))
    .filter(p => p.close != null)
    .sort((a, b) => (a.date < b.date ? -1 : 1));

  if (!pts.length) { container.innerHTML = '<div class="empty-state">暂无有效历史数据</div>'; return; }

  const W = 800, H = 300;
  const PAD = { top: 20, right: 20, bottom: 36, left: 64 };
  const IW = W - PAD.left - PAD.right;
  const IH = H - PAD.top - PAD.bottom;

  const allVals = pts.flatMap(p => [p.close, p.fv].filter(v => v != null));
  const minV = Math.min(...allVals) * 0.97;
  const maxV = Math.max(...allVals) * 1.03;

  const xScale = i => PAD.left + (i / (pts.length - 1 || 1)) * IW;
  const yScale = v => PAD.top + IH - ((v - minV) / (maxV - minV || 1)) * IH;

  const closePts = pts.map((p, i) => `${xScale(i).toFixed(1)},${yScale(p.close).toFixed(1)}`).join(' ');
  const fvPts    = pts.filter(p => p.fv != null)
                      .map((p) => `${xScale(pts.indexOf(p)).toFixed(1)},${yScale(p.fv).toFixed(1)}`).join(' ');

  const labelIdxs = pts.length <= 6
    ? pts.map((_, i) => i)
    : [0, Math.floor(pts.length * 0.25), Math.floor(pts.length * 0.5), Math.floor(pts.length * 0.75), pts.length - 1];

  const xLabels = labelIdxs.map(i =>
    `<text x="${xScale(i).toFixed(1)}" y="${(PAD.top + IH + 20).toFixed(1)}" text-anchor="middle" font-size="11" fill="#6d7c91">${(pts[i].date || '').slice(0, 10)}</text>`
  ).join('');

  const yTicks = 4;
  const yLabels = Array.from({ length: yTicks + 1 }, (_, k) => {
    const val = minV + (maxV - minV) * (k / yTicks);
    const y   = yScale(val);
    return `<text x="${(PAD.left - 8).toFixed(1)}" y="${y.toFixed(1)}" text-anchor="end" font-size="11" fill="#6d7c91" dominant-baseline="middle">¥${val.toFixed(2)}</text>
            <line x1="${PAD.left}" y1="${y.toFixed(1)}" x2="${W - PAD.right}" y2="${y.toFixed(1)}" stroke="#e2e7ef" stroke-width="1" />`;
  }).join('');

  const tooltip = document.getElementById('chart-tooltip');
  const overlayRects = pts.map((p, i) => {
    const x  = (xScale(i) - (IW / pts.length / 2)).toFixed(1);
    const bw = (IW / pts.length).toFixed(1);
    return `<rect class="chart-hover-rect" x="${x}" y="${PAD.top}" width="${bw}" height="${IH}" fill="transparent"
      data-date="${p.date || ''}" data-close="${p.close?.toFixed(2) || ''}"
      data-fv="${p.fv?.toFixed(2) || ''}" data-rd="${p.runDate || ''}"></rect>`;
  }).join('');

  container.innerHTML = `<svg viewBox="0 0 ${W} ${H}" xmlns="http://www.w3.org/2000/svg" style="display:block;width:100%">
    ${yLabels}
    ${fvPts ? `<polyline points="${fvPts}" fill="none" stroke="#0d5bd7" stroke-width="2" stroke-dasharray="6 3" />` : ''}
    <polyline points="${closePts}" fill="none" stroke="#8898aa" stroke-width="2" />
    ${xLabels}
    ${overlayRects}
  </svg>`;

  const note = document.getElementById('chart-note');
  const latest = pts[pts.length - 1];
  if (note && latest.runDate) note.textContent = `最近估值日期：${latest.runDate}`;

  container.querySelectorAll('.chart-hover-rect').forEach(rect => {
    rect.addEventListener('mouseenter', () => {
      if (!tooltip) return;
      tooltip.innerHTML = `
        <div class="tt-date">${rect.dataset.date}</div>
        <div class="tt-row"><span class="tt-label">收盘价</span><span class="tt-val">¥${rect.dataset.close}</span></div>
        <div class="tt-row"><span class="tt-label">公允价值</span><span class="tt-val">${rect.dataset.fv ? '¥' + rect.dataset.fv : '—'}</span></div>
        ${rect.dataset.rd ? `<div style="font-size:11px;color:rgba(255,255,255,.6)">估值日 ${rect.dataset.rd}</div>` : ''}
      `;
      const svgRect  = container.getBoundingClientRect();
      const hoverRect = rect.getBoundingClientRect();
      tooltip.style.left    = (hoverRect.left - svgRect.left + hoverRect.width / 2) + 'px';
      tooltip.style.top     = (hoverRect.top  - svgRect.top  - 8) + 'px';
      tooltip.style.display = 'block';
    });
    rect.addEventListener('mouseleave', () => { if (tooltip) tooltip.style.display = 'none'; });
  });
}

// ─── Tab: Peers ───────────────────────────────────────────────────────────────

async function loadPeers() {
  if (_peersLoaded) return;
  _peersLoaded = true;

  const metaEl  = document.getElementById('peers-meta');
  const tableEl = document.getElementById('peers-table-wrap');
  if (!tableEl) return;
  tableEl.innerHTML = '<div class="empty-state">加载中…</div>';

  try {
    const peers = await apiFetch(`/v1/peers/CN/${encodeURIComponent(TICKER)}?limit=8`);
    if (metaEl) renderPeersMeta(peers, metaEl);
    renderPeersTable(peers?.items || [], tableEl);
  } catch (e) {
    tableEl.innerHTML = `<div class="empty-state">同行数据加载失败：${escHtml(e.message)}</div>`;
  }
}

function renderPeersMeta(peers, el) {
  const parts = [];
  if (peers.selectionBasis) parts.push(`筛选依据：${peers.selectionBasis}`);
  const peerCandidateCount = peers.peerCandidateCount ?? peers.peer_candidate_count;
  if (peerCandidateCount != null) parts.push(`候选数量：${peerCandidateCount}`);
  if (peers.peerSelectionRuleVersion) parts.push(`规则版本：${peers.peerSelectionRuleVersion}`);
  el.innerHTML = parts.length ? `<div class="peers-meta-text">${parts.map(escHtml).join('　|　')}</div>` : '';
}

function renderPeersTable(items, el) {
  if (!items.length) { el.innerHTML = '<div class="empty-state">暂无同行数据</div>'; return; }
  const rows = items.map(p => {
    const up    = p.upside != null ? ((p.upside * 100).toFixed(1) + '%') : '—';
    const upCls = p.upside != null ? (p.upside >= 0 ? 'num-good' : 'num-bad') : '';
    return `<tr>
      <td><a href="/cn-stock-detail.html?ticker=${escHtml(p.ticker || '')}" class="ticker-link">${escHtml(p.ticker || '—')}</a></td>
      <td>${escHtml(p.companyName || '—')}</td>
      <td>${p.currentPrice != null ? fmtCNY(p.currentPrice) : '—'}</td>
      <td>${p.fairValue    != null ? fmtCNY(p.fairValue)    : '—'}</td>
      <td class="${upCls}">${up}</td>
      <td>${p.pe        != null ? p.pe.toFixed(1)        + '×' : '—'}</td>
      <td>${p.evEbitda  != null ? p.evEbitda.toFixed(1)  + '×' : '—'}</td>
      <td>${p.roic      != null ? (p.roic * 100).toFixed(1) + '%' : '—'}</td>
    </tr>`;
  }).join('');
  el.innerHTML = `<table class="data-table">
    <thead><tr><th>代码</th><th>公司</th><th>现价</th><th>公允价值</th><th>上行空间</th><th>PE</th><th>EV/EBITDA</th><th>ROIC</th></tr></thead>
    <tbody>${rows}</tbody>
  </table>`;
}

// ─── Tab: Risk ────────────────────────────────────────────────────────────────

function renderRiskTable(items) {
  const el = document.getElementById('risk-table-wrap');
  if (!el) return;
  if (!items.length) { el.innerHTML = '<div class="empty-state">暂无风险数据</div>'; return; }

  const rows = items.map(r => `<tr>
    <td>${escHtml(r.factor || '—')}</td>
    <td><span class="risk-level risk-${(r.level || '').toLowerCase()}">${escHtml(r.level || '—')}</span></td>
    <td>${escHtml(String(r.adjustment ?? '—'))}</td>
    <td style="font-size:12px;color:var(--muted)">${escHtml(r.note || '—')}</td>
  </tr>`).join('');

  el.innerHTML = `<table class="data-table">
    <thead><tr><th>风险因子</th><th>等级</th><th>调整</th><th>备注</th></tr></thead>
    <tbody>${rows}</tbody>
  </table>`;
}

// ─── Accordion ────────────────────────────────────────────────────────────────

function toggleAccordion(id) {
  const body = document.getElementById(id);
  const arr  = document.getElementById('arr-' + id);
  if (!body) return;
  const open = body.style.display !== 'none';
  body.style.display = open ? 'none' : 'block';
  if (arr) arr.textContent = open ? '▶' : '▼';
}

// ─── Tab Switching ────────────────────────────────────────────────────────────

function switchTab(name, btn) {
  document.querySelectorAll('.tab-btn').forEach(b => b.classList.remove('active'));
  if (btn) btn.classList.add('active');
  document.querySelectorAll('.tab-pane').forEach(p => p.classList.remove('active'));
  const pane = document.getElementById('tab-' + name);
  if (pane) pane.classList.add('active');
  if (name === 'history') loadHistory();
  if (name === 'peers')   loadPeers();
}

// ─── Fatal Error ──────────────────────────────────────────────────────────────

function showFatalError(msg) {
  hide('loading');
  const app = document.getElementById('app');
  if (app) {
    app.innerHTML = `<div style="display:flex;flex-direction:column;align-items:center;justify-content:center;min-height:60vh;gap:16px">
      <div style="font-size:48px">⚠️</div>
      <div style="font-size:18px;color:var(--bad);font-weight:700">${escHtml(msg)}</div>
      <a href="/cn-undervalued-stocks.html" style="color:var(--blue)">← 返回榜单</a>
    </div>`;
  }
}

// ─── API Fetch + deepCamelCase ────────────────────────────────────────────────

async function apiFetch(url) {
  if (window.FairvaluePlatformApi && typeof window.FairvaluePlatformApi.fetchJson === 'function') {
    return deepCamelCase(await window.FairvaluePlatformApi.fetchJson(url));
  }
  const res = await fetch(url);
  if (!res.ok) {
    let detail = '';
    try {
      const j = await res.json();
      const raw = j && typeof j === 'object' && 'success' in j ? (j.error || j) : j;
      detail = raw.message || raw.error || '';
    } catch (_) {}
    throw new Error(`HTTP ${res.status}${detail ? '：' + detail : ''}`);
  }
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

function deepCamelCase(obj) {
  if (Array.isArray(obj)) return obj.map(deepCamelCase);
  if (obj !== null && typeof obj === 'object') {
    return Object.fromEntries(
      Object.entries(obj).map(([k, v]) => [toCamel(k), deepCamelCase(v)])
    );
  }
  return obj;
}

function toCamel(s) {
  return s.replace(/_([a-z])/g, (_, c) => c.toUpperCase());
}

// ─── Format Helpers ───────────────────────────────────────────────────────────

function fmtCNY(v) {
  if (v == null) return '—';
  if (v >= 1000) return '¥' + v.toLocaleString('zh-CN', { minimumFractionDigits: 0, maximumFractionDigits: 0 });
  if (v >= 10)   return '¥' + v.toFixed(2);
  return '¥' + v.toFixed(3);
}

function verdictLabel(v) {
  const map = { UNDERVALUED: '低估', FAIRLY_VALUED: '合理', FAIR: '合理', OVERVALUED: '高估', undervalued: '低估', overvalued: '高估' };
  return map[v] || v || '—';
}

function verdictClass(v) {
  const s = (v || '').toLowerCase();
  if (s.includes('under')) return 'undervalued';
  if (s.includes('over'))  return 'overvalued';
  return 'fair';
}

function scenarioKey(s) {
  const map = { 悲观: 'bear', bear: 'bear', 基准: 'base', base: 'base', 乐观: 'bull', bull: 'bull' };
  return map[(s || '').toLowerCase()] || 'base';
}

function scenarioLabel(s) {
  const map = { bear: '悲观', base: '基准', bull: '乐观' };
  return map[(s || '').toLowerCase()] || s;
}

function zoneClass(label) {
  const s = (label || '').toLowerCase();
  if (s.includes('买') || s.includes('buy') || s.includes('低估')) return 'zone-buy';
  if (s.includes('卖') || s.includes('avoid') || s.includes('高估'))  return 'zone-avoid';
  return 'zone-hold';
}

function sectionLabel(key) {
  const map = {
    businessOverview: '公司概况', dcfAnalysis: 'DCF 分析', relativeValuation: '相对估值',
    historicalMultiple: '历史倍数', riskFactors: '风险因素', summary: '总结',
    investmentThesis: '投资结论', valuationMethodSummary: '估值方法摘要',
    dcfAssumptions: 'DCF 假设', peerComparison: '同行对比', operationStrategy: '操作策略',
    uncertaintyAndErrorSources: '数据来源与不确定性',
  };
  return map[key] || key.replace(/([A-Z])/g, ' $1').replace(/^./, s => s.toUpperCase());
}

function marketLabel(m) {
  const map = { CN: 'A股', US: '美股', HK: '港股', JP: '日股' };
  return map[(m || '').toUpperCase()] || m;
}

// ─── DOM Helpers ──────────────────────────────────────────────────────────────

function setText(id, text) {
  const el = document.getElementById(id);
  if (el) el.textContent = text ?? '—';
}

function show(id) {
  const el = typeof id === 'string' ? document.getElementById(id) : id;
  if (el) el.style.display = '';
}

function hide(id) {
  const el = typeof id === 'string' ? document.getElementById(id) : id;
  if (el) el.style.display = 'none';
}

function escHtml(str) {
  return String(str || '')
    .replace(/&/g, '&amp;').replace(/</g, '&lt;')
    .replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}
