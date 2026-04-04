/**
 * jp-stock-detail.js – JP Stock Valuation Detail Page
 *
 * API surface:
 *   GET /v1/jp-equities/{code}/overview
 *   GET /v1/jp-equities/{code}/fair-value
 *   GET /v1/jp-equities/{code}/history?days=365
 */
'use strict';

let CODE = '';
let _historyLoaded = false;

// ─── Boot ────────────────────────────────────────────────────────────────────

document.addEventListener('DOMContentLoaded', () => {
  const params = new URLSearchParams(window.location.search);
  CODE = (params.get('code') || '').trim();
  if (!CODE) {
    showFatalError('缺少 code 参数。请从筛选器页面选择股票。');
    return;
  }
  document.title = `${CODE} 日本株估值 – Fairvalue`;
  loadAll();
});

// ─── Main Load ───────────────────────────────────────────────────────────────

async function loadAll() {
  try {
    const [overview, fv] = await Promise.all([
      apiFetch(`/v1/jp-equities/${CODE}/overview`),
      apiFetch(`/v1/jp-equities/${CODE}/fair-value`),
    ]);

    renderHeader(overview, fv);
    renderHero(overview, fv);
    renderOverview(fv);
    renderValuation(fv);
    renderRisk(fv);

    show('main');
    hide('loading');
  } catch (err) {
    showFatalError('数据加载失败：' + (err.message || '未知错误'));
  }
}

// ─── Header ──────────────────────────────────────────────────────────────────

function renderHeader(overview, fv) {
  setText('hdr-code', CODE);
  setText('hdr-name', overview?.companyName || fv?.companyName || '—');

  const marketEl = document.getElementById('hdr-market');
  if (marketEl) marketEl.textContent = overview?.market || 'JP';

  const industryEl = document.getElementById('hdr-industry');
  if (industryEl) {
    industryEl.textContent = fv?.industry || overview?.market || '—';
    industryEl.className = 'badge badge-neutral';
  }
}

// ─── Hero ─────────────────────────────────────────────────────────────────────

function renderHero(overview, fv) {
  const price = overview?.currentPrice ?? fv?.currentPrice;
  setText('hero-price', fmtJPY(price));

  const low  = overview?.fairValueLow  ?? fv?.fairValueLow;
  const mid  = overview?.fairValueMid  ?? fv?.fairValueMid ?? fv?.fairValueBase;
  const high = overview?.fairValueHigh ?? fv?.fairValueHigh;
  setText('hero-fv-low',  fmtJPY(low));
  setText('hero-fv-mid',  fmtJPY(mid));
  setText('hero-fv-high', fmtJPY(high));

  const upside = overview?.upsideDownsidePct ?? fv?.upsideDownsidePct;
  const upsideEl = document.getElementById('hero-upside');
  if (upsideEl && upside != null) {
    const pct = (upside * 100).toFixed(1);
    upsideEl.textContent = (upside >= 0 ? '▲ ' : '▼ ') + Math.abs(pct) + '%';
    upsideEl.className = 'upside-value ' + (upside >= 0 ? 'up' : 'down');
  }

  const label = overview?.valuationLabel || fv?.valuationLabel || '';
  const verdictEl = document.getElementById('hero-verdict');
  if (verdictEl) {
    verdictEl.textContent = verdictLabel(label);
    verdictEl.className = 'verdict-badge ' + verdictClass(label);
  }

  // Confidence bar
  const confRaw = fv?.confidenceScore;
  const confPct = confRaw != null ? confRaw : parseConfLevel(overview?.confidenceLevel ?? fv?.confidenceLevel);
  if (confPct != null) {
    const fill = document.getElementById('hero-conf-fill');
    if (fill) fill.style.width = confPct + '%';
    setText('hero-conf-pct', confPct + '%');
  }

  setText('hero-data-version', fv?.dataVersion || '—');

  // Warning stack
  renderWarnings(fv);
}

function parseConfLevel(level) {
  if (level == null) return null;
  const map = { HIGH: 85, MEDIUM: 65, LOW: 40, VERY_LOW: 25, high: 85, medium: 65, low: 40, very_low: 25 };
  return map[level] ?? null;
}

function renderWarnings(fv) {
  const stack = document.getElementById('hero-warning-stack');
  if (!stack) return;
  const warnings = [];
  if (fv?.confidenceLevel === 'LOW' || fv?.confidenceLevel === 'VERY_LOW' || fv?.confidenceScore < 50) {
    warnings.push({ cls: 'warn', msg: '置信度较低，估值仅供参考' });
  }
  if (fv?.dataVersion && fv.dataVersion.includes('fallback')) {
    warnings.push({ cls: 'warn', msg: '价格数据为 fallback，非实时市价' });
  }
  stack.innerHTML = warnings.map(w =>
    `<div class="hero-warning hero-warning-${w.cls}">${escHtml(w.msg)}</div>`
  ).join('');
}

// ─── Overview Tab ─────────────────────────────────────────────────────────────

function renderOverview(fv) {
  // Company profile
  const profileEl = document.getElementById('profile-rows');
  if (profileEl && fv) {
    const rows = [
      ['证券代码', CODE],
      ['公司名称', fv.companyName || '—'],
      ['市场', fv.market || '—'],
      ['行业', fv.industry || '—'],
      ['货币', fv.currency || 'JPY'],
      ['当前股价', fmtJPY(fv.currentPrice)],
      ['安全边际', fv.marginOfSafety != null ? (fv.marginOfSafety * 100).toFixed(1) + '%' : '—'],
    ];
    profileEl.innerHTML = rows.map(([k, v]) =>
      `<div class="profile-row"><span class="profile-key">${escHtml(k)}</span><span class="profile-val">${escHtml(String(v))}</span></div>`
    ).join('');
  }

  // Assumptions
  const assumpEl = document.getElementById('assumptions-section');
  if (assumpEl && fv?.assumptions) {
    const entries = Object.entries(fv.assumptions);
    if (entries.length) {
      assumpEl.innerHTML = entries.map(([k, v]) =>
        `<div class="profile-row"><span class="profile-key">${escHtml(k)}</span><span class="profile-val">${typeof v === 'number' ? (v * 100).toFixed(2) + '%' : escHtml(String(v))}</span></div>`
      ).join('');
    } else {
      assumpEl.innerHTML = '<div class="empty-state">暂无参数数据</div>';
    }
  }

  // Weights
  const weightsEl = document.getElementById('weights-section');
  if (weightsEl && fv?.weights) {
    const entries = Object.entries(fv.weights);
    if (entries.length) {
      weightsEl.innerHTML = entries.map(([k, v]) =>
        `<div class="profile-row"><span class="profile-key">${escHtml(k)}</span><span class="profile-val">${(v * 100).toFixed(0)}%</span></div>`
      ).join('');
    } else {
      weightsEl.innerHTML = '<div class="empty-state">暂无权重数据</div>';
    }
  }
}

// ─── Valuation Tab ────────────────────────────────────────────────────────────

function renderValuation(fv) {
  // Method table
  const methodWrap = document.getElementById('method-table-wrap');
  if (methodWrap && fv?.methodResults?.length) {
    const rows = fv.methodResults.map(m => `
      <tr>
        <td>${escHtml(m.method || '—')}</td>
        <td class="num">${fmtJPY(m.value)}</td>
        <td class="num">${m.weight != null ? (m.weight * 100).toFixed(0) + '%' : '—'}</td>
        <td style="color:var(--muted);font-size:12px">${escHtml(m.rationale || '')}</td>
      </tr>`).join('');
    methodWrap.innerHTML = `
      <table class="data-table">
        <thead><tr><th>方法</th><th>公允价值</th><th>权重</th><th>说明</th></tr></thead>
        <tbody>${rows}</tbody>
      </table>`;
  } else if (methodWrap) {
    methodWrap.innerHTML = '<div class="empty-state">暂无方法分解数据</div>';
  }

  // Scenarios
  const scenGrid = document.getElementById('scenario-grid');
  if (scenGrid && fv?.scenarios?.length) {
    scenGrid.innerHTML = fv.scenarios.map(s => `
      <div class="scenario-card scenario-${(s.scenario || '').toLowerCase()}">
        <div class="scenario-label">${escHtml(s.scenario || '—')}</div>
        <div class="scenario-fv">${fmtJPY(s.fairValueBase ?? s.value)}</div>
        <div class="scenario-prob">${s.probability != null ? (s.probability * 100).toFixed(0) + '%' : '—'}</div>
      </div>`).join('');
  } else if (scenGrid) {
    scenGrid.innerHTML = '<div class="empty-state">暂无情景数据</div>';
  }
}

// ─── Risk Tab ─────────────────────────────────────────────────────────────────

function renderRisk(fv) {
  // Risk flags table
  const riskWrap = document.getElementById('risk-table-wrap');
  if (riskWrap && fv?.riskFlags?.length) {
    const rows = fv.riskFlags.map(r => `
      <tr>
        <td><span class="risk-level risk-${(r.level || '').toLowerCase()}">${escHtml(r.level || '—')}</span></td>
        <td>${escHtml(r.category || '—')}</td>
        <td style="color:var(--muted)">${escHtml(r.description || '')}</td>
      </tr>`).join('');
    riskWrap.innerHTML = `
      <table class="data-table">
        <thead><tr><th>级别</th><th>类别</th><th>描述</th></tr></thead>
        <tbody>${rows}</tbody>
      </table>`;
  } else if (riskWrap) {
    riskWrap.innerHTML = '<div class="empty-state">暂无风险标志数据</div>';
  }

  // Catalysts
  const catalystsEl = document.getElementById('catalysts-section');
  if (catalystsEl && fv?.catalystFlags?.length) {
    catalystsEl.innerHTML = fv.catalystFlags.map(c => `
      <div class="card" style="margin-bottom:8px;padding:12px 16px">
        <div style="display:flex;align-items:center;gap:8px;margin-bottom:4px">
          <span class="badge badge-neutral">${escHtml(c.type || '—')}</span>
          <span style="color:var(--muted);font-size:12px">${escHtml(c.timeframe || '')}</span>
        </div>
        <div style="font-size:13px">${escHtml(c.description || '')}</div>
      </div>`).join('');
  } else if (catalystsEl) {
    catalystsEl.innerHTML = '<div class="empty-state">暂无催化剂数据</div>';
  }
}

// ─── History Tab (lazy) ───────────────────────────────────────────────────────

async function loadHistory() {
  if (_historyLoaded) return;
  _historyLoaded = true;
  const wrap = document.getElementById('chart-wrap');
  const note = document.getElementById('chart-note');
  try {
    const data = await apiFetch(`/v1/jp-equities/${CODE}/history?days=365`);
    const pts = data?.history || [];
    if (!pts.length) {
      if (wrap) wrap.innerHTML = '<div class="empty-state">暂无历史数据</div>';
      return;
    }
    renderChart(pts, wrap);
    const simulated = pts.some(p => p.simulated);
    if (note && simulated) note.textContent = '* 历史公允价值为模型合成数据，非实际运行记录';
  } catch (err) {
    if (wrap) wrap.innerHTML = `<div class="empty-state">历史数据加载失败：${escHtml(err.message)}</div>`;
  }
}

function renderChart(pts, container) {
  const W = container.clientWidth || 760;
  const H = 260;
  const PAD = { top: 20, right: 20, bottom: 36, left: 64 };
  const iW = W - PAD.left - PAD.right;
  const iH = H - PAD.top - PAD.bottom;

  const prices = pts.map(p => p.closePrice).filter(v => v > 0);
  const fvs    = pts.map(p => p.fairValueMid).filter(v => v > 0);
  const allV   = [...prices, ...fvs];
  const minV   = Math.min(...allV) * 0.97;
  const maxV   = Math.max(...allV) * 1.03;

  const xScale = i => PAD.left + (i / (pts.length - 1)) * iW;
  const yScale = v => PAD.top + iH - ((v - minV) / (maxV - minV)) * iH;

  const pathD = arr => arr
    .map((p, i) => `${i === 0 ? 'M' : 'L'}${xScale(i).toFixed(1)},${yScale(p).toFixed(1)}`)
    .join(' ');

  // Y-axis ticks
  const ticks = 5;
  const yTicks = Array.from({ length: ticks }, (_, i) => minV + (i / (ticks - 1)) * (maxV - minV));
  const yTicksSvg = yTicks.map(v => `
    <line x1="${PAD.left}" y1="${yScale(v).toFixed(1)}" x2="${PAD.left + iW}" y2="${yScale(v).toFixed(1)}" stroke="#e2e7ef" stroke-width="1"/>
    <text x="${PAD.left - 6}" y="${yScale(v).toFixed(1)}" text-anchor="end" dominant-baseline="middle" font-size="11" fill="#6d7c91">${fmtJPYShort(v)}</text>`
  ).join('');

  // X-axis labels (every ~60 days)
  const step = Math.max(1, Math.floor(pts.length / 6));
  const xLabels = pts
    .filter((_, i) => i % step === 0 || i === pts.length - 1)
    .map((p, _, arr) => {
      const i = pts.indexOf(p);
      return `<text x="${xScale(i).toFixed(1)}" y="${H - 6}" text-anchor="middle" font-size="11" fill="#6d7c91">${p.date?.substring(5) || ''}</text>`;
    }).join('');

  // Simulated dashes for FV
  const hasSim = pts.some(p => p.simulated);
  const fvLine = hasSim
    ? buildSegmentedPath(pts, p => p.fairValueMid, p => p.simulated, xScale, yScale)
    : `<path d="${pathD(pts.map(p => p.fairValueMid))}" fill="none" stroke="#2563eb" stroke-width="1.8" stroke-dasharray="none"/>`;

  const svg = `<svg width="${W}" height="${H}" style="display:block;overflow:visible">
    ${yTicksSvg}
    ${xLabels}
    <path d="${pathD(pts.map(p => p.closePrice))}" fill="none" stroke="#15994e" stroke-width="2"/>
    ${fvLine}
    <line x1="${PAD.left}" y1="${PAD.top}" x2="${PAD.left}" y2="${PAD.top + iH}" stroke="#e2e7ef"/>
    <line x1="${PAD.left}" y1="${PAD.top + iH}" x2="${PAD.left + iW}" y2="${PAD.top + iH}" stroke="#e2e7ef"/>
  </svg>`;

  container.innerHTML = svg;

  // Tooltip
  const tooltip = document.getElementById('chart-tooltip');
  const svgEl = container.querySelector('svg');
  if (svgEl && tooltip) {
    svgEl.addEventListener('mousemove', e => {
      const rect = svgEl.getBoundingClientRect();
      const mx = e.clientX - rect.left - PAD.left;
      const idx = Math.max(0, Math.min(pts.length - 1, Math.round((mx / iW) * (pts.length - 1))));
      const p = pts[idx];
      tooltip.style.display = 'block';
      tooltip.style.left = (xScale(idx) + 8) + 'px';
      tooltip.style.top = (yScale(p.closePrice) - 32) + 'px';
      tooltip.innerHTML = `<b>${p.date || ''}</b><br>收盘：${fmtJPY(p.closePrice)}<br>公允：${fmtJPY(p.fairValueMid)}${p.simulated ? '<br><span style="color:var(--warn)">合成</span>' : ''}`;
    });
    svgEl.addEventListener('mouseleave', () => { tooltip.style.display = 'none'; });
  }
}

function buildSegmentedPath(pts, valFn, isSimFn, xScale, yScale) {
  let result = '';
  let seg = [];
  let prevSim = null;
  pts.forEach((p, i) => {
    const sim = isSimFn(p);
    if (prevSim !== null && sim !== prevSim) {
      result += segPath(seg, prevSim, xScale, yScale, valFn);
      seg = [{ p, i }];
    } else {
      seg.push({ p, i });
    }
    prevSim = sim;
  });
  if (seg.length) result += segPath(seg, prevSim, xScale, yScale, valFn);
  return result;
}

function segPath(seg, simulated, xScale, yScale, valFn) {
  if (!seg.length) return '';
  const d = seg.map(({ p, i }, idx) =>
    `${idx === 0 ? 'M' : 'L'}${xScale(i).toFixed(1)},${yScale(valFn(p)).toFixed(1)}`
  ).join(' ');
  return `<path d="${d}" fill="none" stroke="#2563eb" stroke-width="1.8" ${simulated ? 'stroke-dasharray="5,4"' : ''}/>`;
}

// ─── Tab Switch ───────────────────────────────────────────────────────────────

function switchTab(name, btn) {
  document.querySelectorAll('.tab-btn').forEach(b => b.classList.remove('active'));
  if (btn) btn.classList.add('active');
  document.querySelectorAll('.tab-pane').forEach(p => p.classList.remove('active'));
  const pane = document.getElementById('tab-' + name);
  if (pane) pane.classList.add('active');
  if (name === 'history') loadHistory();
}

// ─── API Fetch ────────────────────────────────────────────────────────────────

async function apiFetch(url) {
  const res = await fetch(url);
  if (!res.ok) {
    let msg = `HTTP ${res.status}`;
    try { const j = await res.json(); msg += '：' + (j?.error?.message || j?.message || ''); } catch (_) {}
    throw new Error(msg);
  }
  const payload = await res.json();
  const data = (payload && typeof payload === 'object' && 'success' in payload)
    ? (payload.success === false ? (payload.error || payload) : payload.data)
    : payload;
  return deepCamelCase(data);
}

function deepCamelCase(obj) {
  if (Array.isArray(obj)) return obj.map(deepCamelCase);
  if (obj !== null && typeof obj === 'object') {
    return Object.fromEntries(
      Object.entries(obj).map(([k, v]) => [toCamelCase(k), deepCamelCase(v)])
    );
  }
  return obj;
}

function toCamelCase(s) {
  return s.replace(/_([a-z])/g, (_, c) => c.toUpperCase());
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

function fmtJPY(v) {
  if (v == null) return '—';
  const n = Math.round(v);
  return '¥' + n.toLocaleString('ja-JP');
}

function fmtJPYShort(v) {
  if (v == null) return '';
  if (Math.abs(v) >= 10000) return '¥' + (v / 10000).toFixed(1) + '万';
  return '¥' + Math.round(v).toLocaleString();
}

function verdictLabel(v) {
  const map = {
    UNDERVALUED: '低估', FAIRLY_VALUED: '合理', OVERVALUED: '高估',
    undervalued: '低估', fairly_valued: '合理', overvalued: '高估',
    FAIR: '合理', fair: '合理', LOW_CONFIDENCE: '低置信',
  };
  return map[v] || v || '—';
}

function verdictClass(v) {
  const s = (v || '').toLowerCase();
  if (s.includes('under')) return 'undervalued';
  if (s.includes('over'))  return 'overvalued';
  return 'fair';
}

function setText(id, val) {
  const el = document.getElementById(id);
  if (el) el.textContent = val ?? '—';
}

function show(id) {
  const el = document.getElementById(id);
  if (el) el.style.display = '';
}

function hide(id) {
  const el = document.getElementById(id);
  if (el) el.style.display = 'none';
}

function escHtml(s) {
  return String(s ?? '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}

function showFatalError(msg) {
  hide('loading');
  const app = document.getElementById('app');
  if (app) {
    app.innerHTML = `<div style="display:flex;flex-direction:column;align-items:center;justify-content:center;min-height:60vh;gap:16px">
      <div style="font-size:48px">⚠️</div>
      <div style="font-size:18px;color:var(--bad);font-weight:700">${escHtml(msg)}</div>
      <a href="/valuation-screener.html" style="color:var(--blue)">← 返回筛选器</a>
    </div>`;
  }
}
