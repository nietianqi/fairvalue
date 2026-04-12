'use strict';

(function initFairvalueShell(global, document) {
  const modalState = {
    onOpen: null,
    onClose: null,
  };

  document.addEventListener('DOMContentLoaded', () => {
    bindGlobalSearch();
    ensureUiRoots();
  });

  function bindGlobalSearch() {
    const form = document.querySelector('[data-global-search]');
    if (!form) {
      return;
    }

    form.addEventListener('submit', (event) => {
      event.preventDefault();
      const input = form.querySelector('input[name="q"]');
      const raw = input ? String(input.value || '').trim() : '';
      if (!raw) {
        return;
      }

      const normalized = raw.toUpperCase();
      let target = `/us-stock-detail.html?ticker=${encodeURIComponent(normalized)}`;

      if (/^\d{6}$/.test(raw)) {
        target = `/cn-stock-detail.html?ticker=${encodeURIComponent(raw)}`;
      } else if (/^\d{4}$/.test(raw)) {
        target = `/jp-stock-detail.html?code=${encodeURIComponent(raw)}`;
      } else if (/[\u4e00-\u9fa5]/.test(raw)) {
        target = '/cn-undervalued-stocks.html';
      }

      global.location.href = target;
    });
  }

  function ensureUiRoots() {
    if (!document.getElementById('fvModalRoot')) {
      const modalRoot = document.createElement('div');
      modalRoot.id = 'fvModalRoot';
      modalRoot.className = 'fv-modal-root';
      modalRoot.innerHTML = `
        <div class="fv-modal-backdrop" data-fv-modal-close hidden></div>
        <section class="fv-modal-shell" role="dialog" aria-modal="true" hidden>
          <button class="fv-modal-close" type="button" aria-label="关闭" data-fv-modal-close>×</button>
          <div class="fv-modal-head">
            <div class="fv-modal-kicker" id="fvModalKicker"></div>
            <h2 class="fv-modal-title" id="fvModalTitle"></h2>
            <p class="fv-modal-subtitle" id="fvModalSubtitle"></p>
          </div>
          <div class="fv-modal-body" id="fvModalBody"></div>
          <div class="fv-modal-footer" id="fvModalFooter"></div>
        </section>
      `;
      document.body.appendChild(modalRoot);
      modalRoot.addEventListener('click', (event) => {
        if (event.target.closest('[data-fv-modal-close]')) {
          closeModal();
        }
      });
      document.addEventListener('keydown', (event) => {
        if (event.key === 'Escape') {
          closeModal();
        }
      });
    }

    if (!document.getElementById('fvToastRoot')) {
      const toastRoot = document.createElement('div');
      toastRoot.id = 'fvToastRoot';
      toastRoot.className = 'fv-toast-root';
      document.body.appendChild(toastRoot);
    }
  }

  function openModal(options) {
    ensureUiRoots();
    const root = document.getElementById('fvModalRoot');
    const backdrop = root.querySelector('.fv-modal-backdrop');
    const shell = root.querySelector('.fv-modal-shell');
    const kicker = document.getElementById('fvModalKicker');
    const title = document.getElementById('fvModalTitle');
    const subtitle = document.getElementById('fvModalSubtitle');
    const body = document.getElementById('fvModalBody');
    const footer = document.getElementById('fvModalFooter');

    kicker.textContent = options.kicker || '';
    kicker.style.display = options.kicker ? '' : 'none';
    title.textContent = options.title || '工作台';
    subtitle.textContent = options.subtitle || '';
    subtitle.style.display = options.subtitle ? '' : 'none';
    body.innerHTML = options.bodyHtml || '';
    footer.innerHTML = options.footerHtml || '';
    footer.style.display = options.footerHtml ? '' : 'none';

    shell.className = `fv-modal-shell ${options.size ? `is-${options.size}` : 'is-md'} ${options.classes || ''}`.trim();
    root.classList.add('is-open');
    backdrop.hidden = false;
    shell.hidden = false;
    modalState.onOpen = typeof options.onOpen === 'function' ? options.onOpen : null;
    modalState.onClose = typeof options.onClose === 'function' ? options.onClose : null;
    if (modalState.onOpen) {
      modalState.onOpen(shell);
    }
  }

  function closeModal() {
    const root = document.getElementById('fvModalRoot');
    if (!root) {
      return;
    }
    const backdrop = root.querySelector('.fv-modal-backdrop');
    const shell = root.querySelector('.fv-modal-shell');
    root.classList.remove('is-open');
    backdrop.hidden = true;
    shell.hidden = true;
    const onClose = modalState.onClose;
    modalState.onOpen = null;
    modalState.onClose = null;
    if (onClose) {
      onClose();
    }
  }

  function toast(message, tone) {
    ensureUiRoots();
    const root = document.getElementById('fvToastRoot');
    const toastNode = document.createElement('div');
    toastNode.className = `fv-toast ${tone ? `is-${tone}` : ''}`.trim();
    toastNode.textContent = message;
    root.appendChild(toastNode);
    global.setTimeout(() => {
      toastNode.classList.add('is-visible');
    }, 10);
    global.setTimeout(() => {
      toastNode.classList.remove('is-visible');
      global.setTimeout(() => toastNode.remove(), 220);
    }, 2800);
  }

  function showUpgradeModal(config) {
    const features = Array.isArray(config.features) ? config.features : [];
    openModal({
      kicker: config.kicker || 'Membership Upgrade',
      title: config.title || '解锁更多会员能力',
      subtitle: config.subtitle || '当前模块提供预览，升级后可以继续在原位置查看完整内容。',
      size: 'md',
      bodyHtml: `
        <div class="fv-upgrade-panel">
          <div class="fv-upgrade-note">${escapeHtml(config.note || '锁定的是更深的分析能力，而不是整页访问权限。')}</div>
          <div class="fv-upgrade-list">
            ${features.map((feature) => `<div class="fv-upgrade-item">${escapeHtml(feature)}</div>`).join('')}
          </div>
        </div>
      `,
      footerHtml: `
        <a class="app-primary-btn" href="${escapeAttr(config.href || '/membership-pricing.html')}">${escapeHtml(config.ctaText || '查看会员与升级')}</a>
        <button class="app-pill-btn" type="button" data-fv-modal-close>稍后再看</button>
      `,
    });
  }

  function renderLockedCard(config) {
    const items = Array.isArray(config.features) ? config.features : [];
    return `
      <section class="fv-locked-card ${escapeAttr(config.tone || '')}">
        <div class="fv-locked-icon">🔒</div>
        <div class="fv-locked-copy">
          <span class="section-kicker">${escapeHtml(config.kicker || 'Premium Insight')}</span>
          <h3>${escapeHtml(config.title || '解锁深度模块')}</h3>
          <p>${escapeHtml(config.description || '当前保留结果预览，升级后即可解锁完整解释、更多历史和更细维度。')}</p>
        </div>
        ${items.length ? `<div class="fv-locked-features">${items.map((item) => `<span>${escapeHtml(item)}</span>`).join('')}</div>` : ''}
        <div class="fv-locked-actions">
          <button class="app-primary-btn" type="button" data-fv-upgrade-trigger>${escapeHtml(config.ctaText || '解锁完整能力')}</button>
        </div>
      </section>
    `;
  }

  function bindUpgradeTriggers(root, configFactory) {
    root.querySelectorAll('[data-fv-upgrade-trigger]').forEach((button) => {
      button.addEventListener('click', () => {
        const config = typeof configFactory === 'function' ? configFactory(button) : configFactory;
        showUpgradeModal(config || {});
      });
    });
  }

  function escapeHtml(value) {
    return String(value == null ? '' : value)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
  }

  function escapeAttr(value) {
    return escapeHtml(value);
  }

  global.FairvalueUi = {
    openModal,
    closeModal,
    toast,
    showUpgradeModal,
    renderLockedCard,
    bindUpgradeTriggers,
    escapeHtml,
  };
})(window, document);
