'use strict';

(function initFairvaluePlatformApi(global) {
  let bootstrapPromise = null;

  function normalizeBootstrap(payload) {
    const source = payload && typeof payload === 'object' ? payload : {};
    return {
      asOf: source.asOf || source.as_of || null,
      apiKeyHeader: source.apiKeyHeader || source.api_key_header || 'X-API-Key',
      apiKey: source.apiKey || source.api_key || '',
      envelopeHeader: source.envelopeHeader || source.envelope_header || 'X-Use-Envelope',
      forceEnvelope: source.forceEnvelope ?? source.force_envelope ?? true,
      clientName: source.clientName || source.client_name || '',
      planCode: source.planCode || source.plan_code || '',
      requestsPerMinute: source.requestsPerMinute || source.requests_per_minute || 0,
      entitlements: Array.isArray(source.entitlements) ? source.entitlements : [],
      bootstrapError: source.bootstrapError || source.bootstrap_error || null,
    };
  }

  async function getBootstrap() {
    if (!bootstrapPromise) {
      bootstrapPromise = fetch('/platform/bootstrap', {
        headers: {
          'Accept': 'application/json',
        },
      }).then(async (response) => {
        const payload = await response.json();
        if (!response.ok) {
          throw new Error(payload.message || `Bootstrap failed: HTTP ${response.status}`);
        }
        return normalizeBootstrap(payload);
      }).catch((error) => {
        bootstrapPromise = Promise.resolve(normalizeBootstrap({
          apiKeyHeader: 'X-API-Key',
          apiKey: '',
          envelopeHeader: 'X-Use-Envelope',
          forceEnvelope: true,
          bootstrapError: error.message || 'bootstrap_failed',
        }));
        return bootstrapPromise;
      });
    }
    return bootstrapPromise;
  }

  function unwrapApiEnvelope(payload) {
    if (payload && typeof payload === 'object' && 'success' in payload) {
      return payload.success === false ? (payload.error || payload) : payload.data;
    }
    return payload;
  }

  async function fetchJson(url, options = {}) {
    const bootstrap = await getBootstrap();
    const headers = new Headers(options.headers || {});
    headers.set('Accept', 'application/json');
    if (bootstrap.envelopeHeader && !headers.has(bootstrap.envelopeHeader)) {
      headers.set(bootstrap.envelopeHeader, 'true');
    }
    if (bootstrap.apiKeyHeader && bootstrap.apiKey && !headers.has(bootstrap.apiKeyHeader)) {
      headers.set(bootstrap.apiKeyHeader, bootstrap.apiKey);
    }
    const response = await fetch(url, { ...options, headers });
    const payload = await response.json();
    const normalized = unwrapApiEnvelope(payload);
    if (!response.ok) {
      const errorPayload = payload && payload.error ? payload.error : payload;
      throw new Error(errorPayload.message || payload.message || `HTTP ${response.status}`);
    }
    return normalized;
  }

  global.FairvaluePlatformApi = {
    getBootstrap,
    fetchJson,
    unwrapApiEnvelope,
  };
})(window);
