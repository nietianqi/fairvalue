/**
 * us-stock-detail.js  â€”  US Stock Valuation Detail Page
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

// â”€â”€â”€ Globals â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

let TICKER = '';
let _historyLoaded = false;
let _peersLoaded   = false;

// â”€â”€â”€ Boot â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

document.addEventListener('DOMContentLoaded', () => {
  const params = new URLSearchParams(window.location.search);
  TICKER = (params.get('ticker') || '').toUpperCase().trim();

  if (!TICKER) {
    showFatalError('ç¼ºå°‘ ticker å‚æ•°ã€‚è¯·ä»Žæ¦œå•é¡µé€‰æ‹©è‚¡ç¥¨ã€‚');
    return;
  }

  document.title = `${TICKER} ä¼°å€¼è¯¦æƒ… â€” Fairvalue`;
  loadAll();
});

// â”€â”€â”€ Main Load Orchestrator â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

async function loadAll() {
  try {
    // Step 1: parallel â€” profile + summary + quality cards
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

    // Step 3: lazy â€” history + peers loaded on tab switch
  } catch (err) {
    showFatalError('æ•°æ®åŠ è½½å¤±è´¥ï¼š' + (err.message || 'æœªçŸ¥é”™è¯¯'));
  }
}

// â”€â”€â”€ Header â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

function renderHeader(profile, summary) {
  setText('hdr-ticker', TICKER);
  setText('hdr-name', profile?.companyName || profile?.name || 'â€”');

  const exchange = profile?.exchange || 'US';
  setText('hdr-exchange', exchange);

  const companyType = profile?.companyType || summary?.classification || '';
  const typeEl = document.getElementById('hdr-type');
  if (typeEl) {
    typeEl.textContent = formatCompanyType(companyType);
    typeEl.className = 'badge ' + verdictClass(companyType, 'type');
  }
}

// â”€â”€â”€ Hero Band â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

function renderHero(summary) {
  if (!summary) return;

  // Current price
  const price = summary.currentPrice;
  setText('hero-price', price != null ? fmtUSD(price) : 'â€”');

  // Fair value range
  const fvr = summary.fairValueRange || {};
  setText('hero-fv-low',  fvr.low  != null ? fmtUSD(fvr.low)  : 'â€”');
  setText('hero-fv-mid',  fvr.mid  != null ? fmtUSD(fvr.mid)  : 'â€”');
  setText('hero-fv-high', fvr.high != null ? fmtUSD(fvr.high) : 'â€”');

  // Upside
  const upside = summary.upsideDownside;
  const upsideEl = document.getElementById('hero-upside');
  if (upsideEl && upside != null) {
    const pct = (upside * 100).toFixed(1);
    upsideEl.textContent = (upside >= 0 ? 'â–² ' : 'â–¼ ') + Math.abs(pct) + '%';
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
  const conf = summary.confidenceLevel;  // 0.0â€“1.0
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
    el.textContent = `${fmtUSD(zone.low)} â€“ ${fmtUSD(zone.high)}`;
  } else if (zone.low != null) {
    el.textContent = `â‰¤ ${fmtUSD(zone.low)}`;
  } else if (zone.high != null) {
    el.textContent = `â‰¥ ${fmtUSD(zone.high)}`;
  }
}

// â”€â”€â”€ Tab: Overview â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

function renderOverview(profile, dataQuality, finQuality) {
  renderProfileCard(profile);
  renderDataQualityCard(dataQuality);
  renderFinQualityCard(finQuality);
}

function renderProfileCard(profile) {
  const el = document.getElementById('profile-rows');
  if (!el) return;
  if (!profile) { el.innerHTML = '<div class="empty-state">æš‚æ— æ•°æ®</div>'; return; }

  const rows = [
    ['äº¤æ˜“æ‰€',    profile.exchange || 'â€”'],
    ['è¡Œä¸š',      profile.sector   || 'â€”'],
    ['å­è¡Œä¸š',    profile.industry || 'â€”'],
    ['å¸‚å€¼',      profile.marketCap != null ? fmtMarketCap(profile.marketCap) : 'â€”'],
    ['å…¬å¸ç±»åž‹',  formatCompanyType(profile.companyType || '')],
    ['ä¼°å€¼æ¨¡æ¿',  profile.sectorTemplate || 'â€”'],
    ['æ•°æ®ç‰ˆæœ¬',  profile.dataVersion || profile.data_version || 'â€”'],
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
  if (!dq) { el.innerHTML = '<div class="empty-state">æš‚æ— æ•°æ®</div>'; return; }

  const conf = dq.confidenceLevel != null ? Math.round(dq.confidenceLevel * 100) : null;
  const missingItems = dq.missingItems || dq.missing_items || [];
  const warnings     = dq.warningFlags || dq.warning_flags || [];
  const filingDate   = dq.latestFilingDate || dq.latest_filing_date || null;

  let html = '';
  if (conf != null) {
    html += `<div class="confidence-wrap" style="margin-bottom:12px">
      <span class="confidence-label">ç½®ä¿¡åº¦</span>
      <div class="confidence-bar">
        <div class="confidence-fill" style="width:${conf}%"></div>
      </div>
      <span class="confidence-pct">${conf}%</span>
    </div>`;
  }
  if (filingDate) {
    html += `<div class="profile-row"><span class="profile-label">æœ€æ–°è´¢æŠ¥æ—¥æœŸ</span><span class="profile-value">${escHtml(filingDate)}</span></div>`;
  }
  if (missingItems.length) {
    html += `<div style="margin-top:10px"><div style="font-size:12px;color:var(--muted);margin-bottom:4px">ç¼ºå¤±é¡¹</div>`;
    html += missingItems.map(m => `<div class="tag tag-warn">${escHtml(String(m))}</div>`).join('');
    html += '</div>';
  }
  if (warnings.length) {
    html += `<div style="margin-top:10px"><div style="font-size:12px;color:var(--muted);margin-bottom:4px">è­¦å‘Š</div>`;
    html += warnings.map(w => `<div class="tag tag-bad">${escHtml(String(w))}</div>`).join('');
    html += '</div>';
  }
  if (!html) html = '<div class="empty-state">æ•°æ®è´¨é‡æ­£å¸¸</div>';
  el.innerHTML = html;
}

function renderFinQualityCard(fq) {
  const el = document.getElementById('fin-quality-section');
  if (!el) return;
  if (!fq) { el.innerHTML = '<div class="empty-state">æš‚æ— æ•°æ®</div>'; return; }

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
      <div style="font-size:12px;color:var(--muted)">æ€»è´¢åŠ¡è´¨é‡è¯„åˆ†</div>
    </div>`;
  }
  const scoreRows = [
    ['ç›ˆåˆ©è´¨é‡', earnings],
    ['æ”¶å…¥è´¨é‡', revenue],
    ['èµ„æœ¬æ•ˆçŽ‡', capital],
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
    html += `<div style="margin-top:10px"><div style="font-size:12px;color:var(--muted);margin-bottom:4px">çº¢æ——è­¦ç¤º</div>`;
    html += redFlags.map(f => `<div class="tag tag-bad">${escHtml(String(f))}</div>`).join('');
    html += '</div>';
  }
  if (!html) html = '<div class="empty-state">æš‚æ— æ•°æ®</div>';
  el.innerHTML = html;
}

// â”€â”€â”€ Tab: Valuation â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

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
  renderWarnings(summary, report);

  // Explanation accordion â€” from report.explanation.blocks or report.explanationBlocks
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
    const methodStatus = m.methodStatus || m.method_status || 'active';
    const outlierTrimmed = Boolean(m.outlierTrimmed ?? m.outlier_trimmed);
    const weightAdjusted = Boolean(m.weightAdjustedByDataQuality ?? m.weight_adjusted_by_data_quality);
    return `<tr>
      <td><strong>${escHtml(methodLabel(m.method || ''))}</strong></td>
      <td>${escHtml(methodStatus)}</td>
      <td>${outlierTrimmed ? '是' : '否'}</td>
      <td>${weightAdjusted ? '是' : '否'}</td>
      <td>${weight}</td>
      <td>${fmtUSDOrDash(m.bearValue)}</td>
      <td>${fmtUSDOrDash(m.baseValue)}</td>
      <td>${fmtUSDOrDash(m.bullValue)}</td>
      <td style="color:var(--muted);font-size:12px">${escHtml(m.rationale || '—')}</td>
    </tr>`;
  }).join('');

  el.innerHTML = `<table class="data-table">
    <thead><tr>
      <th>方法</th><th>状态</th><th>剪裁</th><th>质量调权</th><th>权重</th><th>悲观</th><th>基准</th><th>乐观</th><th>备注</th>
    </tr></thead>
    <tbody>${rows}</tbody>
  </table>`;
}

function renderScenarioMatrix(scenarios) {
  const el = document.getElementById('scenario-grid');
  if (!el) return;
  if (!scenarios.length) {
    el.innerHTML = '<div class="empty-state">æš‚æ— æƒ…æ™¯æ•°æ®</div>';
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
    const prob  = s.probability != null ? (s.probability * 100).toFixed(0) + '%' : 'â€”';
    const price = fmtUSDOrDash(s.targetPrice);
    const up    = s.upside != null ? (s.upside * 100).toFixed(1) + '%' : 'â€”';
    const upCls = s.upside != null ? (s.upside >= 0 ? 'up' : 'down') : '';

    // Key assumptions from fairValueLow/High or generic fields
    const low  = fmtUSDOrDash(s.fairValueLow);
    const high = fmtUSDOrDash(s.fairValueHigh);
    const range = (s.fairValueLow != null && s.fairValueHigh != null)
      ? `<div class="scenario-assumption">å…¬å…åŒºé—´ ${low} â€“ ${high}</div>` : '';

    return `<div class="scenario-card ${key}">
      <div class="scenario-label">${label}</div>
      <div class="scenario-prob">æ¦‚çŽ‡ ${prob}</div>
      <div class="scenario-price">${price}</div>
      <div class="scenario-upside ${upCls}">${s.upside != null ? (s.upside >= 0 ? 'â–²' : 'â–¼') + ' ' + Math.abs((s.upside * 100).toFixed(1)) + '%' : 'â€”'}</div>
      ${range}
    </div>`;
  }).join('');
}

function renderSourceAttribution(attr) {
  const el = document.getElementById('attr-grid');
  if (!el) return;

  const displayRules = {
    price_source: '价格来源',
    price_source_type: '价格类型',
    price_as_of: '价格日期',
    price_freshness_days: '价格鲜度',
    risk_free_rate_source: '无风险利率',
    erp_source: 'ERP来源',
    beta_source: 'Beta来源',
    industry_multiple_source: '行业倍数来源',
    industry_match_source: '行业匹配来源',
    industry_match_confidence: '行业匹配置信',
    industry_fallback_used: '行业回退',
    market_multiple_source: '市场倍数来源',
    peer_set_source: 'Peer集合来源',
    peer_selection_basis: 'Peer筛选依据',
    relative_source_mode: '相对估值模式',
    data_version: '数据版本',
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

  const peerTickers = attr.peer_set_tickers || attr.peerSetTickers || [];
  if (peerTickers.length) {
    html += `<div class="attr-item" style="grid-column:1/-1">
      <div class="attr-label">Peer 股票池</div>
      <div class="attr-value">${peerTickers.map(t => `<span class="badge badge-neutral" style="margin:2px">${escHtml(t)}</span>`).join('')}</div>
    </div>`;
  }

  el.innerHTML = html || '<div class="empty-state">暂无来源归因数据</div>';
}

function renderWarnings(summary, report) {
  const stack = document.getElementById('hero-warning-stack');
  if (!stack) return;

  const decision = report?.decision || {};
  const attr = decision.sourceAttribution || decision.source_attribution || {};
  const breakdown = report?.valuationBreakdown || report?.valuation_breakdown || [];
  const warnings = [];

  if (summary?.priceSourceType === 'research_fallback') {
    warnings.push({
      cls: 'hero-warning-bad',
      text: '当前价格来自 research fallback，这份结论更适合研究参考，不会进入严格可交易排名。',
    });
  }

  if (summary?.rankable === false) {
    warnings.push({
      cls: 'hero-warning-warn',
      text: `这只股票当前不参与严格榜单排名${summary?.exclusionReason ? `，原因：${summary.exclusionReason}` : ''}。`,
    });
  }

  const industryFallbackUsed = attr.industryFallbackUsed ?? attr.industry_fallback_used;
  if (industryFallbackUsed === true) {
    warnings.push({
      cls: 'hero-warning-warn',
      text: '行业匹配已经使用 fallback 路径，相对估值参数可能比平时更保守。',
    });
  }

  const trimmedMethods = breakdown.filter(item => Boolean(item.outlierTrimmed ?? item.outlier_trimmed));
  if (trimmedMethods.length) {
    warnings.push({
      cls: 'hero-warning-warn',
      text: `估值护栏已对 ${trimmedMethods.map(item => methodLabel(item.method || '')).join('、')} 做异常剪裁，用来防止坏输入抬高公允价值。`,
    });
  }

  stack.innerHTML = warnings.map(warning =>
    `<div class="hero-warning ${warning.cls}">${escHtml(warning.text)}</div>`
  ).join('');
}

// â”€â”€â”€ Tab: History (SVG Chart) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

async function loadHistory() {
  if (_historyLoaded) return;
  _historyLoaded = true;

  const wrap = document.getElementById('chart-wrap');
  if (!wrap) return;
  wrap.innerHTML = '<div class="empty-state">åŠ è½½ä¸­â€¦</div>';

  try {
    const data = await apiFetch(`/v1/valuation/history/US/${TICKER}?days=90`);
    const points = data?.points || data || [];
    if (!points.length) {
      wrap.innerHTML = '<div class="empty-state">æš‚æ— åŽ†å²æ•°æ®</div>';
      return;
    }
    renderHistoryChart(points, wrap);
  } catch (e) {
    wrap.innerHTML = `<div class="empty-state">åŽ†å²æ•°æ®åŠ è½½å¤±è´¥ï¼š${escHtml(e.message)}</div>`;
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
    container.innerHTML = '<div class="empty-state">æš‚æ— æœ‰æ•ˆåŽ†å²æ•°æ®</div>';
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

  // X-axis labels â€” pick ~5 evenly spaced dates
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

  // Hover overlay â€” invisible rects triggering tooltip
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
    chartNote.textContent = latest.runDate ? `æœ€è¿‘ä¼°å€¼æ—¥æœŸï¼š${latest.runDate}` : '';
  }

  // Attach tooltip events
  container.querySelectorAll('.chart-hover-rect').forEach(rect => {
    rect.addEventListener('mouseenter', e => {
      if (!tooltip) return;
      const d    = rect.dataset.date  || '';
      const cl   = rect.dataset.close || 'â€”';
      const fv   = rect.dataset.fv    || 'â€”';
      const rd   = rect.dataset.rd    || '';
      tooltip.innerHTML = `
        <div class="tt-date">${d}</div>
        <div class="tt-row"><span class="tt-label">æ”¶ç›˜ä»·</span><span class="tt-val">$${cl}</span></div>
        <div class="tt-row"><span class="tt-label">å…¬å…ä»·å€¼</span><span class="tt-val">${fv !== '' ? '$' + fv : 'â€”'}</span></div>
        ${rd ? `<div class="tt-row" style="font-size:11px;color:var(--muted)">ä¼°å€¼æ—¥ ${rd}</div>` : ''}
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

// â”€â”€â”€ Tab: Peers â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

async function loadPeers() {
  if (_peersLoaded) return;
  _peersLoaded = true;

  const metaEl  = document.getElementById('peers-meta');
  const tableEl = document.getElementById('peers-table-wrap');
  if (!metaEl || !tableEl) return;

  metaEl.innerHTML  = '<div class="empty-state">åŠ è½½ä¸­â€¦</div>';
  tableEl.innerHTML = '<div class="empty-state">åŠ è½½ä¸­â€¦</div>';

  try {
    const peers = await apiFetch(`/v1/peers/US/${TICKER}?limit=8`);
    renderPeersMeta(peers, metaEl);
    renderPeersTable(peers?.items || [], tableEl);
  } catch (e) {
    tableEl.innerHTML = `<div class="empty-state">åŒè¡Œæ•°æ®åŠ è½½å¤±è´¥ï¼š${escHtml(e.message)}</div>`;
  }
}

function renderPeersMeta(peers, el) {
  if (!peers) { el.innerHTML = ''; return; }
  const parts = [];
  if (peers.selectionBasis || peers.selection_basis) parts.push(`ç­›é€‰ä¾æ®ï¼š${peers.selectionBasis || peers.selection_basis}`);
  if (peers.sourceMode      || peers.source_mode)     parts.push(`æ¥æºæ¨¡å¼ï¼š${peers.sourceMode || peers.source_mode}`);
  const peerCandidateCount = peers.peerCandidateCount ?? peers.peer_candidate_count;
  if (peerCandidateCount != null)                      parts.push(`å€™é€‰æ•°é‡ï¼š${peerCandidateCount}`);
  if (peers.peerSelectionRuleVersion || peers.peer_selection_rule_version)
    parts.push(`è§„åˆ™ç‰ˆæœ¬ï¼š${peers.peerSelectionRuleVersion || peers.peer_selection_rule_version}`);
  el.innerHTML = parts.length
    ? `<div class="peers-meta-text">${parts.map(escHtml).join('ã€€|ã€€')}</div>`
    : '';
}

function renderPeersTable(items, el) {
  if (!items.length) {
    el.innerHTML = '<div class="empty-state">æš‚æ— åŒè¡Œæ•°æ®</div>';
    return;
  }

  const rows = items.map(p => {
    const up   = p.upside != null ? ((p.upside * 100).toFixed(1) + '%') : 'â€”';
    const upCls = p.upside != null ? (p.upside >= 0 ? 'num-good' : 'num-bad') : '';
    return `<tr>
      <td><a href="/us-stock-detail.html?ticker=${escHtml(p.ticker || '')}" class="ticker-link">${escHtml(p.ticker || 'â€”')}</a></td>
      <td>${escHtml(p.companyName || p.company_name || 'â€”')}</td>
      <td>${fmtUSDOrDash(p.currentPrice || p.current_price)}</td>
      <td>${fmtUSDOrDash(p.fairValue || p.fair_value)}</td>
      <td class="${upCls}">${up}</td>
      <td>${p.pe != null ? p.pe.toFixed(1) + 'Ã—' : 'â€”'}</td>
      <td>${p.evEbitda != null ? p.evEbitda.toFixed(1) + 'Ã—' : (p.ev_ebitda != null ? p.ev_ebitda.toFixed(1) + 'Ã—' : 'â€”')}</td>
      <td>${p.roic != null ? (p.roic * 100).toFixed(1) + '%' : 'â€”'}</td>
      <td>${p.fcfMargin != null ? (p.fcfMargin * 100).toFixed(1) + '%' : (p.fcf_margin != null ? (p.fcf_margin * 100).toFixed(1) + '%' : 'â€”')}</td>
    </tr>`;
  }).join('');

  el.innerHTML = `<table class="data-table">
    <thead><tr>
      <th>è‚¡ç¥¨ä»£ç </th><th>å…¬å¸</th><th>çŽ°ä»·</th><th>å…¬å…ä»·å€¼</th><th>ä¸Šè¡Œç©ºé—´</th>
      <th>PE</th><th>EV/EBITDA</th><th>ROIC</th><th>FCFåˆ©æ¶¦çŽ‡</th>
    </tr></thead>
    <tbody>${rows}</tbody>
  </table>`;
}

// â”€â”€â”€ Tab: Risk â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

function renderRiskMatrix(items) {
  const el = document.getElementById('risk-table-wrap');
  if (!el) return;
  if (!items.length) {
    el.innerHTML = '<div class="empty-state">æš‚æ— é£Žé™©æ•°æ®</div>';
    return;
  }

  const rows = items.map(r => {
    const probLabel   = r.probability || 'â€”';
    const impactLabel = r.impact      || 'â€”';
    const adjType     = r.adjustmentType || r.adjustment_type || 'â€”';
    const adj         = r.adjustment  || 'â€”';
    return `<tr>
      <td>${escHtml(riskLabel(r.riskType || r.risk_type || ''))}</td>
      <td><span class="risk-level risk-${(r.probability || '').toLowerCase()}">${escHtml(probLabel)}</span></td>
      <td><span class="risk-level risk-${(r.impact || '').toLowerCase()}">${escHtml(impactLabel)}</span></td>
      <td>${escHtml(adjType)}</td>
      <td>${escHtml(String(adj))}</td>
      <td style="font-size:12px;color:var(--muted)">${escHtml(r.note || 'â€”')}</td>
    </tr>`;
  }).join('');

  el.innerHTML = `<table class="data-table">
    <thead><tr>
      <th>é£Žé™©ç±»åž‹</th><th>æ¦‚çŽ‡</th><th>å½±å“</th><th>è°ƒæ•´ç±»åž‹</th><th>è°ƒæ•´å€¼</th><th>å¤‡æ³¨</th>
    </tr></thead>
    <tbody>${rows}</tbody>
  </table>`;
}

// â”€â”€â”€ Explanation Blocks (Accordion) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

function renderExplanationBlocks(blocks) {
  const el = document.getElementById('explanation-accordion');
  if (!el) return;
  if (!blocks.length) {
    el.innerHTML = '<div class="empty-state">æš‚æ— è§£é‡Šæ•°æ®</div>';
    return;
  }

  const sorted = [...blocks].sort((a, b) => (a.displayOrder || 0) - (b.displayOrder || 0));

  el.innerHTML = sorted.map((b, idx) => {
    const id = `acc-${idx}`;
    return `<div class="accordion-item">
      <button class="accordion-header" onclick="toggleAccordion('${id}')">
        <span class="accordion-title">${escHtml(b.title || b.key || '')}</span>
        <span class="accordion-arrow" id="arr-${id}">â–¶</span>
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
  if (arr) arr.textContent = open ? 'â–¶' : 'â–¼';
}

// â”€â”€â”€ Source Attribution Toggle â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

function toggleAttr() {
  const body   = document.getElementById('attr-body');
  const toggle = document.getElementById('attr-toggle');
  if (!body || !toggle) return;
  const open = body.classList.contains('open');
  body.classList.toggle('open', !open);
  const arrow = toggle.querySelector('.arrow');
  if (arrow) arrow.textContent = open ? 'â–¶' : 'â–¼';
}

// â”€â”€â”€ Run Valuation â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

async function runValuation() {
  const btn = document.getElementById('btn-run');
  if (!btn) return;
  btn.disabled = true;
  btn.textContent = 'âŸ³ ä¼°å€¼ä¸­â€¦';

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
    showToast('âœ“ é‡æ–°ä¼°å€¼å®Œæˆï¼Œæ­£åœ¨åˆ·æ–°â€¦');
    // Reload page data
    _historyLoaded = false;
    _peersLoaded   = false;
    hide('main');
    show('loading');
    setTimeout(() => loadAll(), 600);
  } catch (e) {
    showToast('âœ— ä¼°å€¼å¤±è´¥ï¼š' + (e.message || 'æœªçŸ¥é”™è¯¯'), 'error');
    btn.disabled = false;
    btn.textContent = 'âŸ³ é‡æ–°ä¼°å€¼';
  }
}

// â”€â”€â”€ Tab Switching â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

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

// â”€â”€â”€ Toast â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

function showToast(msg, type = 'success') {
  const el = document.getElementById('toast');
  if (!el) return;
  el.textContent = msg;
  el.className = 'toast ' + (type === 'error' ? 'toast-error' : 'toast-success');
  el.classList.add('visible');
  setTimeout(() => el.classList.remove('visible'), 3500);
}

// â”€â”€â”€ Fatal Error â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

function showFatalError(msg) {
  hide('loading');
  const app = document.getElementById('app');
  if (app) {
    app.innerHTML = `<div style="display:flex;flex-direction:column;align-items:center;justify-content:center;min-height:60vh;gap:16px">
      <div style="font-size:48px">âš ï¸</div>
      <div style="font-size:18px;color:var(--bad);font-weight:700">${escHtml(msg)}</div>
      <a href="/cn-undervalued-stocks.html" style="color:var(--blue)">â† è¿”å›žæ¦œå•</a>
    </div>`;
  }
}

// â”€â”€â”€ API Fetch Helper â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

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
    throw new Error(`HTTP ${res.status}${detail ? 'ï¼š' + detail : ''}`);
  }
  const data = unwrapApiEnvelope(await res.json());
  // Normalize snake_case keys (Jackson SNAKE_CASE strategy) â†’ camelCase for uniform JS access
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
 * e.g. { fair_value_range: { low: 1 } } â†’ { fairValueRange: { low: 1 } }
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

// â”€â”€â”€ Format Helpers â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

function fmtUSD(v) {
  if (v == null) return 'â€”';
  if (v >= 1000) return '$' + v.toLocaleString('en-US', { minimumFractionDigits: 0, maximumFractionDigits: 0 });
  if (v >= 100)  return '$' + v.toFixed(1);
  return '$' + v.toFixed(2);
}

function fmtUSDOrDash(v) {
  return v != null ? fmtUSD(v) : 'â€”';
}

function fmtMarketCap(v) {
  if (v == null) return 'â€”';
  if (v >= 1e12) return '$' + (v / 1e12).toFixed(2) + 'T';
  if (v >= 1e9)  return '$' + (v / 1e9).toFixed(1)  + 'B';
  if (v >= 1e6)  return '$' + (v / 1e6).toFixed(1)  + 'M';
  return '$' + v.toLocaleString();
}

function verdictLabel(v) {
  const map = {
    UNDERVALUED: 'ä½Žä¼°', FAIRLY_VALUED: 'åˆç†', OVERVALUED: 'é«˜ä¼°',
    undervalued: 'ä½Žä¼°', fairly_valued: 'åˆç†', overvalued: 'é«˜ä¼°',
    FAIR: 'åˆç†', fair: 'åˆç†',
  };
  return map[v] || v || 'â€”';
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
    reverse_dcf:          'åå‘DCF',
    relative_valuation:   'ç›¸å¯¹ä¼°å€¼',
    historical_multiple:  'åŽ†å²å€æ•°',
  };
  return map[m.toLowerCase().replace(/ /g, '_')] || m;
}

function scenarioLabel(s) {
  const map = { bear: 'æ‚²è§‚', base: 'åŸºå‡†', bull: 'ä¹è§‚' };
  return map[(s || '').toLowerCase()] || s;
}

function riskLabel(r) {
  const map = {
    earnings_miss:            'ç›ˆåˆ©æœªè¾¾é¢„æœŸ',
    multiple_compression:     'ä¼°å€¼åŽ‹ç¼©',
    balance_sheet:            'èµ„äº§è´Ÿå€ºè¡¨é£Žé™©',
    liquidity:                'æµåŠ¨æ€§é£Žé™©',
    regulatory:               'ç›‘ç®¡é£Žé™©',
    competition:              'ç«žäº‰åŠ å‰§',
    macro:                    'å®è§‚ç»æµŽ',
    interest_rate:            'åˆ©çŽ‡é£Žé™©',
    currency:                 'æ±‡çŽ‡é£Žé™©',
    execution:                'æ‰§è¡Œé£Žé™©',
  };
  return map[r.toLowerCase().replace(/ /g, '_')] || r;
}

function formatCompanyType(ct) {
  const map = {
    compounder:       'å¤åˆ©å¢žé•¿åž‹',
    income_defensive: 'æ”¶æ¯é˜²å¾¡åž‹',
    growth:           'æˆé•¿åž‹',
    cyclical:         'å‘¨æœŸåž‹',
    turnaround:       'è½¬åž‹ä¿®å¤åž‹',
    general_quality:  'ç»¼åˆè´¨é‡åž‹',
  };
  const key = (ct || '').split('/')[0].trim().toLowerCase().replace(/ /g, '_');
  return map[key] || ct || 'â€”';
}

function formatAttrValue(raw) {
  if (!raw) return 'æ¥è‡ªä¼°å€¼å‚æ•°æ¨¡æ¿';
  if (raw === 'configured_template') return 'æ¥è‡ªä¼°å€¼å‚æ•°æ¨¡æ¿ï¼ˆå†…åµŒå‚è€ƒå€¼ï¼‰';
  if (raw === 'configured_template:damodaran_ref') return 'ä»¥DamodaranåŽ†å²ç ”ç©¶ä¸ºå‚è€ƒï¼Œé€šè¿‡ä¼°å€¼å‚æ•°æ¨¡æ¿é…ç½®';
  if (raw === 'configured_template:stale_ref') return 'æ¥è‡ªä¼°å€¼å‚æ•°æ¨¡æ¿ï¼ˆå‚è€ƒæ•°æ®å¯èƒ½å·²è¿‡æœŸï¼‰';
  if (raw === 'template_only') return 'ä»…ä½¿ç”¨æ¨¡æ¿æ•°æ®';
  if (raw.startsWith('damodaran_live:')) return `Damodaran ${raw.slice(15)} é£Žé™©æº¢ä»·ç ”ç©¶ï¼ˆå®žæ—¶ï¼‰`;
  if (raw.startsWith('damodaran')) return `Damodaran æ•°æ®`;
  if (raw.startsWith('fred')) return `FRED ${raw.slice(5)}`;
  if (raw.startsWith('longbridge:')) return `Longbridgeï¼ˆ${raw.slice(11)}ï¼‰`;
  if (raw.startsWith('longbridge')) return 'Longbridge å®žæ—¶æŠ¥ä»·';
  if (raw.startsWith('stooq')) return `Stooq ${raw.slice(6)}`;
  return raw;
}

// â”€â”€â”€ DOM Helpers â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

function setText(id, text) {
  const el = document.getElementById(id);
  if (el) el.textContent = text ?? 'â€”';
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

