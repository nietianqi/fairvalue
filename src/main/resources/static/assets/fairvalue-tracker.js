'use strict';

(function initFairvalueTracker(global) {
  const WATCHLIST_KEY = 'fairvalue.member.watchlist.v2';
  const ALERTS_KEY = 'fairvalue.member.alerts.v2';
  const GROUPS_KEY = 'fairvalue.member.groups.v2';
  const CONTEXTS_KEY = 'fairvalue.member.savedContexts.v2';
  const WORKSPACE_META_KEY = 'fairvalue.member.workspace.v2';
  const WORKSPACE_KEY_KEY = 'fairvalue.member.workspaceKey.v1';
  const SEEDED_KEY = 'fairvalue.member.seeded.v2';

  const LEGACY_WATCHLIST_KEY = 'fairvalue.member.watchlist.v1';
  const LEGACY_ALERTS_KEY = 'fairvalue.member.alerts.v1';
  const LEGACY_CONTEXT_KEY = 'fairvalue.valuationScreener.savedState.v5';

  const DEFAULT_GROUPS = [
    {
      id: 'core-holdings',
      label: '核心底仓',
      description: '适合长期跟踪的高质量资产和低估样本。',
      market: 'ALL',
      accent: 'core',
      sortOrder: 0,
      isPinned: true,
    },
    {
      id: 'trend-setup',
      label: '短线趋势',
      description: '适合右侧趋势、催化或回踩观察的候选池。',
      market: 'ALL',
      accent: 'trend',
      sortOrder: 1,
      isPinned: true,
    },
    {
      id: 'valuation-lab',
      label: '估值实验室',
      description: '用来放深度低估、高分歧和等待验证的标的。',
      market: 'ALL',
      accent: 'value',
      sortOrder: 2,
      isPinned: false,
    },
  ];

  const ALERT_TEMPLATES = [
    {
      id: 'value-return',
      label: '价值回归',
      category: 'price',
      type: 'undervalue_zone',
      description: '跌入合理估值区间时提醒',
      channel: '站内信 + 邮件',
      frequency: '实时',
      threshold: '进入低估区',
      icon: 'zap',
    },
    {
      id: 'trend-entry',
      label: '右侧入场',
      category: 'trend',
      type: 'trend_breakout',
      description: '放量突破关键压力位提醒',
      channel: '站内信',
      frequency: '实时',
      threshold: '突破关键压力位',
      icon: 'trend',
    },
    {
      id: 'earnings-window',
      label: '财报临近',
      category: 'event',
      type: 'earnings_window',
      description: '财报披露前 3 天提醒',
      channel: '站内信 + 邮件',
      frequency: '财报前 3 天',
      threshold: '财报前 3 天',
      icon: 'calendar',
    },
    {
      id: 'big-move',
      label: '价格异动',
      category: 'price',
      type: 'price_move',
      description: '日内大幅异动或放量提醒',
      channel: '站内信',
      frequency: '实时',
      threshold: '涨跌超 5% / 成交放量',
      icon: 'pulse',
    },
  ];

  let workspaceReadyResolve;
  const workspaceReady = new Promise((resolve) => {
    workspaceReadyResolve = resolve;
  });
  let workspaceRemoteHydrated = false;
  let workspaceSyncTimer = null;
  let workspaceSyncInFlight = Promise.resolve();

  function readJson(key, fallback) {
    try {
      const raw = global.localStorage.getItem(key);
      if (!raw) {
        return fallback;
      }
      const parsed = JSON.parse(raw);
      if (Array.isArray(fallback)) {
        return Array.isArray(parsed) ? parsed : fallback;
      }
      return parsed && typeof parsed === 'object' ? parsed : fallback;
    } catch (_error) {
      return fallback;
    }
  }

  function writeJson(key, value) {
    global.localStorage.setItem(key, JSON.stringify(value));
  }

  function listOrEmpty(value) {
    return Array.isArray(value) ? value : [];
  }

  function mapOrEmpty(value) {
    return value && typeof value === 'object' && !Array.isArray(value) ? value : {};
  }

  function emitChange(type, detail) {
    global.dispatchEvent(new CustomEvent('fairvalue:workspace-change', {
      detail: { type, ...detail },
    }));
  }

  function nowIso() {
    return new Date().toISOString();
  }

  function randomId(prefix) {
    return `${prefix}-${Math.random().toString(36).slice(2, 8)}-${Date.now().toString(36)}`;
  }

  function ensureWorkspaceKey() {
    let workspaceKey = String(global.localStorage.getItem(WORKSPACE_KEY_KEY) || '').trim();
    if (!workspaceKey) {
      const generated = typeof global.crypto?.randomUUID === 'function'
        ? `workspace-${global.crypto.randomUUID()}`
        : randomId('workspace');
      workspaceKey = generated.replace(/[^A-Za-z0-9:_-]+/g, '-').slice(0, 128);
      global.localStorage.setItem(WORKSPACE_KEY_KEY, workspaceKey);
    }
    return workspaceKey;
  }

  function getWorkspaceKey() {
    return ensureWorkspaceKey();
  }

  function normalizeTicker(value) {
    return String(value || '')
      .trim()
      .toUpperCase()
      .replace(/\.US$/, '')
      .replace(/\.HK$/, '')
      .replace(/\.SH$/, '')
      .replace(/\.SZ$/, '')
      .replace(/\.T$/, '')
      .replace(/\./g, '-');
  }

  function normalizeMarket(value) {
    const market = String(value || 'US').trim().toUpperCase();
    return ['US', 'CN', 'JP', 'HK', 'ALL'].includes(market) ? market : 'US';
  }

  function normalizeGroupId(value) {
    return String(value || '')
      .trim()
      .toLowerCase()
      .replace(/[^a-z0-9]+/g, '-')
      .replace(/^-+|-+$/g, '') || 'watch-group';
  }

  function titleCaseLabel(value, fallback) {
    const text = String(value || '').trim();
    return text || fallback;
  }

  function watchlistKeyOf(item) {
    return `${normalizeMarket(item.market)}:${normalizeTicker(item.ticker)}`;
  }

  function inferAlertCategory(type) {
    const normalized = String(type || '').trim().toLowerCase();
    if (normalized.includes('trend')) {
      return 'trend';
    }
    if (normalized.includes('earnings') || normalized.includes('announcement') || normalized.includes('event')) {
      return 'event';
    }
    return 'price';
  }

  function inferAlertLabel(type) {
    const labels = {
      undervalue_zone: '跌入低估区提醒',
      trend_breakout: '趋势突破提醒',
      earnings_window: '财报临近提醒',
      announcement: '公告提醒',
      price_move: '价格异动提醒',
      event_window: '事件驱动提醒',
    };
    return labels[String(type || '').trim()] || '智能提醒';
  }

  function normalizeGroupRecord(group, index) {
    return {
      id: normalizeGroupId(group.id || group.label || `group-${index + 1}`),
      label: titleCaseLabel(group.label, `分组 ${index + 1}`),
      description: String(group.description || '').trim(),
      market: normalizeMarket(group.market || 'ALL'),
      accent: String(group.accent || 'default').trim() || 'default',
      sortOrder: Number.isFinite(Number(group.sortOrder)) ? Number(group.sortOrder) : index,
      isPinned: group.isPinned === true,
      createdAt: group.createdAt || nowIso(),
      updatedAt: group.updatedAt || nowIso(),
    };
  }

  function normalizeWatchlistRecord(item, groups) {
    const availableGroups = groups && groups.length ? groups : DEFAULT_GROUPS;
    const fallbackGroup = availableGroups.find((candidate) => candidate.id === 'core-holdings') || availableGroups[0];
    const groupId = normalizeGroupId(item.groupId || item.group || fallbackGroup.id);
    const matchedGroup = availableGroups.find((candidate) => candidate.id === groupId) || fallbackGroup;
    const market = normalizeMarket(item.market || matchedGroup.market || 'US');
    const ticker = normalizeTicker(item.ticker);
    return {
      id: item.id || `${market}-${ticker}`,
      market,
      ticker,
      companyName: titleCaseLabel(item.companyName || item.name, ticker),
      thesis: String(item.thesis || '').trim(),
      groupId: matchedGroup.id,
      groupLabel: titleCaseLabel(item.groupLabel || matchedGroup.label, matchedGroup.label),
      tag: titleCaseLabel(item.tag, matchedGroup.id === 'trend-setup' ? '趋势跟踪' : matchedGroup.id === 'valuation-lab' ? '估值复核' : '核心底仓'),
      sourceContext: String(item.sourceContext || item.source || '').trim(),
      note: String(item.note || '').trim(),
      addedAt: item.addedAt || item.createdAt || nowIso(),
      updatedAt: item.updatedAt || nowIso(),
      lastViewedAt: item.lastViewedAt || null,
    };
  }

  function normalizeAlertRecord(alert, groups) {
    const availableGroups = groups && groups.length ? groups : DEFAULT_GROUPS;
    const fallbackGroup = availableGroups.find((candidate) => candidate.id === 'core-holdings') || availableGroups[0];
    const groupId = normalizeGroupId(alert.groupId || alert.group || fallbackGroup.id);
    const matchedGroup = availableGroups.find((candidate) => candidate.id === groupId) || fallbackGroup;
    const market = normalizeMarket(alert.market || 'US');
    const ticker = normalizeTicker(alert.ticker);
    const type = String(alert.type || 'undervalue_zone').trim() || 'undervalue_zone';
    return {
      id: alert.id || randomId(`${market}-${ticker || 'alert'}`),
      market,
      ticker,
      type,
      category: String(alert.category || inferAlertCategory(type)).trim() || inferAlertCategory(type),
      label: titleCaseLabel(alert.label, inferAlertLabel(type)),
      description: String(alert.description || '').trim(),
      channel: titleCaseLabel(alert.channel, '站内信'),
      frequency: titleCaseLabel(alert.frequency, '实时'),
      threshold: titleCaseLabel(alert.threshold, '默认阈值'),
      enabled: alert.enabled !== false,
      groupId: matchedGroup.id,
      groupLabel: titleCaseLabel(alert.groupLabel || matchedGroup.label, matchedGroup.label),
      sourceContext: String(alert.sourceContext || '').trim(),
      createdAt: alert.createdAt || nowIso(),
      updatedAt: alert.updatedAt || nowIso(),
    };
  }

  function saveGroups(groups) {
    writeJson(GROUPS_KEY, groups);
  }

  function getGroups() {
    const groups = readJson(GROUPS_KEY, []).map(normalizeGroupRecord);
    if (groups.length) {
      return groups.sort((left, right) => left.sortOrder - right.sortOrder || left.label.localeCompare(right.label));
    }
    saveGroups(DEFAULT_GROUPS.map(normalizeGroupRecord));
    return getGroups();
  }

  function saveWatchlist(items) {
    writeJson(WATCHLIST_KEY, items);
  }

  function getWatchlist() {
    const groups = getGroups();
    return readJson(WATCHLIST_KEY, [])
      .map((item) => normalizeWatchlistRecord(item, groups))
      .sort((left, right) => new Date(right.updatedAt || right.addedAt || 0) - new Date(left.updatedAt || left.addedAt || 0));
  }

  function saveAlerts(items) {
    writeJson(ALERTS_KEY, items);
  }

  function getAlerts() {
    const groups = getGroups();
    return readJson(ALERTS_KEY, [])
      .map((item) => normalizeAlertRecord(item, groups))
      .sort((left, right) => new Date(right.updatedAt || right.createdAt || 0) - new Date(left.updatedAt || left.createdAt || 0));
  }

  function getGroupedWatchlist() {
    const groups = getGroups();
    const watchlist = getWatchlist();
    return groups.map((group) => ({
      ...group,
      items: watchlist.filter((item) => item.groupId === group.id),
    }));
  }

  function getWorkspaceMeta() {
    const fallback = {
      recentSymbols: [],
      reminderQuota: 20,
      currentPlan: '专业版',
      expiresAt: '2025-12-31',
      upgradedHint: '升级至尊版可解锁更多提醒模板与更深度 AI 解读。',
      updatedAt: nowIso(),
    };
    return { ...fallback, ...readJson(WORKSPACE_META_KEY, fallback) };
  }

  function saveWorkspaceMeta(patch) {
    const current = getWorkspaceMeta();
    const next = { ...current, ...patch, updatedAt: nowIso() };
    writeJson(WORKSPACE_META_KEY, next);
    emitChange('workspace-meta', { meta: next });
    scheduleRemoteSync('workspace-meta');
    return next;
  }

  function saveFlowContext(patch) {
    return saveWorkspaceMeta({
      ...mapOrEmpty(patch),
      lastActionAt: nowIso(),
    });
  }

  function getSavedContexts() {
    return readJson(CONTEXTS_KEY, []).slice().sort((left, right) => new Date(right.updatedAt || 0) - new Date(left.updatedAt || 0));
  }

  function saveSavedContexts(items) {
    writeJson(CONTEXTS_KEY, items);
  }

  function saveScreenerContext(payload) {
    const context = {
      id: payload.id || randomId('screen'),
      title: titleCaseLabel(payload.title, '未命名筛选方案'),
      market: normalizeMarket(payload.market || 'US'),
      description: String(payload.description || '').trim(),
      summary: String(payload.summary || '').trim(),
      filters: payload.filters && typeof payload.filters === 'object' ? payload.filters : {},
      resultCount: Number.isFinite(Number(payload.resultCount)) ? Number(payload.resultCount) : null,
      updatedAt: nowIso(),
    };
    const contexts = getSavedContexts().filter((item) => item.id !== context.id && item.title !== context.title);
    contexts.unshift(context);
    saveSavedContexts(contexts.slice(0, 8));
    emitChange('saved-contexts', { contexts: getSavedContexts() });
    scheduleRemoteSync('saved-contexts');
    return context;
  }

  function createGroup(payload) {
    const groups = getGroups();
    const record = normalizeGroupRecord({
      ...payload,
      sortOrder: groups.length,
      createdAt: nowIso(),
      updatedAt: nowIso(),
    }, groups.length);
    if (groups.some((item) => item.id === record.id)) {
      return groups.find((item) => item.id === record.id);
    }
    const next = groups.concat(record);
    saveGroups(next);
    emitChange('groups', { groups: getGroups() });
    scheduleRemoteSync('groups');
    return record;
  }

  function upsertWatchlist(item) {
    const groups = getGroups();
    if (item.groupLabel && !item.groupId) {
      item.groupId = normalizeGroupId(item.groupLabel);
    }
    if (item.groupId && !groups.some((candidate) => candidate.id === normalizeGroupId(item.groupId))) {
      createGroup({ id: item.groupId, label: item.groupLabel || item.groupId, market: item.market || 'ALL' });
    }
    const normalized = normalizeWatchlistRecord({
      ...item,
      updatedAt: nowIso(),
      addedAt: item.addedAt || nowIso(),
    }, getGroups());
    const existing = getWatchlist();
    const key = watchlistKeyOf(normalized);
    const filtered = existing.filter((candidate) => watchlistKeyOf(candidate) !== key);
    filtered.unshift(normalized);
    saveWatchlist(filtered);
    saveWorkspaceMeta({ recentSymbols: rememberRecentSymbol(normalized.market, normalized.ticker) });
    emitChange('watchlist', { watchlist: getWatchlist() });
    scheduleRemoteSync('watchlist');
    return normalized;
  }

  function bulkUpsertWatchlist(items, defaults) {
    const added = [];
    (items || []).forEach((item) => {
      const record = upsertWatchlist({ ...(defaults || {}), ...(item || {}) });
      added.push(record);
    });
    return added;
  }

  function removeFromWatchlist(ticker, market) {
    const key = `${normalizeMarket(market)}:${normalizeTicker(ticker)}`;
    const filtered = getWatchlist().filter((candidate) => watchlistKeyOf(candidate) !== key);
    saveWatchlist(filtered);
    emitChange('watchlist', { watchlist: getWatchlist() });
    scheduleRemoteSync('watchlist');
    return filtered;
  }

  function hasInWatchlist(ticker, market) {
    const key = `${normalizeMarket(market)}:${normalizeTicker(ticker)}`;
    return getWatchlist().some((candidate) => watchlistKeyOf(candidate) === key);
  }

  function toggleWatchlist(item) {
    if (hasInWatchlist(item.ticker, item.market)) {
      removeFromWatchlist(item.ticker, item.market);
      return false;
    }
    upsertWatchlist(item);
    return true;
  }

  function createAlert(payload) {
    const normalized = normalizeAlertRecord({
      ...payload,
      updatedAt: nowIso(),
      createdAt: payload.createdAt || nowIso(),
    }, getGroups());
    const existing = getAlerts().filter((item) => item.id !== normalized.id);
    existing.unshift(normalized);
    saveAlerts(existing);
    emitChange('alerts', { alerts: getAlerts() });
    scheduleRemoteSync('alerts');
    return normalized;
  }

  function removeAlert(id) {
    const alerts = getAlerts().filter((item) => item.id !== id);
    saveAlerts(alerts);
    emitChange('alerts', { alerts: getAlerts() });
    scheduleRemoteSync('alerts');
    return alerts;
  }

  function toggleAlertEnabled(id) {
    const alerts = getAlerts().map((item) => item.id === id ? { ...item, enabled: !item.enabled, updatedAt: nowIso() } : item);
    saveAlerts(alerts);
    const target = alerts.find((item) => item.id === id) || null;
    emitChange('alerts', { alerts: getAlerts() });
    scheduleRemoteSync('alerts');
    return target;
  }

  function listAlertsForTicker(ticker, market) {
    const normalizedMarket = normalizeMarket(market);
    const normalizedTicker = normalizeTicker(ticker);
    return getAlerts().filter((item) => item.market === normalizedMarket && item.ticker === normalizedTicker);
  }

  function createAlertFromTemplate(templateId, target) {
    const template = ALERT_TEMPLATES.find((item) => item.id === templateId) || ALERT_TEMPLATES[0];
    return createAlert({
      market: normalizeMarket(target.market || 'US'),
      ticker: normalizeTicker(target.ticker),
      label: `${normalizeTicker(target.ticker)} ${template.label}`,
      type: template.type,
      category: template.category,
      description: template.description,
      channel: template.channel,
      frequency: template.frequency,
      threshold: template.threshold,
      groupId: target.groupId || 'core-holdings',
      groupLabel: target.groupLabel || '核心底仓',
      sourceContext: target.sourceContext || template.id,
    });
  }

  function upsertValuationAlert(ticker, market, companyName, extra) {
    const normalizedMarket = normalizeMarket(market);
    const normalizedTicker = normalizeTicker(ticker);
    const type = extra && extra.type ? String(extra.type) : 'undervalue_zone';
    const id = `${normalizedMarket}-${normalizedTicker}-${type}`;
    const existing = getAlerts().find((item) => item.id === id);
    if (existing) {
      const target = toggleAlertEnabled(id);
      return { active: Boolean(target && target.enabled), alert: target };
    }
    const created = createAlert({
      id,
      market: normalizedMarket,
      ticker: normalizedTicker,
      type,
      category: inferAlertCategory(type),
      label: `${companyName || normalizedTicker} ${inferAlertLabel(type)}`,
      description: extra && extra.description ? extra.description : inferAlertLabel(type),
      channel: extra && extra.channel ? extra.channel : '站内信 + 邮件',
      frequency: extra && extra.frequency ? extra.frequency : '实时',
      threshold: extra && extra.threshold ? extra.threshold : '默认阈值',
      groupId: extra && extra.groupId ? extra.groupId : 'core-holdings',
      groupLabel: extra && extra.groupLabel ? extra.groupLabel : '核心底仓',
      sourceContext: extra && extra.sourceContext ? extra.sourceContext : 'detail-page',
      enabled: true,
    });
    return { active: true, alert: created };
  }

  function getAlertTemplates() {
    return ALERT_TEMPLATES.slice();
  }

  function rememberRecentSymbol(market, ticker) {
    const current = getWorkspaceMeta().recentSymbols || [];
    const entry = `${normalizeMarket(market)}:${normalizeTicker(ticker)}`;
    const filtered = current.filter((item) => item !== entry);
    filtered.unshift(entry);
    return filtered.slice(0, 8);
  }

  function recordRecentSymbol(market, ticker) {
    return saveWorkspaceMeta({ recentSymbols: rememberRecentSymbol(market, ticker) });
  }

  function getWorkspaceSummary() {
    const watchlist = getWatchlist();
    const alerts = getAlerts();
    const groups = getGroups();
    const meta = getWorkspaceMeta();
    return {
      watchlistCount: watchlist.length,
      alertCount: alerts.length,
      enabledAlertCount: alerts.filter((item) => item.enabled).length,
      groupCount: groups.length,
      reminderQuota: meta.reminderQuota,
      reminderUsed: alerts.length,
      currentPlan: meta.currentPlan,
      expiresAt: meta.expiresAt,
      recentSymbols: meta.recentSymbols || [],
      storageMode: meta.storageMode || 'browser_local',
      lastSyncedAt: meta.lastSyncedAt || null,
    };
  }

  function buildWorkspaceState() {
    return {
      schemaVersion: 2,
      groups: getGroups(),
      watchlist: getWatchlist(),
      alerts: getAlerts(),
      savedContexts: getSavedContexts(),
      workspaceMeta: {
        ...getWorkspaceMeta(),
        workspaceKey: getWorkspaceKey(),
      },
    };
  }

  function hasRemoteState(state) {
    const snapshot = mapOrEmpty(state);
    return listOrEmpty(snapshot.groups).length > 0
      || listOrEmpty(snapshot.watchlist).length > 0
      || listOrEmpty(snapshot.alerts).length > 0
      || listOrEmpty(snapshot.savedContexts).length > 0
      || Object.keys(mapOrEmpty(snapshot.workspaceMeta)).length > 0;
  }

  function setWorkspaceMetaLocal(meta) {
    writeJson(WORKSPACE_META_KEY, {
      ...getWorkspaceMeta(),
      ...mapOrEmpty(meta),
      workspaceKey: getWorkspaceKey(),
      updatedAt: nowIso(),
    });
  }

  function applyWorkspaceState(snapshot) {
    const state = mapOrEmpty(snapshot);
    const groups = listOrEmpty(state.groups).length
      ? listOrEmpty(state.groups).map(normalizeGroupRecord)
      : DEFAULT_GROUPS.map(normalizeGroupRecord);
    saveGroups(groups);
    saveWatchlist(listOrEmpty(state.watchlist).map((item) => normalizeWatchlistRecord(item, groups)));
    saveAlerts(listOrEmpty(state.alerts).map((item) => normalizeAlertRecord(item, groups)));
    saveSavedContexts(listOrEmpty(state.savedContexts));
    setWorkspaceMetaLocal({
      ...mapOrEmpty(state.workspaceMeta),
      storageMode: 'server_sync',
      lastSyncError: null,
      lastSyncedAt: mapOrEmpty(state.workspaceMeta).lastSyncedAt || nowIso(),
    });
  }

  async function pushRemoteState(reason) {
    const fetchJson = global.FairvaluePlatformApi?.fetchJson;
    if (typeof fetchJson !== 'function') {
      return null;
    }
    try {
      const response = await fetchJson('/v1/member/workspace', {
        method: 'PUT',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          workspaceKey: getWorkspaceKey(),
          state: buildWorkspaceState(),
          reason: reason || 'workspace-sync',
        }),
      });
      setWorkspaceMetaLocal({
        storageMode: 'server_sync',
        lastSyncError: null,
        lastSyncedAt: response?.updatedAt || nowIso(),
      });
      emitChange('workspace-sync', {
        reason: reason || 'workspace-sync',
        updatedAt: response?.updatedAt || null,
      });
      return response || null;
    } catch (error) {
      setWorkspaceMetaLocal({
        storageMode: 'browser_fallback',
        lastSyncError: error?.message || 'workspace_sync_failed',
      });
      emitChange('workspace-sync-error', {
        reason: reason || 'workspace-sync',
        message: error?.message || 'workspace_sync_failed',
      });
      return null;
    }
  }

  function scheduleRemoteSync(reason) {
    if (!workspaceRemoteHydrated) {
      return;
    }
    if (workspaceSyncTimer) {
      global.clearTimeout(workspaceSyncTimer);
    }
    workspaceSyncTimer = global.setTimeout(() => {
      workspaceSyncInFlight = workspaceSyncInFlight.then(() => pushRemoteState(reason));
    }, 180);
  }

  async function hydrateRemoteState() {
    const fetchJson = global.FairvaluePlatformApi?.fetchJson;
    if (typeof fetchJson !== 'function') {
      setWorkspaceMetaLocal({ storageMode: 'browser_local' });
      workspaceRemoteHydrated = true;
      workspaceReadyResolve(true);
      return;
    }
    try {
      const response = await fetchJson(`/v1/member/workspace?workspaceKey=${encodeURIComponent(getWorkspaceKey())}`);
      if (response?.persisted && hasRemoteState(response.state)) {
        applyWorkspaceState(response.state);
      } else {
        setWorkspaceMetaLocal({ storageMode: 'browser_local' });
      }
    } catch (error) {
      setWorkspaceMetaLocal({
        storageMode: 'browser_fallback',
        lastSyncError: error?.message || 'workspace_bootstrap_failed',
      });
    } finally {
      workspaceRemoteHydrated = true;
      workspaceReadyResolve(true);
      if (getWorkspaceMeta().storageMode !== 'server_sync') {
        scheduleRemoteSync('workspace-bootstrap');
      }
    }
  }

  function migrateLegacyData() {
    if (global.localStorage.getItem(SEEDED_KEY) === '1') {
      return;
    }

    const groups = DEFAULT_GROUPS.map(normalizeGroupRecord);
    saveGroups(groups);

    const legacyWatchlist = readJson(LEGACY_WATCHLIST_KEY, []);
    const migratedWatchlist = legacyWatchlist.map((item, index) => normalizeWatchlistRecord({
      ...item,
      groupId: index === 2 ? 'trend-setup' : 'core-holdings',
      groupLabel: index === 2 ? '短线趋势' : '核心底仓',
      tag: index === 2 ? '趋势跟踪' : '核心底仓',
      sourceContext: 'legacy-watchlist',
    }, groups));

    const seededWatchlist = migratedWatchlist.length ? migratedWatchlist : [
      normalizeWatchlistRecord({ market: 'US', ticker: 'AAPL', companyName: 'Apple Inc.', thesis: '现金流质量高，适合作为低估跟踪样本。', groupId: 'core-holdings', groupLabel: '核心底仓', tag: '核心底仓' }, groups),
      normalizeWatchlistRecord({ market: 'US', ticker: 'MSFT', companyName: 'Microsoft Corp.', thesis: '高质量复利资产，适合做估值与趋势协同观察。', groupId: 'core-holdings', groupLabel: '核心底仓', tag: '核心底仓' }, groups),
      normalizeWatchlistRecord({ market: 'US', ticker: 'NVDA', companyName: 'NVIDIA Corp.', thesis: '高景气龙头，适合观察高预期下的风险回报。', groupId: 'trend-setup', groupLabel: '短线趋势', tag: '趋势观察' }, groups),
    ];
    saveWatchlist(seededWatchlist);

    const legacyAlerts = readJson(LEGACY_ALERTS_KEY, []);
    const migratedAlerts = legacyAlerts.map((item) => normalizeAlertRecord({
      ...item,
      category: inferAlertCategory(item.type),
      threshold: item.frequency || '默认阈值',
      groupId: item.ticker === 'NVDA' ? 'trend-setup' : 'core-holdings',
      groupLabel: item.ticker === 'NVDA' ? '短线趋势' : '核心底仓',
      sourceContext: 'legacy-alerts',
    }, groups));

    const seededAlerts = migratedAlerts.length ? migratedAlerts : [
      normalizeAlertRecord({ market: 'US', ticker: 'AAPL', type: 'undervalue_zone', category: 'price', label: 'AAPL 低估区提醒', channel: '站内信 + 邮件', frequency: '实时', threshold: '进入低估区', groupId: 'core-holdings', groupLabel: '核心底仓' }, groups),
      normalizeAlertRecord({ market: 'US', ticker: 'MSFT', type: 'earnings_window', category: 'event', label: 'MSFT 财报临近提醒', channel: '站内信', frequency: '财报前 3 天', threshold: '财报前 3 天', groupId: 'core-holdings', groupLabel: '核心底仓' }, groups),
      normalizeAlertRecord({ market: 'US', ticker: 'NVDA', type: 'trend_breakout', category: 'trend', label: 'NVDA 趋势突破提醒', channel: '站内信', frequency: '实时', threshold: '突破关键压力位', groupId: 'trend-setup', groupLabel: '短线趋势' }, groups),
    ];
    saveAlerts(seededAlerts);

    const legacyContextRaw = global.localStorage.getItem(LEGACY_CONTEXT_KEY);
    if (legacyContextRaw) {
      try {
        const parsed = JSON.parse(legacyContextRaw);
        saveSavedContexts([{
          id: 'legacy-screener-context',
          title: '上次筛选条件',
          market: normalizeMarket(parsed.market || (Array.isArray(parsed.markets) ? parsed.markets[0] : 'US')),
          description: '从旧版筛选器自动迁移的最近一次工作上下文。',
          summary: parsed.sortBy ? `排序：${parsed.sortBy}` : '保留最近一次筛选设置',
          filters: parsed.filters || parsed.filterValues || {},
          resultCount: parsed.limit || null,
          updatedAt: nowIso(),
        }]);
      } catch (_error) {
        saveSavedContexts([]);
      }
    } else {
      saveSavedContexts([]);
    }

    saveWorkspaceMeta({
      currentPlan: '专业版',
      expiresAt: '2025-12-31',
      reminderQuota: 20,
      upgradedHint: '升级至尊版可解锁无限量提醒规则与深度 AI 解读。',
    });

    global.localStorage.setItem(SEEDED_KEY, '1');
  }

  migrateLegacyData();
  ensureWorkspaceKey();
  hydrateRemoteState();

  global.FairvalueTracker = {
    ready: workspaceReady,
    getWatchlist,
    getGroupedWatchlist,
    getGroups,
    createGroup,
    upsertWatchlist,
    bulkUpsertWatchlist,
    removeFromWatchlist,
    hasInWatchlist,
    toggleWatchlist,
    getAlerts,
    createAlert,
    removeAlert,
    toggleAlertEnabled,
    listAlertsForTicker,
    upsertValuationAlert,
    createAlertFromTemplate,
    getAlertTemplates,
    getSavedContexts,
    saveScreenerContext,
    getWorkspaceMeta,
    saveWorkspaceMeta,
    saveFlowContext,
    recordRecentSymbol,
    getWorkspaceSummary,
    getWorkspaceKey,
    syncNow: () => pushRemoteState('manual'),
  };
})(window);
