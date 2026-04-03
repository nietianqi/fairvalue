/**
 * us-stock-detail.js  —  US Stock Valuation Detail Page
 * Served by Spring Boot static at /assets/us-stock-detail.js
 *
 * API surface used:
 *   GET  /v1/us-equities/{ticker}/profile
 *   GET  /v1/us-equities/{ticker}/valuation/summary
 *   GET  /v1/us-equities/{ticker}/data-quality
 *   GET  /v1/us-equities/{ticker}/financial-quality
 *   GET  /v1/us-equities/{ticker}/valuation/report
 *   GET  /v1/valuation/history/US/{ticker}?days=90
 *   GET  /v1/peers/US/{ticker}?limit=8
 *   POST /v1/us-equities/{ticker}/valuation/run
 */

'use strict';

// ─── Globals ────────────────────────────────────────────────────────────────

let TICKER = '';
let _historyLoaded = false;
let _peersLoaded   = false;

// ─── Boot ────────────────────────────────────────────────────────────────────

document.addEventListener('DOMContentLoaded', () => {
  const params = new URLSearchParams(window.location.search);
  TICKER = (params.get('ticker') || '').toUpperCase().trim();

  if (!TICKER) {
    showFatalError('缺少 ticker 参数。请从榜单页选择股票。');
    return;
  }

  document.title = `${TICKER} 估值详情 — Fairvalue`;
  loadAll();
});

// ─── Main Load Orchestrator ──────────────────────────────────────────────────

async function loadAll() {
  try {
    // Step 1: parallel — profile + summary + quality cards
    const [profile, summary, dataQuality, finQuality] = await Promise.all([
      apiFetch(`/v1/us-equities/${TICKER}/profile`),
      apiFetch(`/v1/us-equities/${TICKER}/valuation/summary`),
      apiFetch(`/v1/us-equities/${TICKER}/data-quality`),
      apiFetch(`/v1/us-equities/${TICKER}/financial-quality`),
    ]);

    renderHeader(profile, summary);
    renderHero(summary);
    renderOverview(profile, dataQuality, finQuality);

    show('main');
    hide('loading');

    // Step 2: valuation report (needed for valuation tab)
    const report = await apiFetch(`/v1/us-equities/${TICKER}/valuation/report`);
    renderValuation(report, summary);

    // Patch hero with decision data (marginOfSafety, valueTrapFlag) from report
    const decision = report?.decision || {};
    if (decision.marginOfSafety != null) {
      setText('hero-mos', (decision.marginOfSafety * 100).toFixed(1) + '%');
    }
    if (decision.valueTrapFlag === true) {
      show('value-trap-alert');
    }

    // Step 3: lazy — history + peers loaded on tab switch
  } catch (err) {
    showFatalError('数据加载失败：' + (err.message || '未知错误'));
  }
}

// ─── Header ──────────────────────────────────────────────────────────────────

function renderHeader(profile, summary) {
  setText('hdr-ticker', TICKER);
  setText('hdr-name', profile?.companyName || profile?.name || '—');

  const exchange = profile?.exchange || 'US';
  setText('hdr-exchange', exchange);

  const companyType = profile?.companyType || summary?.classification || '';
  const typeEl = document.getElementById('hdr-type');
  if (typeEl) {
    typeEl.textContent = formatCompanyType(companyType);
    typeEl.className = 'badge ' + verdictClass(companyType, 'type');
  }
}

// ─── Hero Band ───────────────────────────────────────────────────────────────

function renderHero(summary) {
  if (!summary) return;

  // Current price
  const price = summary.currentPrice;
  setText('hero-price', price != null ? fmtUSD(price) : '—');

  // Fair value range
  const fvr = summary.fairValueRange || {};
  setText('hero-fv-low',  fvr.low  != null ? fmtUSD(fvr.low)  : '—');
  setText('hero-fv-mid',  fvr.mid  != null ? fmtUSD(fvr.mid)  : '—');
  setText('hero-fv-high', fvr.high != null ? fmtUSD(fvr.high) : '—');

  // Upside
  const upside = summary.upsideDownside;
  const upsideEl = document.getElementById('hero-upside');
  if (upsideEl && upside != null) {
    const pct = (upside * 100).toFixed(1);
    upsideEl.textContent = (upside >= 0 ? '▲ ' : '▼ ') + Math.abs(pct) + '%';
    upsideEl.className = 'upside-value ' + (upside >= 0 ? 'up' : 'down');
  }

  // Verdict
  const verdictEl = document.getElementById('hero-verdict');
  if (verdictEl) {
    const v = summary.verdict || '';
    verdictEl.textContent = verdictLabel(v);
    verdictEl.className = 'verdict-badge ' + verdictClass(v);
  }

  // Price zones
  renderZone('hero-buy-zone',  summary.buyZone);
  renderZone('hero-hold-zone', summary.holdZone);
  renderZone('hero-avoid-zone', summary.avoidZone);

  // Confidence bar
  const conf = summary.confidenceLevel;  // 0.0–1.0
  if (conf != null) {
    const pct = Math.round(conf * 100);
    const fill = document.getElementById('hero-conf-fill');
    if (fill) fill.style.width = pct + '%';
    setText('hero-conf-pct', pct + '%');
  }

  // Margin of safety
  const mos = summary.marginOfSafety != null ? summary.marginOfSafety
            : (summary.upsideDownside != null ? summary.upsideDownside : null);
  if (mos != null) {
    setText('hero-mos', (mos * 100).toFixed(1) + '%');
  }

  // Value trap warning
  if (summary.valueTrapFlag === true) {
    show('value-trap-alert');
  }
}

function renderZone(elId, zone) {
  if (!zone) return;
  const el = document.getElementById(elId);
  if (!el) return;
  // zone may be { low, high } or a string
  if (typeof zone === 'string') {
    el.textContent = zone;
  } else if (zone.low != null && zone.high != null) {
    el.textContent = `${fmtUSD(zone.low)} – ${fmtUSD(zone.high)}`;
  } else if (zone.low != null) {
    el.textContent = `≤ ${fmtUSD(zone.low)}`;
  } else if (zone.high != null) {
    el.textContent = `≥ ${fmtUSD(zone.high)}`;
  }
}

// ─── Tab: Overview ───────────────────────────────────────────────────────────

function renderOverview(profile, dataQuality, finQuality) {
  renderProfileCard(profile);
  renderDataQualityCard(dataQuality);
  renderFinQualityCard(finQuality);
}

function renderProfileCard(profile) {
  const el = document.getElementById('profile-rows');
  if (!el) return;
  if (!profile) { el.innerHTML = '<div class="empty-state">暂无数据</div>'; return; }

  const rows = [
    ['交易所',    profile.exchange || '—'],
    ['行业',      profile.sector   || '—'],
    ['子行业',    profile.industry || '—'],
    ['市值',      profile.marketCap != null ? fmtMarketCap(profile.marketCap) : '—'],
    ['公司类型',  formatCompanyType(profile.companyType || '')],
    ['估值模板',  profile.sectorTemplate || '—'],
    ['数据版本',  profile.dataVersion || profile.data_version || '—'],
  ];

  el.innerHTML = rows.map(([label, value]) =>
    `<div class="profile-row">
       <span class="profile-label">${label}</span>
       <span class="profile-value">${escHtml(String(value))}</span>
     </div>`
  ).join('');
}

function renderDataQualityCard(dq) {
  const el = document.getElementById('data-quality-section');
  if (!el) return;
  if (!dq) { el.innerHTML = '<div class="empty-state">暂无数据</div>'; return; }

  const conf = dq.confidenceLevel != null ? Math.round(dq.confidenceLevel * 100) : null;
  const missingItems = dq.missingItems || dq.missing_items || [];
  const warnings     = dq.warningFlags || dq.warning_flags || [];
  const filingDate   = dq.latestFilingDate || dq.latest_filing_date || null;

  let html = '';
  if (conf != null) {
    html += `<div class="confidence-wrap" style="margin-bottom:12px">
      <span class="confidence-label">置信度</span>
      <div class="confidence-bar">
        <div class="confidence-fill" style="width:${conf}%"></div>
      </div>
      <span class="confidence-pct">${conf}%</span>
    </div>`;
  }
  if (filingDate) {
    html += `<div class="profile-row"><span class="profile-label">最新财报日期</span><span class="profile-value">${escHtml(filingDate)}</span></div>`;
  }
  if (missingItems.length) {
    html += `<div style="margin-top:10px"><div style="font-size:12px;color:var(--muted);margin-bottom:4px">缺失项</div>`;
    html += missingItems.map(m => `<div class="tag tag-warn">${escHtml(String(m))}</div>`).join('');
    html += '</div>';
  }
  if (warnings.length) {
    html += `<div style="margin-top:10px"><div style="font-size:12px;color:var(--muted);margin-bottom:4px">警告</div>`;
    html += warnings.map(w => `<div class="tag tag-bad">${escHtml(String(w))}</div>`).join('');
    html += '</div>';
  }
  if (!html) html = '<div class="empty-state">数据质量正常</div>';
  el.innerHTML = html;
}

function renderFinQualityCard(fq) {
  const el = document.getElementById('fin-quality-section');
  if (!el) return;
  if (!fq) { el.innerHTML = '<div class="empty-state">暂无数据</div>'; return; }

  const total    = fq.totalQualityScore  != null ? Math.round(fq.totalQualityScore * 100)    : null;
  const earnings = fq.earningsScore      != null ? Math.round(fq.earningsScore * 100)         : null;
  const revenue  = fq.revenueScore       != null ? Math.round(fq.revenueScore * 100)          : null;
  const capital  = fq.capitalEfficiencyScore != null ? Math.round(fq.capitalEfficiencyScore * 100) : null;
  const redFlags = fq.redFlags || fq.red_flags || [];

  let html = '';
  if (total != null) {
    const cls = total >= 70 ? 'good' : total >= 40 ? 'warn' : 'bad';
    html += `<div style="text-align:center;margin-bottom:14px">
      <div style="font-size:32px;font-weight:800;color:var(--${cls})">${total}</div>
      <div style="font-size:12px;color:var(--muted)">总财务质量评分</div>
    </div>`;
  }
  const scoreRows = [
    ['盈利质量', earnings],
    ['收入质量', revenue],
    ['资本效率', capital],
  ];
  scoreRows.forEach(([label, score]) => {
    if (score == null) return;
    const cls = score >= 70 ? 'good' : score >= 40 ? 'warn' : 'bad';
    html += `<div class="confidence-wrap" style="margin-bottom:8px">
      <span class="confidence-label" style="min-width:70px">${label}</span>
      <div class="confidence-bar"><div class="confidence-fill" style="width:${score}%;background:var(--${cls})"></div></div>
      <span class="confidence-pct">${score}</span>
    </div>`;
  });
  if (redFlags.length) {
    html += `<div style="margin-top:10px"><div style="font-size:12px;color:var(--muted);margin-bottom:4px">红旗警示</div>`;
    html += redFlags.map(f => `<div class="tag tag-bad">${escHtml(String(f))}</div>`).join('');
    html += '</div>';
  }
  if (!html) html = '<div class="empty-state">暂无数据</div>';
  el.innerHTML = html;
}

// ─── Tab: Valuation ──────────────────────────────────────────────────────────

function renderValuation(report, summary) {
  if (!report) return;

  // Executive summary
  const oneLiner = report.oneLineVerdict || report.one_line_verdict || '';
  const execSum  = report.executiveSummary || report.executive_summary || '';
  setText('exec-one-liner', oneLiner);
  setText('exec-summary',   execSum);

  // Implied expectation
  const implied = summary?.impliedExpectation || report.decision?.impliedExpectation || '';
  if (implied) {
    setText('implied-text', implied);
    show('implied-box');
  }

  // Method breakdown table
  renderMethodTable(report.valuationBreakdown || report.valuation_breakdown || []);

  // Scenario matrix
  renderScenarioMatrix(report.scenarioMatrix || report.scenario_matrix || []);

  // Source attribution
  const decision = report.decision || {};
  patchHeroDecision(decision);
  renderSourceAttribution(decision.sourceAttribution || decision.source_attribution || {});

  // Explanation accordion — from report.explanation.blocks or report.explanationBlocks
  const expBlocks = report.explanation?.blocks
    || report.explanationBlocks
    || report.explanation_blocks
    || [];
  renderExplanationBlocks(expBlocks);

  // Risk matrix (render into risk tab)
  const riskItems = report.riskMatrix || report.risk_matrix
    || decision.riskMatrix || decision.risk_matrix || [];
  renderRiskMatrix(riskItems);
}

function patchHeroDecision(decision) {
  if (!decision) return;

  const decisionMos = decision.marginOfSafety ?? decision.margin_of_safety;
  if (decisionMos != null) {
    setText('hero-mos', (decisionMos * 100).toFixed(1) + '%');
  }

  const valueTrap = decision.valueTrapFlag ?? decision.value_trap_flag;
  if (valueTrap === true) {
    show('value-trap-alert');
  } else if (valueTrap === false) {
    hide('value-trap-alert');
  }
}

function renderMethodTable(breakdown) {
  const el = document.getElementById('method-table-wrap');
  if (!el) return;
  if (!breakdown.length) {
    el.innerHTML = '<div class="empty-state">暂无估值方法数据</div>';
    return;
  }

  const rows = breakdown.map(m => {
    const weight = m.weight != null ? (m.weight * 100).toFixed(0) + '%' : '—';
    return `<tr>
      <td><strong>${escHtml(methodLabel(m.method || ''))}</strong></td>
      <td>${weight}</td>
      <td>${fmtUSDOrDash(m.bearValue)}</td>
      <td>${fmtUSDOrDash(m.baseValue)}</td>
      <td>${fmtUSDOrDash(m.bullValue)}</td>
      <td style="color:var(--muted);font-size:12px">${escHtml(m.rationale || '—')}</td>
    </tr>`;
  }).join('');

  el.innerHTML = `<table class="data-table">
    <thead><tr>
      <th>方法</th><th>权重</th><th>悲观</th><th>基准</th><th>乐观</th><th>备注</th>
    </tr></thead>
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

  // Sort: bear / base / bull
  const order = { bear: 0, base: 1, bull: 2 };
  const sorted = [...scenarios].sort((a, b) => {
    const ka = (a.scenario || '').toLowerCase();
    const kb = (b.scenario || '').toLowerCase();
    return (order[ka] ?? 1) - (order[kb] ?? 1);
  });

  el.innerHTML = sorted.map(s => {
    const key   = (s.scenario || '').toLowerCase();
    const label = scenarioLabel(s.scenario || '');
    const prob  = s.probability != null ? (s.probability * 100).toFixed(0) + '%' : '—';
    const price = fmtUSDOrDash(s.targetPrice);
    const up    = s.upside != null ? (s.upside * 100).toFixed(1) + '%' : '—';
    const upCls = s.upside != null ? (s.upside >= 0 ? 'up' : 'down') : '';

    // Key assumptions from fairValueLow/High or generic fields
    const low  = fmtUSDOrDash(s.fairValueLow);
    const high = fmtUSDOrDash(s.fairValueHigh);
    const range = (s.fairValueLow != null && s.fairValueHigh != null)
      ? `<div class="scenario-assumption">公允区间 ${low} – ${high}</div>` : '';

    return `<div class="scenario-card ${key}">
      <div class="scenario-label">${label}</div>
      <div class="scenario-prob">概率 ${prob}</div>
      <div class="scenario-price">${price}</div>
      <div class="scenario-upside ${upCls}">${s.upside != null ? (s.upside >= 0 ? '▲' : '▼') + ' ' + Math.abs((s.upside * 100).toFixed(1)) + '%' : '—'}</div>
      ${range}
    </div>`;
  }).join('');
}

function renderSourceAttribution(attr) {
  const el = document.getElementById('attr-grid');
  if (!el) return;

  const displayRules = {
    price_source:              '价格来源',
    risk_free_rate_source:     '无风险利率',
    erp_source:                'ERP来源',
    beta_source:               'Beta来源',
    industry_multiple_source:  '行业倍数来源',
    market_multiple_source:    '市场倍数来源',
    peer_set_source:           'Peer集合来源',
    peer_selection_basis:      'Peer筛选依据',
    relative_source_mode:      '相对估值模式',
    data_version:              '数据版本',
  };

  let html = '';
  for (const [key, label] of Object.entries(displayRules)) {
    const raw = attr[key] ?? attr[toCamelCase(key)];
    const display = raw != null ? formatAttrValue(raw) : '来自估值参数模板';
    html += `<div class="attr-item">
      <div class="attr-label">${label}</div>
      <div class="attr-value">${escHtml(display)}</div>
    </div>`;
  }

  // Peer set tickers as badges
  const peerTickers = attr.peer_set_tickers || attr.peerSetTickers || [];
  if (peerTickers.length) {
    html += `<div class="attr-item" style="grid-column:1/-1">
      <div class="attr-label">Peer 股票池</div>
      <div class="attr-value">${peerTickers.map(t => `<span class="badge badge-neutral" style="margin:2px">${escHtml(t)}</span>`).join('')}</div>
    </div>`;
  }

  el.innerHTML = html || '<div class="empty-state">暂无来源归因数据</div>';
}

// ─── Tab: History (SVG Chart) ─────────────────────────────────────────────────

async function loadHistory() {
  if (_historyLoaded) return;
  _historyLoaded = true;

  const wrap = document.getElementById('chart-wrap');
  if (!wrap) return;
  wrap.innerHTML = '<div class="empty-state">加载中…</div>';

  try {
    const data = await apiFetch(`/v1/valuation/history/US/${TICKER}?days=90`);
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
  // Parse + sort by date
  const pts = points
    .map(p => ({
      date:     p.date,
      close:    p.closePrice,
      fv:       p.tradableFairValue,
      runDate:  p.valuationRunDate || null,
    }))
    .filter(p => p.close != null)
    .sort((a, b) => a.date < b.date ? -1 : 1);

  if (!pts.length) {
    container.innerHTML = '<div class="empty-state">暂无有效历史数据</div>';
    return;
  }

  const W = 800, H = 300;
  const PAD = { top: 20, right: 20, bottom: 36, left: 56 };
  const IW = W - PAD.left - PAD.right;
  const IH = H - PAD.top  - PAD.bottom;

  const allVals = pts.flatMap(p => [p.close, p.fv].filter(v => v != null));
  const minV = Math.min(...allVals) * 0.97;
  const maxV = Math.max(...allVals) * 1.03;

  const xScale = i => PAD.left + (i / (pts.length - 1)) * IW;
  const yScale = v => PAD.top + IH - ((v - minV) / (maxV - minV)) * IH;

  // Build polyline points
  const closePts = pts.map((p, i) => `${xScale(i).toFixed(1)},${yScale(p.close).toFixed(1)}`).join(' ');
  const fvPts    = pts.filter(p => p.fv != null)
                      .map((p, _) => {
                        const i = pts.indexOf(p);
                        return `${xScale(i).toFixed(1)},${yScale(p.fv).toFixed(1)}`;
                      }).join(' ');

  // Shaded area between close and fv
  let shadePath = '';
  const fvPtsArr = pts.map((p, i) => p.fv != null ? { x: xScale(i), y: yScale(p.fv) } : null).filter(Boolean);
  if (fvPtsArr.length > 1) {
    const closePtsArr = pts.map((p, i) => ({ x: xScale(i), y: yScale(p.close) }));
    // Build a fill polygon: forward on close, backward on fv
    const poly = closePtsArr.map(p => `${p.x.toFixed(1)},${p.y.toFixed(1)}`).join(' ') + ' ' +
                 [...closePtsArr].reverse().map((_, ri) => {
                   const fi = pts.length - 1 - ri;
                   const fv = pts[fi].fv;
                   return fv != null ? `${xScale(fi).toFixed(1)},${yScale(fv).toFixed(1)}` : null;
                 }).filter(Boolean).join(' ');
    shadePath = `<polygon points="${poly}" fill="rgba(13,91,215,0.07)" />`;
  }

  // X-axis labels — pick ~5 evenly spaced dates
  const labelIdxs = pts.length <= 6
    ? pts.map((_, i) => i)
    : [0, Math.floor(pts.length * 0.25), Math.floor(pts.length * 0.5), Math.floor(pts.length * 0.75), pts.length - 1];

  const xLabels = labelIdxs.map(i => {
    const d = pts[i].date ? pts[i].date.slice(0, 10) : '';
    return `<text x="${xScale(i).toFixed(1)}" y="${(PAD.top + IH + 20).toFixed(1)}" text-anchor="middle" font-size="11" fill="#6d7c91">${d}</text>`;
  }).join('');

  // Y-axis labels
  const yTicks = 4;
  const yLabels = Array.from({ length: yTicks + 1 }, (_, k) => {
    const val = minV + (maxV - minV) * (k / yTicks);
    const y   = yScale(val);
    return `<text x="${(PAD.left - 8).toFixed(1)}" y="${y.toFixed(1)}" text-anchor="end" font-size="11" fill="#6d7c91" dominant-baseline="middle">${fmtUSD(val)}</text>
            <line x1="${PAD.left}" y1="${y.toFixed(1)}" x2="${W - PAD.right}" y2="${y.toFixed(1)}" stroke="#e2e7ef" stroke-width="1" />`;
  }).join('');

  // Hover overlay — invisible rects triggering tooltip
  const tooltip  = document.getElementById('chart-tooltip');
  const chartNote = document.getElementById('chart-note');

  const overlayRects = pts.map((p, i) => {
    const x = (xScale(i) - (IW / pts.length / 2)).toFixed(1);
    const bw = (IW / pts.length).toFixed(1);
    const dataAttrs = [
      `data-date="${p.date || ''}"`,
      `data-close="${p.close?.toFixed(2) || ''}"`,
      `data-fv="${p.fv?.toFixed(2) || ''}"`,
      `data-rd="${p.runDate || ''}"`,
      `data-x="${xScale(i).toFixed(1)}"`,
      `data-y="${yScale(p.close).toFixed(1)}"`,
    ].join(' ');
    return `<rect class="chart-hover-rect" x="${x}" y="${PAD.top}" width="${bw}" height="${IH}" fill="transparent" ${dataAttrs}></rect>`;
  }).join('');

  const svg = `<svg viewBox="0 0 ${W} ${H}" xmlns="http://www.w3.org/2000/svg" class="chart-svg" style="display:block;width:100%">
    <!-- Grid lines -->
    ${yLabels}
    <!-- Shaded fill -->
    ${shadePath}
    <!-- Fair value line -->
    ${fvPts ? `<polyline points="${fvPts}" fill="none" stroke="#0d5bd7" stroke-width="2" stroke-dasharray="6 3" />` : ''}
    <!-- Close price line -->
    <polyline points="${closePts}" fill="none" stroke="#8898aa" stroke-width="2" />
    <!-- X-axis labels -->
    ${xLabels}
    <!-- Hover overlay -->
    ${overlayRects}
  </svg>`;

  container.innerHTML = svg;

  if (chartNote) {
    const latest = pts[pts.length - 1];
    chartNote.textContent = latest.runDate ? `最近估值日期：${latest.runDate}` : '';
  }

  // Attach tooltip events
  container.querySelectorAll('.chart-hover-rect').forEach(rect => {
    rect.addEventListener('mouseenter', e => {
      if (!tooltip) return;
      const d    = rect.dataset.date  || '';
      const cl   = rect.dataset.close || '—';
      const fv   = rect.dataset.fv    || '—';
      const rd   = rect.dataset.rd    || '';
      tooltip.innerHTML = `
        <div class="tt-date">${d}</div>
        <div class="tt-row"><span class="tt-label">收盘价</span><span class="tt-val">$${cl}</span></div>
        <div class="tt-row"><span class="tt-label">公允价值</span><span class="tt-val">${fv !== '' ? '$' + fv : '—'}</span></div>
        ${rd ? `<div class="tt-row" style="font-size:11px;color:var(--muted)">估值日 ${rd}</div>` : ''}
      `;
      const svgRect  = container.getBoundingClientRect();
      const rectRect = rect.getBoundingClientRect();
      const tx = rectRect.left - svgRect.left + rectRect.width / 2;
      const ty = rectRect.top  - svgRect.top;
      tooltip.style.left    = tx + 'px';
      tooltip.style.top     = (ty - 8) + 'px';
      tooltip.style.display = 'block';
    });
    rect.addEventListener('mouseleave', () => {
      if (tooltip) tooltip.style.display = 'none';
    });
  });
}

// ─── Tab: Peers ───────────────────────────────────────────────────────────────

async function loadPeers() {
  if (_peersLoaded) return;
  _peersLoaded = true;

  const metaEl  = document.getElementById('peers-meta');
  const tableEl = document.getElementById('peers-table-wrap');
  if (!metaEl || !tableEl) return;

  metaEl.innerHTML  = '<div class="empty-state">加载中…</div>';
  tableEl.innerHTML = '<div class="empty-state">加载中…</div>';

  try {
    const peers = await apiFetch(`/v1/peers/US/${TICKER}?limit=8`);
    renderPeersMeta(peers, metaEl);
    renderPeersTable(peers?.items || [], tableEl);
  } catch (e) {
    tableEl.innerHTML = `<div class="empty-state">同行数据加载失败：${escHtml(e.message)}</div>`;
  }
}

function renderPeersMeta(peers, el) {
  if (!peers) { el.innerHTML = ''; return; }
  const parts = [];
  if (peers.selectionBasis || peers.selection_basis) parts.push(`筛选依据：${peers.selectionBasis || peers.selection_basis}`);
  if (peers.sourceMode      || peers.source_mode)     parts.push(`来源模式：${peers.sourceMode || peers.source_mode}`);
  const peerCandidateCount = peers.peerCandidateCount ?? peers.peer_candidate_count;
  if (peerCandidateCount != null)                      parts.push(`候选数量：${peerCandidateCount}`);
  if (peers.peerSelectionRuleVersion || peers.peer_selection_rule_version)
    parts.push(`规则版本：${peers.peerSelectionRuleVersion || peers.peer_selection_rule_version}`);
  el.innerHTML = parts.length
    ? `<div class="peers-meta-text">${parts.map(escHtml).join('　|　')}</div>`
    : '';
}

function renderPeersTable(items, el) {
  if (!items.length) {
    el.innerHTML = '<div class="empty-state">暂无同行数据</div>';
    return;
  }

  const rows = items.map(p => {
    const up   = p.upside != null ? ((p.upside * 100).toFixed(1) + '%') : '—';
    const upCls = p.upside != null ? (p.upside >= 0 ? 'num-good' : 'num-bad') : '';
    return `<tr>
      <td><a href="/us-stock-detail.html?ticker=${escHtml(p.ticker || '')}" class="ticker-link">${escHtml(p.ticker || '—')}</a></td>
      <td>${escHtml(p.companyName || p.company_name || '—')}</td>
      <td>${fmtUSDOrDash(p.currentPrice || p.current_price)}</td>
      <td>${fmtUSDOrDash(p.fairValue || p.fair_value)}</td>
      <td class="${upCls}">${up}</td>
      <td>${p.pe != null ? p.pe.toFixed(1) + '×' : '—'}</td>
      <td>${p.evEbitda != null ? p.evEbitda.toFixed(1) + '×' : (p.ev_ebitda != null ? p.ev_ebitda.toFixed(1) + '×' : '—')}</td>
      <td>${p.roic != null ? (p.roic * 100).toFixed(1) + '%' : '—'}</td>
      <td>${p.fcfMargin != null ? (p.fcfMargin * 100).toFixed(1) + '%' : (p.fcf_margin != null ? (p.fcf_margin * 100).toFixed(1) + '%' : '—')}</td>
    </tr>`;
  }).join('');

  el.innerHTML = `<table class="data-table">
    <thead><tr>
      <th>股票代码</th><th>公司</th><th>现价</th><th>公允价值</th><th>上行空间</th>
      <th>PE</th><th>EV/EBITDA</th><th>ROIC</th><th>FCF利润率</th>
    </tr></thead>
    <tbody>${rows}</tbody>
  </table>`;
}

// ─── Tab: Risk ────────────────────────────────────────────────────────────────

function renderRiskMatrix(items) {
  const el = document.getElementById('risk-table-wrap');
  if (!el) return;
  if (!items.length) {
    el.innerHTML = '<div class="empty-state">暂无风险数据</div>';
    return;
  }

  const rows = items.map(r => {
    const probLabel   = r.probability || '—';
    const impactLabel = r.impact      || '—';
    const adjType     = r.adjustmentType || r.adjustment_type || '—';
    const adj         = r.adjustment  || '—';
    return `<tr>
      <td>${escHtml(riskLabel(r.riskType || r.risk_type || ''))}</td>
      <td><span class="risk-level risk-${(r.probability || '').toLowerCase()}">${escHtml(probLabel)}</span></td>
      <td><span class="risk-level risk-${(r.impact || '').toLowerCase()}">${escHtml(impactLabel)}</span></td>
      <td>${escHtml(adjType)}</td>
      <td>${escHtml(String(adj))}</td>
      <td style="font-size:12px;color:var(--muted)">${escHtml(r.note || '—')}</td>
    </tr>`;
  }).join('');

  el.innerHTML = `<table class="data-table">
    <thead><tr>
      <th>风险类型</th><th>概率</th><th>影响</th><th>调整类型</th><th>调整值</th><th>备注</th>
    </tr></thead>
    <tbody>${rows}</tbody>
  </table>`;
}

// ─── Explanation Blocks (Accordion) ──────────────────────────────────────────

function renderExplanationBlocks(blocks) {
  const el = document.getElementById('explanation-accordion');
  if (!el) return;
  if (!blocks.length) {
    el.innerHTML = '<div class="empty-state">暂无解释数据</div>';
    return;
  }

  const sorted = [...blocks].sort((a, b) => (a.displayOrder || 0) - (b.displayOrder || 0));

  el.innerHTML = sorted.map((b, idx) => {
    const id = `acc-${idx}`;
    return `<div class="accordion-item">
      <button class="accordion-header" onclick="toggleAccordion('${id}')">
        <span class="accordion-title">${escHtml(b.title || b.key || '')}</span>
        <span class="accordion-arrow" id="arr-${id}">▶</span>
      </button>
      <div class="accordion-body" id="${id}" style="display:none">
        <p>${escHtml(b.content || '').replace(/\n/g, '<br>')}</p>
      </div>
    </div>`;
  }).join('');
}

function toggleAccordion(id) {
  const body = document.getElementById(id);
  const arr  = document.getElementById('arr-' + id);
  if (!body) return;
  const open = body.style.display !== 'none';
  body.style.display = open ? 'none' : 'block';
  if (arr) arr.textContent = open ? '▶' : '▼';
}

// ─── Source Attribution Toggle ────────────────────────────────────────────────

function toggleAttr() {
  const body   = document.getElementById('attr-body');
  const toggle = document.getElementById('attr-toggle');
  if (!body || !toggle) return;
  const open = body.classList.contains('open');
  body.classList.toggle('open', !open);
  const arrow = toggle.querySelector('.arrow');
  if (arrow) arrow.textContent = open ? '▶' : '▼';
}

// ─── Run Valuation ────────────────────────────────────────────────────────────

async function runValuation() {
  const btn = document.getElementById('btn-run');
  if (!btn) return;
  btn.disabled = true;
  btn.textContent = '⟳ 估值中…';

  try {
    await apiFetch(`/v1/us-equities/${TICKER}/valuation/run`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        style: 'balanced',
        horizon: '6-18m',
        useConsensus: true,
        forceMethods: [],
        customAssumptions: {},
      }),
    });
    showToast('✓ 重新估值完成，正在刷新…');
    // Reload page data
    _historyLoaded = false;
    _peersLoaded   = false;
    hide('main');
    show('loading');
    setTimeout(() => loadAll(), 600);
  } catch (e) {
    showToast('✗ 估值失败：' + (e.message || '未知错误'), 'error');
    btn.disabled = false;
    btn.textContent = '⟳ 重新估值';
  }
}

// ─── Tab Switching ────────────────────────────────────────────────────────────

function switchTab(name, btn) {
  // Update tab buttons
  document.querySelectorAll('.tab-btn').forEach(b => b.classList.remove('active'));
  if (btn) btn.classList.add('active');

  // Update panes
  document.querySelectorAll('.tab-pane').forEach(p => p.classList.remove('active'));
  const pane = document.getElementById('tab-' + name);
  if (pane) pane.classList.add('active');

  // Lazy-load history and peers
  if (name === 'history') loadHistory();
  if (name === 'peers')   loadPeers();
}

// ─── Toast ────────────────────────────────────────────────────────────────────

function showToast(msg, type = 'success') {
  const el = document.getElementById('toast');
  if (!el) return;
  el.textContent = msg;
  el.className = 'toast ' + (type === 'error' ? 'toast-error' : 'toast-success');
  el.classList.add('visible');
  setTimeout(() => el.classList.remove('visible'), 3500);
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

// ─── API Fetch Helper ─────────────────────────────────────────────────────────

async function apiFetch(url, options = {}) {
  if (window.FairvaluePlatformApi && typeof window.FairvaluePlatformApi.fetchJson === 'function') {
    return deepCamelCase(await window.FairvaluePlatformApi.fetchJson(url, options));
  }
  const res = await fetch(url, options);
  if (!res.ok) {
    let detail = '';
    try {
      const j = await res.json();
      const raw = j && typeof j === 'object' && 'success' in j ? (j.error || j) : j;
      detail = raw.message || raw.error || '';
    } catch (_) {}
    throw new Error(`HTTP ${res.status}${detail ? '：' + detail : ''}`);
  }
  const data = unwrapApiEnvelope(await res.json());
  // Normalize snake_case keys (Jackson SNAKE_CASE strategy) → camelCase for uniform JS access
  return deepCamelCase(data);
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
 * e.g. { fair_value_range: { low: 1 } } → { fairValueRange: { low: 1 } }
 */
function deepCamelCase(obj) {
  if (Array.isArray(obj)) return obj.map(deepCamelCase);
  if (obj !== null && typeof obj === 'object') {
    return Object.fromEntries(
      Object.entries(obj).map(([k, v]) => [toCamelCase(k), deepCamelCase(v)])
    );
  }
  return obj;
}

// ─── Format Helpers ───────────────────────────────────────────────────────────

function fmtUSD(v) {
  if (v == null) return '—';
  if (v >= 1000) return '$' + v.toLocaleString('en-US', { minimumFractionDigits: 0, maximumFractionDigits: 0 });
  if (v >= 100)  return '$' + v.toFixed(1);
  return '$' + v.toFixed(2);
}

function fmtUSDOrDash(v) {
  return v != null ? fmtUSD(v) : '—';
}

function fmtMarketCap(v) {
  if (v == null) return '—';
  if (v >= 1e12) return '$' + (v / 1e12).toFixed(2) + 'T';
  if (v >= 1e9)  return '$' + (v / 1e9).toFixed(1)  + 'B';
  if (v >= 1e6)  return '$' + (v / 1e6).toFixed(1)  + 'M';
  return '$' + v.toLocaleString();
}

function verdictLabel(v) {
  const map = {
    UNDERVALUED: '低估', FAIRLY_VALUED: '合理', OVERVALUED: '高估',
    undervalued: '低估', fairly_valued: '合理', overvalued: '高估',
    FAIR: '合理', fair: '合理',
  };
  return map[v] || v || '—';
}

function verdictClass(v) {
  const s = (v || '').toLowerCase();
  if (s.includes('under')) return 'undervalued';
  if (s.includes('over'))  return 'overvalued';
  return 'fair';
}

function methodLabel(m) {
  const map = {
    dcf:                  'DCF',
    reverse_dcf:          '反向DCF',
    relative_valuation:   '相对估值',
    historical_multiple:  '历史倍数',
  };
  return map[m.toLowerCase().replace(/ /g, '_')] || m;
}

function scenarioLabel(s) {
  const map = { bear: '悲观', base: '基准', bull: '乐观' };
  return map[(s || '').toLowerCase()] || s;
}

function riskLabel(r) {
  const map = {
    earnings_miss:            '盈利未达预期',
    multiple_compression:     '估值压缩',
    balance_sheet:            '资产负债表风险',
    liquidity:                '流动性风险',
    regulatory:               '监管风险',
    competition:              '竞争加剧',
    macro:                    '宏观经济',
    interest_rate:            '利率风险',
    currency:                 '汇率风险',
    execution:                '执行风险',
  };
  return map[r.toLowerCase().replace(/ /g, '_')] || r;
}

function formatCompanyType(ct) {
  const map = {
    compounder:       '复利增长型',
    income_defensive: '收息防御型',
    growth:           '成长型',
    cyclical:         '周期型',
    turnaround:       '转型修复型',
    general_quality:  '综合质量型',
  };
  const key = (ct || '').split('/')[0].trim().toLowerCase().replace(/ /g, '_');
  return map[key] || ct || '—';
}

function formatAttrValue(raw) {
  if (!raw) return '来自估值参数模板';
  if (raw === 'configured_template') return '来自估值参数模板（内嵌参考值）';
  if (raw === 'configured_template:damodaran_ref') return '以Damodaran历史研究为参考，通过估值参数模板配置';
  if (raw === 'configured_template:stale_ref') return '来自估值参数模板（参考数据可能已过期）';
  if (raw === 'template_only') return '仅使用模板数据';
  if (raw.startsWith('damodaran_live:')) return `Damodaran ${raw.slice(15)} 风险溢价研究（实时）`;
  if (raw.startsWith('damodaran')) return `Damodaran 数据`;
  if (raw.startsWith('fred')) return `FRED ${raw.slice(5)}`;
  if (raw.startsWith('longbridge:')) return `Longbridge（${raw.slice(11)}）`;
  if (raw.startsWith('longbridge')) return 'Longbridge 实时报价';
  if (raw.startsWith('stooq')) return `Stooq ${raw.slice(6)}`;
  return raw;
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
  return String(str)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

function toCamelCase(s) {
  return s.replace(/_([a-z])/g, (_, c) => c.toUpperCase());
}
