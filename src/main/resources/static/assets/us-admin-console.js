'use strict';

const tickerInput = document.getElementById('tickerInput');
const statusBox = document.getElementById('statusBox');
const responseBox = document.getElementById('responseBox');
const heroStatus = document.getElementById('heroStatus');
const detailLink = document.getElementById('detailLink');
const classificationLine = document.getElementById('classificationLine');
const sourceAsOf = document.getElementById('sourceAsOf');
const sourceCards = document.getElementById('sourceCards');
const alertsAsOf = document.getElementById('alertsAsOf');
const alertCards = document.getElementById('alertCards');
const platformPlan = document.getElementById('platformPlan');
const platformCards = document.getElementById('platformCards');
const jobSummaryCards = document.getElementById('jobSummaryCards');
const tickerJobsMeta = document.getElementById('tickerJobsMeta');
const globalJobsMeta = document.getElementById('globalJobsMeta');
const tickerJobsTable = document.getElementById('tickerJobsTable');
const globalJobsTable = document.getElementById('globalJobsTable');
const documentsTable = document.getElementById('documentsTable');
const rawFactsTable = document.getElementById('rawFactsTable');
const standardizedTable = document.getElementById('standardizedTable');
const derivedTable = document.getElementById('derivedTable');
const qualityTable = document.getElementById('qualityTable');
const auditTable = document.getElementById('auditTable');
const buttons = [...document.querySelectorAll('button')];

const countRefs = {
  docCount: document.getElementById('docCount'),
  irDocCount: document.getElementById('irDocCount'),
  rawCount: document.getElementById('rawCount'),
  stdCount: document.getElementById('stdCount'),
  derivedCount: document.getElementById('derivedCount'),
  qualityCount: document.getElementById('qualityCount'),
  auditCount: document.getElementById('auditCount'),
  marketDailyCount: document.getElementById('marketDailyCount'),
  marketSnapshotCount: document.getElementById('marketSnapshotCount'),
  valuationRunCount: document.getElementById('valuationRunCount'),
  latestSnapshotFlag: document.getElementById('latestSnapshotFlag'),
  valuationJobCount: document.getElementById('valuationJobCount'),
};

const state = {
  overview: null,
  sourceStatus: null,
  alerts: null,
  platformMe: null,
  platformUsage: null,
  jobSummary: null,
  tickerJobs: [],
  globalJobs: [],
};

document.addEventListener('DOMContentLoaded', () => {
  bindEvents();
  syncDetailLink();
  loadDashboard();
});

function bindEvents() {
  tickerInput.addEventListener('change', () => {
    tickerInput.value = ticker();
    syncDetailLink();
    loadDashboard();
  });
  tickerInput.addEventListener('keydown', (event) => {
    if (event.key === 'Enter') {
      tickerInput.value = ticker();
      syncDetailLink();
      loadDashboard();
    }
  });

  document.getElementById('overviewBtn').addEventListener('click', loadDashboard);
  document.getElementById('sourceStatusBtn').addEventListener('click', async () => {
    await runAction(`/v1/us-equities-admin/${ticker()}/source-status`, { refresh: ['sourceStatus'] });
  });
  document.getElementById('marketSyncBtn').addEventListener('click', async () => {
    await runAction(`/v1/us-equities-admin/${ticker()}/market-sync`, { method: 'POST', refresh: ['all'] });
  });
  document.getElementById('secSyncBtn').addEventListener('click', async () => {
    await runAction(`/v1/us-equities-admin/${ticker()}/sec-sync`, { method: 'POST', refresh: ['all'] });
  });
  document.getElementById('irSyncBtn').addEventListener('click', async () => {
    await runAction(`/v1/us-equities-admin/${ticker()}/ir-sync`, { method: 'POST', refresh: ['all'] });
  });
  document.getElementById('standardizeBtn').addEventListener('click', async () => {
    await runAction(`/v1/us-equities-admin/${ticker()}/standardize`, { method: 'POST', refresh: ['all'] });
  });
  document.getElementById('runValuationBtn').addEventListener('click', async () => {
    await runAction(`/v1/us-equities/${ticker()}/valuation/run`, { method: 'POST', refresh: ['all'] });
  });
  document.getElementById('retryTickerJobsBtn').addEventListener('click', async () => {
    await runAction(`/v1/us-equities-admin/${ticker()}/valuation-jobs/retry-failed`, { method: 'POST', refresh: ['all'] });
  });
  document.getElementById('retryGlobalJobsBtn').addEventListener('click', async () => {
    await runAction('/v1/us-equities-admin/valuation-jobs/retry-failed', { method: 'POST', refresh: ['all'] });
  });
  document.getElementById('recoverStaleBtn').addEventListener('click', async () => {
    await runAction('/v1/us-equities-admin/valuation-jobs/recover-stale', { method: 'POST', refresh: ['all'] });
  });
  document.getElementById('universeBtn').addEventListener('click', async () => {
    await runAction('/v1/us-equities-admin/universe/sync', { method: 'POST', refresh: ['jobs'] });
  });
}

async function loadDashboard() {
  const symbol = ticker();
  syncDetailLink();
  setBusy(true);
  setStatus(`正在加载 ${symbol} 的后台概览...`);
  heroStatus.textContent = `正在刷新 ${symbol}`;

  const requests = await Promise.allSettled([
    fetchJson(`/v1/us-equities-admin/${symbol}/overview`),
    fetchJson('/v1/us-equities-admin/valuation-jobs/summary'),
    fetchJson(`/v1/us-equities-admin/${symbol}/valuation-jobs?limit=8`),
    fetchJson('/v1/us-equities-admin/valuation-jobs?limit=8'),
    fetchJson('/v1/us-equities-admin/valuation-alerts'),
    fetchJson('/v1/platform/me'),
    fetchJson('/v1/platform/usage'),
  ]);

  const [overviewRes, summaryRes, tickerJobsRes, globalJobsRes, alertsRes, platformMeRes, platformUsageRes] = requests;

  if (overviewRes.status === 'fulfilled') {
    state.overview = overviewRes.value;
    renderOverview(overviewRes.value);
    renderJson(overviewRes.value);
  } else {
    renderOverview(null);
  }

  if (summaryRes.status === 'fulfilled') {
    state.jobSummary = summaryRes.value;
    renderJobSummary(summaryRes.value);
  } else {
    renderJobSummary(null);
  }

  state.tickerJobs = tickerJobsRes.status === 'fulfilled' ? tickerJobsRes.value : [];
  state.globalJobs = globalJobsRes.status === 'fulfilled' ? globalJobsRes.value : [];
  renderJobsTables(state.tickerJobs, state.globalJobs);

  if (alertsRes.status === 'fulfilled') {
    state.alerts = alertsRes.value;
    renderAlerts(alertsRes.value);
  } else {
    renderAlerts(null);
  }

  state.platformMe = platformMeRes.status === 'fulfilled' ? platformMeRes.value : null;
  state.platformUsage = platformUsageRes.status === 'fulfilled' ? platformUsageRes.value : null;
  renderPlatformSummary(state.platformMe, state.platformUsage);

  const failed = requests.filter(item => item.status === 'rejected');
  if (failed.length) {
    setStatus(`加载完成，但有 ${failed.length} 项子请求失败。`, true);
  } else {
    setStatus(`已加载 ${symbol} 的后台概览。`);
  }
  heroStatus.textContent = `最近刷新：${new Date().toLocaleTimeString('zh-CN', { hour12: false })}`;
  setBusy(false);

  fetchJson(`/v1/us-equities-admin/${symbol}/source-status`)
    .then((payload) => {
      state.sourceStatus = payload;
      renderSourceStatus(payload);
    })
    .catch((error) => {
      renderSourceStatus(null, error);
    });
}

async function runAction(url, options = {}) {
  const method = options.method || 'GET';
  setBusy(true);
  setStatus(`请求中：${method} ${url}`);
  heroStatus.textContent = `正在执行 ${method}`;
  try {
    const payload = await fetchJson(url, { method });
    renderJson(payload);
    setStatus(`完成：${method} ${url}`);
    heroStatus.textContent = `刚完成：${method} ${ticker()}`;
    if ((options.refresh || []).includes('all')) {
      await loadDashboard();
    } else {
      if ((options.refresh || []).includes('sourceStatus')) {
        renderSourceStatus(payload);
      }
      if ((options.refresh || []).includes('jobs')) {
        const [summary, jobs] = await Promise.all([
          fetchJson('/v1/us-equities-admin/valuation-jobs/summary'),
          fetchJson('/v1/us-equities-admin/valuation-jobs?limit=8'),
        ]);
        renderJobSummary(summary);
        renderJobsTables(state.tickerJobs, jobs);
      }
    }
    return payload;
  } catch (error) {
    renderJson({ error: error.message || 'unknown_error' });
    setStatus(error.message || '请求失败', true);
    heroStatus.textContent = '最近操作失败';
    throw error;
  } finally {
    setBusy(false);
  }
}

async function fetchJson(url, options = {}) {
  if (window.FairvaluePlatformApi && typeof window.FairvaluePlatformApi.fetchJson === 'function') {
    return window.FairvaluePlatformApi.fetchJson(url, options);
  }
  const response = await fetch(url, options);
  const payload = await response.json();
  const normalized = unwrapApiEnvelope(payload);
  if (!response.ok) {
    const errorPayload = payload && payload.error ? payload.error : payload;
    throw new Error(errorPayload.message || payload.message || `HTTP ${response.status}`);
  }
  return normalized;
}

function unwrapApiEnvelope(payload) {
  if (window.FairvaluePlatformApi && typeof window.FairvaluePlatformApi.unwrapApiEnvelope === 'function') {
    return window.FairvaluePlatformApi.unwrapApiEnvelope(payload);
  }
  if (payload && typeof payload === 'object' && 'success' in payload) {
    if (payload.success === false) {
      return payload.error || payload;
    }
    return payload.data;
  }
  return payload;
}

function renderOverview(payload) {
  if (!payload) {
    Object.values(countRefs).forEach((el) => { if (el) el.textContent = '-'; });
    classificationLine.textContent = '分类：加载失败';
    renderEmptyTable(documentsTable, '暂无 overview 数据');
    renderEmptyTable(rawFactsTable, '暂无 overview 数据');
    renderEmptyTable(standardizedTable, '暂无 overview 数据');
    renderEmptyTable(derivedTable, '暂无 overview 数据');
    renderEmptyTable(qualityTable, '暂无 overview 数据');
    renderEmptyTable(auditTable, '暂无 overview 数据');
    return;
  }

  setCount('docCount', payload.source_document_count);
  setCount('irDocCount', payload.company_ir_document_count);
  setCount('rawCount', payload.raw_fact_count);
  setCount('stdCount', payload.financial_standardized_count);
  setCount('derivedCount', payload.financial_derived_metric_count);
  setCount('qualityCount', payload.financial_quality_score_count);
  setCount('auditCount', payload.data_quality_audit_count);
  setCount('marketDailyCount', payload.market_price_daily_count);
  setCount('marketSnapshotCount', payload.market_snapshot_count);
  setCount('valuationRunCount', payload.valuation_run_count);
  setCount('latestSnapshotFlag', payload.latest_valuation_snapshot_present ? 'YES' : 'NO');
  setCount('valuationJobCount', payload.valuation_job_count);

  classificationLine.textContent = `分类：${safe(payload.company_type)} | 模板：${safe(payload.sector_template)} | 行业：${safe(payload.industry)} | 公司：${safe(payload.company_name)}`;

  renderTable(documentsTable,
    [
      ['document_type', 'Type'],
      ['filing_date', 'Date'],
      ['accession_no', 'Accession'],
      ['parsed_status', 'Status'],
    ],
    payload.latest_documents
  );

  renderTable(rawFactsTable,
    [
      ['document_type', 'Doc'],
      ['concept_name', 'Concept'],
      ['period_end', 'Period End'],
      ['unit', 'Unit'],
      ['value_numeric', 'Value'],
    ],
    payload.latest_raw_facts,
    (row, key) => key === 'value_numeric' ? formatNumber(row[key]) : safe(row[key])
  );

  renderTable(standardizedTable,
    [
      ['period_type', 'Type'],
      ['fiscal_year', 'FY'],
      ['fiscal_period', 'FP'],
      ['period_end', 'Period End'],
      ['revenue', 'Revenue'],
      ['net_income', 'Net Income'],
      ['free_cash_flow', 'FCF'],
    ],
    payload.latest_financial_standardized,
    (row, key) => numericLike(key) ? formatNumber(row[key]) : safe(row[key])
  );

  renderTable(derivedTable,
    [
      ['period_type', 'Type'],
      ['period_end', 'Period End'],
      ['roic', 'ROIC'],
      ['fcf_margin', 'FCF Margin'],
      ['book_value_per_share', 'BVPS'],
      ['eps_diluted', 'EPS'],
    ],
    payload.latest_financial_derived_metrics,
    (row, key) => percentLike(key) ? formatPercent(row[key]) : numericLike(key) ? formatNumber(row[key]) : safe(row[key])
  );

  renderTable(qualityTable,
    [
      ['period_type', 'Type'],
      ['period_end', 'Period End'],
      ['earnings_quality_score', 'Earnings'],
      ['balance_sheet_score', 'Balance'],
      ['capital_efficiency_score', 'Efficiency'],
      ['total_quality_score', 'Total'],
    ],
    payload.latest_financial_quality_scores,
    (row, key) => scoreLike(key) ? formatPercent(row[key]) : safe(row[key])
  );

  renderTable(auditTable,
    [
      ['audit_date', 'Audit Date'],
      ['latest_10k_date', 'Latest 10-K'],
      ['latest_10q_date', 'Latest 10-Q'],
      ['guidance_status', 'Guidance'],
      ['confidence_level', 'Confidence'],
    ],
    payload.latest_data_quality_audits,
    (row, key) => key === 'confidence_level' ? formatPercent(row[key]) : safe(row[key])
  );
}

function renderSourceStatus(payload, error) {
  if (!payload) {
    sourceAsOf.textContent = error ? '加载失败' : '-';
    sourceCards.innerHTML = '<div class="table-shell empty">暂时拿不到 source-status，我们稍后再试。</div>';
    return;
  }

  sourceAsOf.textContent = payload.as_of ? formatDateTime(payload.as_of) : '-';
  const sources = [
    ['FRED', payload.fred, sourceMetaFred],
    ['Longbridge', payload.longbridge, sourceMetaLongbridge],
    ['Damodaran', payload.damodaran, sourceMetaDamodaran],
    ['SimFin', payload.simfin, sourceMetaSimfin],
  ];

  sourceCards.innerHTML = sources.map(([label, item, metaBuilder]) => {
    const status = normalizeStatus(item?.status);
    const meta = metaBuilder(item || {});
    return `
      <article class="source-card">
        <div class="top">
          <div>
            <div class="label">${escapeHtml(label)}</div>
            <strong>${escapeHtml(status.label)}</strong>
          </div>
          <span class="status-pill ${status.cls}">${escapeHtml(status.label)}</span>
        </div>
        <div class="source-meta">${meta}</div>
      </article>`;
  }).join('');
}

function renderAlerts(payload) {
  if (!payload) {
    alertsAsOf.textContent = '加载失败';
    alertCards.innerHTML = '<div class="table-shell empty">暂时拿不到 valuation alerts，我们稍后再试。</div>';
    return;
  }

  alertsAsOf.textContent = payload.as_of ? formatDateTime(payload.as_of) : '-';
  const alerts = Array.isArray(payload.alerts) ? payload.alerts : [];
  if (!alerts.length) {
    alertCards.innerHTML = `
      <article class="source-card">
        <div class="top">
          <div>
            <div class="label">Queue Health</div>
            <strong>OK</strong>
          </div>
          <span class="status-pill status-ok">NO ALERTS</span>
        </div>
        <div class="source-meta">
          ${metaLine('Queued', payload.queued_count ?? '-')}
          ${metaLine('Running', payload.running_count ?? '-')}
          ${metaLine('Backlog', payload.ready_backlog_count ?? '-')}
          ${metaLine('Recent Failed', payload.recent_failed_count ?? '-')}
        </div>
      </article>`;
    return;
  }

  alertCards.innerHTML = alerts.map((item) => {
    const status = normalizeAlertSeverity(item.severity);
    return `
      <article class="source-card">
        <div class="top">
          <div>
            <div class="label">${escapeHtml(item.code || 'alert')}</div>
            <strong>${escapeHtml(item.title || 'Queue Alert')}</strong>
          </div>
          <span class="status-pill ${status.cls}">${escapeHtml(status.label)}</span>
        </div>
        <div class="source-meta">
          ${metaLine('Detail', item.detail || '-')}
          ${metaLine('Queued', payload.queued_count ?? '-')}
          ${metaLine('Running', payload.running_count ?? '-')}
        </div>
      </article>`;
  }).join('');
}

function renderPlatformSummary(me, usage) {
  if (!me || !usage) {
    platformPlan.textContent = '加载失败';
    platformCards.innerHTML = [
      miniCard('RPM', '-'),
      miniCard('Today', '-'),
      miniCard('Routes', '-'),
      miniCard('Last Seen', '-'),
    ].join('');
    return;
  }

  platformPlan.textContent = `${safe(me.plan_code)} · ${safe(me.client_name)}`;
  platformCards.innerHTML = [
    miniCard('RPM', me.requests_per_minute),
    miniCard('Today', usage.total_requests),
    miniCard('Routes', Array.isArray(usage.routes) ? usage.routes.length : '-'),
    miniCard('Last Seen', usage.last_seen_at ? formatDateTime(usage.last_seen_at) : '-'),
  ].join('');
}

function renderJobSummary(payload) {
  if (!payload) {
    jobSummaryCards.innerHTML = [miniCard('Queued', '-'), miniCard('Running', '-'), miniCard('Completed', '-'), miniCard('Failed', '-')].join('');
    return;
  }
  jobSummaryCards.innerHTML = [
    miniCard('Queued', payload.queued_count),
    miniCard('Running', payload.running_count),
    miniCard('Completed', payload.completed_count),
    miniCard('Failed', payload.failed_count),
  ].join('');
}

function renderJobsTables(tickerJobs, globalJobs) {
  tickerJobsMeta.textContent = `${ticker()} 最近 ${tickerJobs.length} 条`;
  globalJobsMeta.textContent = `全局最近 ${globalJobs.length} 条`;

  renderTable(tickerJobsTable,
    [
      ['status', 'Status'],
      ['started_at', 'Started'],
      ['finished_at', 'Finished'],
      ['retry_count', 'Retries'],
      ['valuation_run_id', 'Run ID'],
    ],
    tickerJobs,
    (row, key) => key.endsWith('_at') ? formatDateTime(row[key]) : safe(row[key])
  );

  renderTable(globalJobsTable,
    [
      ['ticker', 'Ticker'],
      ['status', 'Status'],
      ['started_at', 'Started'],
      ['finished_at', 'Finished'],
      ['retry_count', 'Retries'],
    ],
    globalJobs,
    (row, key) => key.endsWith('_at') ? formatDateTime(row[key]) : safe(row[key])
  );
}

function renderTable(container, columns, rows, cellFormatter) {
  if (!rows || !rows.length) {
    renderEmptyTable(container, '暂无数据');
    return;
  }
  const head = columns.map(([, label]) => `<th>${escapeHtml(label)}</th>`).join('');
  const body = rows.map((row) => {
    const cells = columns.map(([key]) => {
      const raw = cellFormatter ? cellFormatter(row, key) : safe(row[key]);
      return `<td>${raw}</td>`;
    }).join('');
    return `<tr>${cells}</tr>`;
  }).join('');
  container.classList.remove('empty');
  container.innerHTML = `<table><thead><tr>${head}</tr></thead><tbody>${body}</tbody></table>`;
}

function renderEmptyTable(container, text) {
  container.classList.add('empty');
  container.innerHTML = escapeHtml(text);
}

function setBusy(flag) {
  buttons.forEach((button) => { button.disabled = flag; });
}

function setStatus(message, isError = false) {
  statusBox.textContent = message;
  statusBox.classList.toggle('error', isError);
}

function renderJson(payload) {
  responseBox.textContent = JSON.stringify(payload, null, 2);
}

function syncDetailLink() {
  detailLink.href = `/us-stock-detail.html?ticker=${encodeURIComponent(ticker())}`;
}

function ticker() {
  return (tickerInput.value || '').trim().toUpperCase().replace('.US', '').replace('.', '-') || 'AAPL';
}

function setCount(id, value) {
  const el = countRefs[id];
  if (!el) return;
  el.textContent = value == null ? '-' : String(value);
}

function miniCard(label, value) {
  return `<article class="mini-card"><span>${escapeHtml(label)}</span><strong>${escapeHtml(value == null ? '-' : String(value))}</strong></article>`;
}

function normalizeStatus(status) {
  const raw = String(status || 'error').toLowerCase();
  const labelMap = {
    ok: 'OK',
    error: 'ERROR',
    disabled: 'DISABLED',
    unauthenticated: 'UNAUTH',
    timeout: 'TIMEOUT',
  };
  return {
    label: labelMap[raw] || raw.toUpperCase(),
    cls: `status-${raw in labelMap ? raw : 'error'}`,
  };
}

function normalizeAlertSeverity(severity) {
  const raw = String(severity || 'warn').toLowerCase();
  if (raw === 'error' || raw === 'critical') {
    return { label: raw.toUpperCase(), cls: 'status-error' };
  }
  if (raw === 'info' || raw === 'ok') {
    return { label: raw.toUpperCase(), cls: 'status-ok' };
  }
  return { label: raw.toUpperCase(), cls: 'status-timeout' };
}

function sourceMetaFred(item) {
  return [
    metaLine('Enabled', booleanLabel(item.enabled)),
    metaLine('API Key', booleanLabel(item.api_key_present)),
    metaLine('Detail', item.detail || '-'),
    metaLine('Risk-free', item.risk_free_observation ? `${formatPercent(item.risk_free_observation.value)} · ${safe(item.risk_free_observation.date)}` : '-'),
  ].join('');
}

function sourceMetaLongbridge(item) {
  return [
    metaLine('Enabled', booleanLabel(item.enabled)),
    metaLine('Configured', booleanLabel(item.configured)),
    metaLine('Detail', item.detail || '-'),
    metaLine('Symbol', item.symbol_full || '-'),
    metaLine('Trade Date', item.trade_date || '-'),
  ].join('');
}

function sourceMetaDamodaran(item) {
  return [
    metaLine('Enabled', booleanLabel(item.enabled)),
    metaLine('Detail', item.detail || '-'),
    metaLine('ERP As Of', item.erp_as_of || '-'),
    metaLine('ERP Source', item.erp_source || '-'),
  ].join('');
}

function sourceMetaSimfin(item) {
  const company = item.company_record || {};
  return [
    metaLine('Enabled', booleanLabel(item.enabled)),
    metaLine('API Key', booleanLabel(item.api_key_present)),
    metaLine('Authenticated', booleanLabel(item.authenticated)),
    metaLine('Detail', item.detail || '-'),
    metaLine('Company', company.simfin_id ? `${safe(company.simfin_id)} · ${safe(company.main_currency)}` : '-'),
  ].join('');
}

function metaLine(label, value) {
  return `<div><strong>${escapeHtml(label)}:</strong> ${escapeHtml(value == null ? '-' : String(value))}</div>`;
}

function booleanLabel(value) {
  return value ? 'yes' : 'no';
}

function formatDateTime(value) {
  if (!value) return '-';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return safe(value);
  return date.toLocaleString('zh-CN', { hour12: false });
}

function formatPercent(value) {
  if (value == null || value === '') return '-';
  return `${(Number(value) * 100).toFixed(1)}%`;
}

function formatNumber(value) {
  if (value == null || value === '') return '-';
  const num = Number(value);
  if (!Number.isFinite(num)) return safe(value);
  if (Math.abs(num) >= 1_000_000_000) return `${(num / 1_000_000_000).toFixed(2)}B`;
  if (Math.abs(num) >= 1_000_000) return `${(num / 1_000_000).toFixed(2)}M`;
  if (Math.abs(num) >= 1_000) return num.toLocaleString('en-US', { maximumFractionDigits: 0 });
  return num.toFixed(4).replace(/\\.0+$/, '').replace(/(\\.\\d*?)0+$/, '$1');
}

function numericLike(key) {
  return /revenue|income|flow|value|eps|score|count|id/i.test(key);
}

function percentLike(key) {
  return /margin|roic|roe|roa|ratio|conversion/i.test(key);
}

function scoreLike(key) {
  return /score/i.test(key);
}

function safe(value) {
  return value == null || value === '' ? '-' : value;
}

function escapeHtml(value) {
  return String(value == null ? '-' : value)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/\"/g, '&quot;')
    .replace(/'/g, '&#39;');
}
