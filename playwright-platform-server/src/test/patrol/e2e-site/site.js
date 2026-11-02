/*
 * 巡页端到端验证站点交互脚本。
 * 覆盖：弹层、多级弹窗、日期/时间/级联/树选择、表格、拖拽、分栏、异步状态等。
 */
'use strict';

const $ = (selector, root = document) => root.querySelector(selector);
const $$ = (selector, root = document) => Array.from(root.querySelectorAll(selector));

function toast(message, type = 'info') {
  let stack = $('.toast-stack');
  if (!stack) {
    stack = document.createElement('div');
    stack.className = 'toast-stack';
    document.body.appendChild(stack);
  }
  const item = document.createElement('div');
  item.className = 'toast toast--' + type;
  item.setAttribute('role', 'status');
  item.textContent = message;
  stack.appendChild(item);
  setTimeout(() => item.remove(), 3200);
}
window.toast = toast;

function setActiveNav() {
  const path = window.location.pathname;
  $$('.topbar a').forEach((link) => {
    const href = link.getAttribute('href');
    if (href === path || (href === '/' && path === '/')) {
      link.classList.add('active');
    }
  });
}

/* ------------------------------------------------------------------ 弹层 */
function initPopups() {
  $$('[data-popup]').forEach((trigger) => {
    trigger.addEventListener('click', (event) => {
      event.preventDefault();
      const target = document.getElementById(trigger.dataset.popup);
      if (!target) return;
      const willOpen = target.hidden;
      if (willOpen) {
        const group = trigger.dataset.popupGroup;
        if (group) {
          $$('[data-popup-group="' + group + '"]').forEach((layer) => {
            layer.hidden = true;
          });
        }
      }
      target.hidden = !willOpen;
      trigger.setAttribute('aria-expanded', String(willOpen));
    });
  });

  document.addEventListener('click', (event) => {
    $$('.popup-layer').forEach((layer) => {
      if (layer.hidden) return;
      const trigger = document.querySelector('[data-popup="' + layer.id + '"]');
      if (layer.contains(event.target)) return;
      if (trigger && trigger.contains(event.target)) return;
      layer.hidden = true;
      if (trigger) trigger.setAttribute('aria-expanded', 'false');
    });
  });

  document.addEventListener('keydown', (event) => {
    if (event.key !== 'Escape') return;
    $$('.popup-layer').forEach((layer) => {
      layer.hidden = true;
    });
    $$('[data-popup]').forEach((trigger) => trigger.setAttribute('aria-expanded', 'false'));
  });
}

/* ------------------------------------------------------------ 自定义选择器 */
function initSelects() {
  $$('.select-popup').forEach((popup) => {
    $$('[data-value]', popup).forEach((option) => {
      option.addEventListener('click', () => {
        const host = popup.closest('.layer-host');
        const label = host ? $('[data-select-label]', host) : null;
        const valueInput = host ? $('[data-select-value]', host) : null;
        if (label) label.textContent = option.dataset.label || option.textContent.trim();
        if (valueInput) valueInput.value = option.dataset.value || '';
        popup.hidden = true;
        const trigger = host ? $('[data-popup]', host) : null;
        if (trigger) trigger.setAttribute('aria-expanded', 'false');
        toast('已选择：' + (option.dataset.label || option.textContent.trim()));
      });
    });
  });

  $$('[data-multi-select]').forEach((host) => {
    const popup = $('.popup-layer', host);
    const label = $('[data-multi-label]', host);
    if (!popup || !label) return;
    $$('[data-multi-value]', popup).forEach((option) => {
      option.addEventListener('click', () => {
        option.classList.toggle('active');
        option.setAttribute('aria-selected', option.classList.contains('active') ? 'true' : 'false');
        const selected = $$('[data-multi-value].active', popup).map((item) => item.dataset.multiValue);
        label.textContent = selected.length ? selected.join('、') : '请选择';
      });
    });
  });
}

/* ---------------------------------------------------------------- 日期选择 */
function initDatepickers() {
  $$('[data-datepicker]').forEach((host) => {
    const input = $('input', host);
    const panel = $('.datepicker-popup', host);
    const grid = $('[data-date-grid]', host);
    const title = $('[data-date-title]', host);
    const yearPanel = $('.year-popup', host);
    if (!input || !panel || !grid || !title) return;
    let year = 2026;
    let month = 3;

    function render() {
      title.textContent = year + ' 年 ' + month + ' 月';
      grid.innerHTML = '';
      const firstDay = new Date(year, month - 1, 1).getDay();
      for (let blank = 0; blank < firstDay; blank += 1) {
        grid.appendChild(document.createElement('span'));
      }
      const days = new Date(year, month, 0).getDate();
      for (let day = 1; day <= days; day += 1) {
        const button = document.createElement('button');
        button.type = 'button';
        button.textContent = String(day);
        button.setAttribute('data-day', String(day));
        button.setAttribute('aria-label', year + ' 年 ' + month + ' 月 ' + day + ' 日');
        button.addEventListener('click', () => {
          input.value = year + '-' + String(month).padStart(2, '0') + '-' + String(day).padStart(2, '0');
          panel.hidden = true;
          if (yearPanel) yearPanel.hidden = true;
        });
        grid.appendChild(button);
      }
    }

    const prev = $('[data-date-prev]', host);
    const next = $('[data-date-next]', host);
    if (prev) prev.addEventListener('click', () => {
      month -= 1;
      if (month < 1) { month = 12; year -= 1; }
      render();
    });
    if (next) next.addEventListener('click', () => {
      month += 1;
      if (month > 12) { month = 1; year += 1; }
      render();
    });
    const today = $('[data-date-today]', host);
    if (today) today.addEventListener('click', () => {
      const now = new Date();
      input.value = now.getFullYear() + '-' + String(now.getMonth() + 1).padStart(2, '0') + '-' + String(now.getDate()).padStart(2, '0');
      panel.hidden = true;
    });
    $$('[data-year]', host).forEach((button) => {
      button.addEventListener('click', () => {
        year = Number(button.dataset.year);
        if (yearPanel) yearPanel.hidden = true;
        render();
      });
    });
    render();
  });
}

/* ---------------------------------------------------------------- 时间选择 */
function initTimepickers() {
  $$('[data-timepicker]').forEach((host) => {
    const input = $('input', host);
    const panel = $('.timepicker-popup', host);
    if (!input || !panel) return;
    $$('[data-hour]', host).forEach((button) => {
      button.addEventListener('click', () => {
        $$('[data-hour]', host).forEach((item) => item.classList.remove('active'));
        button.classList.add('active');
        const minute = $('[data-minute].active', host);
        input.value = String(button.dataset.hour).padStart(2, '0') + ':' + (minute ? minute.dataset.minute : '00');
      });
    });
    $$('[data-minute]', host).forEach((button) => {
      button.addEventListener('click', () => {
        $$('[data-minute]', host).forEach((item) => item.classList.remove('active'));
        button.classList.add('active');
        const hour = $('[data-hour].active', host);
        input.value = (hour ? String(hour.dataset.hour).padStart(2, '0') : '00') + ':' + String(button.dataset.minute).padStart(2, '0');
        panel.hidden = true;
      });
    });
  });
}

/* ---------------------------------------------------------------- 级联选择 */
function initCascaders() {
  $$('[data-cascader]').forEach((host) => {
    const input = $('input', host);
    const popup = $('.cascader-popup', host);
    if (!input || !popup) return;
    const columns = $$('.cascade-col', popup);
    const selected = [];

    $$('.cascade-col li', popup).forEach((item) => {
      item.addEventListener('click', () => {
        const column = item.closest('.cascade-col');
        const level = Number(column.dataset.level || 0);
        $$('li', column).forEach((node) => node.classList.remove('active'));
        item.classList.add('active');
        selected[level] = item.dataset.label || item.textContent.trim();
        selected.length = level + 1;
        columns.forEach((col) => {
          if (Number(col.dataset.level) > level) col.hidden = true;
        });
        if (item.dataset.next) {
          const next = columns.find((col) => Number(col.dataset.level) === Number(item.dataset.next));
          if (next) next.hidden = false;
          input.value = selected.filter(Boolean).join(' / ');
        } else {
          input.value = selected.filter(Boolean).join(' / ');
          popup.hidden = true;
        }
      });
    });
  });
}

/* ------------------------------------------------------------------ 树选择 */
function initTrees() {
  $$('[data-tree]').forEach((host) => {
    $$('[data-tree-toggle]', host).forEach((button) => {
      button.addEventListener('click', () => {
        const branch = button.closest('li');
        const sub = branch ? $('ul', branch) : null;
        if (!sub) return;
        sub.hidden = !sub.hidden;
        button.textContent = sub.hidden ? '+' : '−';
        button.setAttribute('aria-expanded', String(!sub.hidden));
      });
    });
    const input = $('input', host);
    $$('input[type="checkbox"][data-tree-value]', host).forEach((box) => {
      box.addEventListener('change', () => {
        if (!input) return;
        const values = $$('input[type="checkbox"][data-tree-value]:checked', host)
          .map((item) => item.dataset.treeValue);
        input.value = values.join('、');
      });
    });
  });
}

/* -------------------------------------------------------------- 弹窗 / 抽屉 */
function initModals() {
  $$('[data-modal]').forEach((trigger) => {
    trigger.addEventListener('click', () => {
      const layer = document.getElementById(trigger.dataset.modal);
      if (layer) layer.hidden = false;
    });
  });
  $$('.modal-mask').forEach((mask) => {
    mask.addEventListener('click', (event) => {
      if (event.target === mask && mask.dataset.static !== 'true') mask.hidden = true;
    });
    $$('[data-modal-close]', mask).forEach((button) => {
      button.addEventListener('click', () => {
        const owner = button.closest('.modal-mask');
        if (owner) owner.hidden = true;
      });
    });
  });
  $$('[data-drawer]').forEach((trigger) => {
    trigger.addEventListener('click', () => {
      const layer = document.getElementById(trigger.dataset.drawer);
      if (layer) layer.hidden = false;
    });
  });
  $$('.drawer-mask').forEach((mask) => {
    mask.addEventListener('click', (event) => {
      if (event.target === mask) mask.hidden = true;
    });
    $$('[data-drawer-close]', mask).forEach((button) => {
      button.addEventListener('click', () => {
        const owner = button.closest('.drawer-mask');
        if (owner) owner.hidden = true;
      });
    });
  });

  document.addEventListener('keydown', (event) => {
    if (event.key !== 'Escape') return;
    const modals = $$('.modal-mask').filter((item) => !item.hidden);
    if (modals.length) {
      modals[modals.length - 1].hidden = true;
      return;
    }
    const drawers = $$('.drawer-mask').filter((item) => !item.hidden);
    if (drawers.length) drawers[drawers.length - 1].hidden = true;
  });

  document.addEventListener('click', (event) => {
    const next = event.target.closest('[data-dynamic-next]');
    if (next) {
      openDynamicModal(Number(next.dataset.dynamicNext || 1) + 1);
    }
    const close = event.target.closest('[data-dynamic-close]');
    if (close) {
      const mask = close.closest('.modal-mask');
      if (mask) mask.remove();
    }
  });

  $$('[data-dynamic-modal]').forEach((trigger) => {
    trigger.addEventListener('click', () => openDynamicModal(Number(trigger.dataset.dynamicModal || 1)));
  });
}

function openDynamicModal(depth) {
  const mask = document.createElement('div');
  mask.className = 'modal-mask';
  mask.dataset.dynamicLevel = String(depth);
  const nextButton = depth < 4
    ? '<button type="button" class="btn" data-dynamic-next="' + depth + '">打开第 ' + (depth + 1) + ' 层</button>'
    : '<button type="button" class="btn-secondary" data-dynamic-close>已是最后一层</button>';
  mask.innerHTML = [
    '<div class="modal" role="dialog" aria-modal="true" aria-label="动态第 ' + depth + ' 层弹窗">',
    '  <div class="modal-head"><h3>动态第 ' + depth + ' 层弹窗</h3><button type="button" class="modal-close" data-dynamic-close aria-label="关闭">×</button></div>',
    '  <div class="modal-body"><p>本弹层由点击后动态创建，用于验证自主探索对未预渲染弹层的发现能力。</p>',
    '    <div class="field"><label for="dynamic-input-' + depth + '">层级备注</label><input id="dynamic-input-' + depth + '" type="text" placeholder="第 ' + depth + ' 层输入" /></div>',
    '  </div>',
    '  <div class="modal-foot"><button type="button" class="btn-secondary" data-dynamic-close>关闭</button>' + nextButton + '</div>',
    '</div>',
  ].join('');
  document.body.appendChild(mask);
}

/* ------------------------------------------------------------ 右键菜单 */
function initContextMenu() {
  const area = $('[data-context-area]');
  if (!area) return;
  let menu = null;

  area.addEventListener('contextmenu', (event) => {
    event.preventDefault();
    if (menu) menu.remove();
    menu = document.createElement('div');
    menu.className = 'context-menu popup-layer';
    menu.setAttribute('role', 'menu');
    menu.innerHTML = [
      '<div class="menu-item" data-context-action="复制">复制</div>',
      '<div class="menu-item" data-context-action="重命名">重命名</div>',
      '<div class="menu-item" data-context-action="移动">移动到…</div>',
      '<div class="menu-item" data-context-action="删除">删除</div>',
    ].join('');
    menu.style.left = Math.min(event.clientX, window.innerWidth - 200) + 'px';
    menu.style.top = Math.min(event.clientY, window.innerHeight - 180) + 'px';
    document.body.appendChild(menu);
    $$('[data-context-action]', menu).forEach((item) => {
      item.addEventListener('click', () => {
        toast('右键操作：' + item.dataset.contextAction, item.dataset.contextAction === '删除' ? 'warning' : 'info');
        menu.remove();
        menu = null;
      });
    });
  });

  document.addEventListener('click', (event) => {
    if (menu && !menu.contains(event.target)) {
      menu.remove();
      menu = null;
    }
  });
  document.addEventListener('keydown', (event) => {
    if (event.key === 'Escape' && menu) {
      menu.remove();
      menu = null;
    }
  });
}

/* ----------------------------------------------------------------- Tooltip */
function initTooltips() {
  $$('[data-tooltip-trigger]').forEach((trigger) => {
    const tooltip = $('.tooltip', trigger.parentElement);
    if (!tooltip) return;
    const show = () => { tooltip.hidden = false; };
    const hide = () => { tooltip.hidden = true; };
    trigger.addEventListener('mouseenter', show);
    trigger.addEventListener('focus', show);
    trigger.addEventListener('mouseleave', hide);
    trigger.addEventListener('blur', hide);
  });
}

/* ---------------------------------------------------------- 图片预览 / loading */
function initOverlays() {
  $$('[data-lightbox-src]').forEach((trigger) => {
    trigger.addEventListener('click', () => {
      const box = document.createElement('div');
      box.className = 'lightbox';
      box.innerHTML = '<button type="button" class="lightbox-close" aria-label="关闭">×</button>' +
        '<img src="' + trigger.dataset.lightboxSrc + '" alt="图片预览" />';
      const close = () => box.remove();
      $('.lightbox-close', box).addEventListener('click', close);
      box.addEventListener('click', (event) => { if (event.target === box) close(); });
      document.addEventListener('keydown', function onKey(event) {
        if (event.key === 'Escape') { close(); document.removeEventListener('keydown', onKey); }
      });
      document.body.appendChild(box);
    });
  });

  $$('[data-loading]').forEach((trigger) => {
    trigger.addEventListener('click', () => {
      let overlay = $('.loading-overlay');
      if (!overlay) {
        overlay = document.createElement('div');
        overlay.className = 'loading-overlay';
        overlay.innerHTML = '<span class="spinner"></span><span>处理中，请稍候…</span>';
        document.body.appendChild(overlay);
      }
      overlay.hidden = false;
      setTimeout(() => { overlay.hidden = true; }, 900);
    });
  });
}

/* -------------------------------------------------------------- Tabs / 折叠 */
function initTabs() {
  $$('[data-tabs]').forEach((container) => {
    const buttons = $$('[data-tab]', container);
    const panels = $$('[data-tab-panel]', container);
    buttons.forEach((button) => {
      button.addEventListener('click', () => {
        buttons.forEach((item) => item.classList.remove('active'));
        panels.forEach((panel) => { panel.hidden = true; });
        button.classList.add('active');
        const target = $('[data-tab-panel="' + button.dataset.tab + '"]', container);
        if (target) target.hidden = false;
      });
    });
  });
}

function initAccordions() {
  $$('[data-accordion]').forEach((item) => {
    const head = $('[data-accordion-head]', item);
    const body = $('[data-accordion-body]', item);
    if (!head || !body) return;
    head.addEventListener('click', () => {
      const willOpen = body.hidden;
      body.hidden = !willOpen;
      head.setAttribute('aria-expanded', String(willOpen));
    });
  });
}

/* ----------------------------------------------------------------- 轮播 */
function initCarousels() {
  $$('[data-carousel]').forEach((carousel) => {
    const track = $('.carousel-track', carousel);
    const slides = $$('.carousel-slide', carousel);
    const dots = $('.carousel-dots', carousel);
    if (!track || slides.length === 0) return;
    let index = 0;

    function go(next) {
      index = (next + slides.length) % slides.length;
      track.style.transform = 'translateX(-' + index * 100 + '%)';
      $$('button', dots).forEach((dot, dotIndex) => {
        dot.classList.toggle('active', dotIndex === index);
      });
    }

    $$('[data-carousel-prev]', carousel).forEach((button) => button.addEventListener('click', () => go(index - 1)));
    $$('[data-carousel-next]', carousel).forEach((button) => button.addEventListener('click', () => go(index + 1)));
    $$('button', dots).forEach((dot, dotIndex) => dot.addEventListener('click', () => go(dotIndex)));
  });
}

/* ----------------------------------------------------------------- 步骤条 */
function initWizards() {
  $$('[data-wizard]').forEach((wizard) => {
    const steps = $$('[data-wizard-step]', wizard);
    const panels = $$('[data-wizard-panel]', wizard);
    const hints = $$('[data-wizard-hint]', wizard);
    if (!steps.length) return;
    let current = 0;

    function render() {
      steps.forEach((step, index) => step.classList.toggle('active', index === current));
      panels.forEach((panel, index) => { panel.hidden = index !== current; });
    }

    $$('[data-wizard-next]', wizard).forEach((button) => {
      button.addEventListener('click', () => {
        const panel = panels[current];
        const required = $$('[data-required]', panel);
        const missing = required.find((input) => !input.value.trim());
        const hint = $('[data-wizard-hint]', panel);
        if (missing) {
          if (hint) hint.textContent = '请先填写必填项';
          missing.focus();
          return;
        }
        if (hint) hint.textContent = '';
        current = Math.min(current + 1, panels.length - 1);
        render();
      });
    });
    $$('[data-wizard-prev]', wizard).forEach((button) => {
      button.addEventListener('click', () => {
        current = Math.max(current - 1, 0);
        render();
      });
    });
    $$('[data-wizard-finish]', wizard).forEach((button) => {
      button.addEventListener('click', () => toast('向导已完成', 'success'));
    });
    render();
  });
}

/* ------------------------------------------------------------------ 表格 */
function initTables() {
  $$('[data-table]').forEach((table) => {
    const wrap = table.closest('[data-table-wrap]');
    const rows = $$('tbody tr[data-row]', table);
    const details = $$('tbody tr.row-detail', table);
    const filter = wrap ? $('[data-table-filter]', wrap) : null;
    const pager = wrap ? $('[data-pagination]', wrap) : null;
    const bulk = wrap ? $('[data-bulk]', wrap) : null;
    const pageSize = Number(wrap ? wrap.dataset.pageSize || 8 : 8);
    let page = 1;
    let sortKey = '';
    let sortDirection = 1;

    function visibleRows() {
      const term = filter ? filter.value.trim().toLowerCase() : '';
      let result = rows.filter((row) => !term || row.textContent.toLowerCase().includes(term));
      if (sortKey) {
        result = result.slice().sort((left, right) => {
          const a = left.dataset[sortKey] || '';
          const b = right.dataset[sortKey] || '';
          const na = Number(a);
          const nb = Number(b);
          if (!Number.isNaN(na) && !Number.isNaN(nb) && a !== '' && b !== '') {
            return (na - nb) * sortDirection;
          }
          return String(a).localeCompare(String(b), 'zh-CN') * sortDirection;
        });
        // 按排序结果重排数据行，并让展开详情行紧跟所属数据行。
        const tbody = table.querySelector('tbody');
        const fragment = document.createDocumentFragment();
        result.forEach((row) => {
          fragment.appendChild(row);
          const detail = details.find((item) => item.dataset.detailFor === row.dataset.row);
          if (detail) fragment.appendChild(detail);
        });
        tbody.appendChild(fragment);
      }
      return result;
    }

    function apply() {
      const result = visibleRows();
      const pages = Math.max(1, Math.ceil(result.length / pageSize));
      page = Math.min(page, pages);
      rows.forEach((row) => row.classList.add('is-hidden'));
      details.forEach((row) => row.classList.add('is-hidden'));
      result.slice((page - 1) * pageSize, page * pageSize).forEach((row) => {
        row.classList.remove('is-hidden');
        if (row.dataset.expanded === 'true') {
          const detail = details.find((item) => item.dataset.detailFor === row.dataset.row);
          if (detail) detail.classList.remove('is-hidden');
        }
      });
      renderPager(pages);
      renderBulk();
    }

    function renderPager(pages) {
      if (!pager) return;
      pager.innerHTML = '';
      const prev = document.createElement('button');
      prev.type = 'button';
      prev.textContent = '上一页';
      prev.disabled = page <= 1;
      prev.addEventListener('click', () => { page -= 1; apply(); });
      pager.appendChild(prev);
      for (let index = 1; index <= pages; index += 1) {
        const button = document.createElement('button');
        button.type = 'button';
        button.textContent = String(index);
        button.classList.toggle('active', index === page);
        button.addEventListener('click', () => { page = index; apply(); });
        pager.appendChild(button);
      }
      const next = document.createElement('button');
      next.type = 'button';
      next.textContent = '下一页';
      next.disabled = page >= pages;
      next.addEventListener('click', () => { page += 1; apply(); });
      pager.appendChild(next);
      const info = document.createElement('span');
      info.className = 'muted small';
      info.textContent = '共 ' + pages + ' 页';
      pager.appendChild(info);
    }

    function renderBulk() {
      if (!bulk) return;
      const checked = $$('[data-row-check]:checked', table).length;
      bulk.textContent = checked ? '已选择 ' + checked + ' 项' : '未选择任何行';
      const clear = $('[data-bulk-clear]', wrap);
      if (clear) clear.disabled = checked === 0;
    }

    if (filter) filter.addEventListener('input', () => { page = 1; apply(); });
    $$('th[data-sort]', table).forEach((header) => {
      header.addEventListener('click', () => {
        const key = header.dataset.sort;
        if (sortKey === key) sortDirection *= -1;
        else { sortKey = key; sortDirection = 1; }
        apply();
      });
    });
    $$('[data-expand]', table).forEach((button) => {
      button.addEventListener('click', () => {
        const row = button.closest('tr[data-row]');
        if (!row) return;
        row.dataset.expanded = row.dataset.expanded === 'true' ? 'false' : 'true';
        apply();
      });
    });
    $$('[data-row-check]', table).forEach((box) => box.addEventListener('change', renderBulk));
    if (wrap) {
      const clear = $('[data-bulk-clear]', wrap);
      if (clear) clear.addEventListener('click', () => {
        $$('[data-row-check]', table).forEach((box) => { box.checked = false; });
        renderBulk();
      });
    }
    apply();
  });
}

/* --------------------------------------------------------------- 穿梭框 */
function initTransfer() {
  $$('[data-transfer]').forEach((host) => {
    const source = $('[data-transfer-source]', host);
    const target = $('[data-transfer-target]', host);
    if (!source || !target) return;
    const right = $('[data-move-right]', host);
    const left = $('[data-move-left]', host);
    if (right) right.addEventListener('click', () => {
      $$('input[type="checkbox"]:checked', source).forEach((box) => {
        const item = box.closest('li');
        box.checked = false;
        target.appendChild(item);
      });
    });
    if (left) left.addEventListener('click', () => {
      $$('input[type="checkbox"]:checked', target).forEach((box) => {
        const item = box.closest('li');
        box.checked = false;
        source.appendChild(item);
      });
    });
  });
}

/* ------------------------------------------------------------- 标签 / 评分 */
function initTags() {
  $$('[data-tag-input]').forEach((host) => {
    const input = $('input', host);
    if (!input) return;
    input.addEventListener('keydown', (event) => {
      if (event.key !== 'Enter' || !input.value.trim()) return;
      event.preventDefault();
      const pill = document.createElement('span');
      pill.className = 'tag-pill';
      pill.textContent = input.value.trim();
      const remove = document.createElement('button');
      remove.type = 'button';
      remove.className = 'btn-link';
      remove.textContent = '×';
      remove.setAttribute('aria-label', '移除标签');
      remove.addEventListener('click', () => pill.remove());
      pill.appendChild(remove);
      host.insertBefore(pill, input);
      input.value = '';
    });
  });
}

function initRatings() {
  $$('[data-rating]').forEach((host) => {
    const buttons = $$('button', host);
    const value = $('[data-rating-value]', host);
    buttons.forEach((button, index) => {
      button.addEventListener('click', () => {
        buttons.forEach((item, itemIndex) => item.classList.toggle('active', itemIndex <= index));
        if (value) value.value = String(index + 1);
      });
    });
  });
}

function initRanges() {
  $$('[data-range]').forEach((range) => {
    const output = $('[data-range-output]', range.parentElement);
    const update = () => { if (output) output.textContent = range.value; };
    range.addEventListener('input', update);
    update();
  });
}

/* --------------------------------------------------------------- 拖拽 / 分栏 */
function initDragDrop() {
  $$('[data-drag-list]').forEach((list) => {
    $$('li', list).forEach((item) => {
      item.draggable = true;
      item.addEventListener('dragstart', () => {
        item.classList.add('dragging');
        window.__patrolDragItem = item;
      });
      item.addEventListener('dragend', () => {
        item.classList.remove('dragging');
        window.__patrolDragItem = null;
      });
    });
    list.addEventListener('dragover', (event) => {
      event.preventDefault();
      const dragging = window.__patrolDragItem;
      if (!dragging) return;
      const after = $$('li:not(.dragging)', list).find((item) => {
        const rect = item.getBoundingClientRect();
        return event.clientY < rect.top + rect.height / 2;
      });
      if (after) list.insertBefore(dragging, after);
      else list.appendChild(dragging);
    });
    list.addEventListener('drop', (event) => {
      event.preventDefault();
      const dragging = window.__patrolDragItem;
      if (dragging && !list.contains(dragging)) list.appendChild(dragging);
    });
  });
}

function initSplit() {
  $$('[data-split]').forEach((host) => {
    const left = $('[data-split-pane]', host);
    const handle = $('.split-handle', host);
    if (!left || !handle) return;
    let startX = 0;
    let startWidth = 0;
    handle.addEventListener('mousedown', (event) => {
      startX = event.clientX;
      startWidth = left.getBoundingClientRect().width;
      const move = (moveEvent) => {
        const width = Math.max(80, startWidth + moveEvent.clientX - startX);
        left.style.width = width + 'px';
        left.style.flex = '0 0 ' + width + 'px';
      };
      const up = () => {
        document.removeEventListener('mousemove', move);
        document.removeEventListener('mouseup', up);
      };
      document.addEventListener('mousemove', move);
      document.addEventListener('mouseup', up);
    });
  });
}

/* --------------------------------------------------------------- 滚动加载 */
function initInfiniteScroll() {
  $$('[data-scroll-box]').forEach((box) => {
    const loading = $('.scroll-loading', box);
    let count = 20;
    let busy = false;
    box.addEventListener('scroll', () => {
      if (busy || box.scrollTop + box.clientHeight < box.scrollHeight - 30) return;
      if (count >= 80) {
        if (loading) loading.textContent = '已加载全部 80 条';
        return;
      }
      busy = true;
      if (loading) loading.textContent = '加载中…';
      setTimeout(() => {
        for (let index = 0; index < 10; index += 1) {
          count += 1;
          const item = document.createElement('div');
          item.className = 'scroll-item';
          item.textContent = '滚动列表第 ' + count + ' 条记录';
          box.insertBefore(item, loading);
        }
        busy = false;
        if (loading) loading.textContent = count >= 80 ? '已加载全部 80 条' : '继续滚动加载更多';
      }, 500);
    });
  });
}

/* ----------------------------------------------------------------- 异步状态 */
function initAsyncStates() {
  $$('[data-async]').forEach((button) => {
    button.addEventListener('click', () => {
      const kind = button.dataset.async;
      const container = $('[data-async-result="' + kind + '"]');
      if (!container) return;
      container.innerHTML = '<div class="skeleton"></div><div class="skeleton" style="width:70%"></div><div class="skeleton" style="width:45%"></div>';
      let url = '/api/orders';
      if (kind === 'slow') url = '/api/slow?ms=1200';
      if (kind === 'error') url = '/api/error';
      if (kind === 'unauthorized') url = '/api/unauthorized';
      fetch(url)
        .then((response) => {
          if (!response.ok) throw new Error('HTTP ' + response.status);
          return response.json();
        })
        .then((data) => {
          if (Array.isArray(data.items)) {
            container.innerHTML = '<ul class="timeline">' + data.items.map((item) => '<li>' + item + '</li>').join('') + '</ul>';
          } else {
            container.innerHTML = '<div class="empty-state">请求成功：' + JSON.stringify(data) + '</div>';
          }
        })
        .catch((error) => {
          container.innerHTML = '<div class="error-state">' + error.message +
            '<div class="mt-12"><button type="button" class="btn-secondary" data-retry>重试</button></div></div>';
          const retry = $('[data-retry]', container);
          if (retry) retry.addEventListener('click', () => button.click());
        });
    });
  });
}

/* ------------------------------------------------------------- 复制 / 搜索 */
function initCopy() {
  $$('[data-copy]').forEach((button) => {
    button.addEventListener('click', () => {
      const text = button.dataset.copy || '';
      if (navigator.clipboard) {
        navigator.clipboard.writeText(text).then(() => toast('已复制：' + text, 'success')).catch(() => toast('复制失败', 'error'));
      } else {
        toast('当前环境不支持复制', 'warning');
      }
    });
  });
}

function initAutocomplete() {
  $$('[data-autocomplete]').forEach((host) => {
    const input = $('input', host);
    const popup = $('.autocomplete-popup', host);
    if (!input || !popup) return;
    const options = $$('li', popup);
    function filter() {
      const term = input.value.trim().toLowerCase();
      let matched = 0;
      options.forEach((option) => {
        const hit = !term || option.textContent.toLowerCase().includes(term);
        option.hidden = !hit;
        if (hit) matched += 1;
      });
      popup.hidden = matched === 0 || !term;
    }
    input.addEventListener('input', filter);
    input.addEventListener('focus', filter);
    options.forEach((option) => {
      option.addEventListener('click', () => {
        input.value = option.textContent.trim();
        popup.hidden = true;
      });
    });
  });
}

/* ------------------------------------------------------------------ 命令面板 */
function initCommandPalette() {
  const modal = $('[data-command-modal]');
  if (!modal) return;
  const input = $('[data-command-search]', modal);
  const items = $$('[data-command-item]', modal);
  function open() {
    modal.hidden = false;
    if (input) { input.value = ''; input.focus(); }
    items.forEach((item) => { item.hidden = false; });
  }
  function close() { modal.hidden = true; }
  $$('[data-command-open]').forEach((button) => button.addEventListener('click', open));
  document.addEventListener('keydown', (event) => {
    if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'k') {
      event.preventDefault();
      open();
    }
  });
  if (input) input.addEventListener('input', () => {
    const term = input.value.trim().toLowerCase();
    items.forEach((item) => {
      item.hidden = Boolean(term) && !item.textContent.toLowerCase().includes(term);
    });
  });
  $$('[data-command-item]', modal).forEach((item) => {
    item.addEventListener('click', () => {
      toast('执行命令：' + item.textContent.trim());
      close();
    });
  });
  $$('[data-command-close]', modal).forEach((button) => button.addEventListener('click', close));
}

/* ---------------------------------------------------------------- 表单校验 */
function initFormValidation() {
  $$('[data-validate-form]').forEach((form) => {
    const submit = $('[data-submit]', form);
    if (!submit) return;
    submit.addEventListener('click', (event) => {
      event.preventDefault();
      let valid = true;
      $$('[data-required]', form).forEach((input) => {
        const field = input.closest('.field');
        const error = field ? $('.field-error', field) : null;
        const empty = !input.value.trim();
        if (field) field.classList.toggle('field-invalid', empty);
        if (error) error.textContent = empty ? '此项为必填' : '';
        if (empty) valid = false;
      });
      $$('[data-email]', form).forEach((input) => {
        if (!input.value || /^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(input.value)) return;
        const field = input.closest('.field');
        const error = field ? $('.field-error', field) : null;
        if (field) field.classList.add('field-invalid');
        if (error) error.textContent = '邮箱格式不正确';
        valid = false;
      });
      toast(valid ? '表单校验通过' : '表单校验失败', valid ? 'success' : 'error');
    });
  });
}

/* ------------------------------------------------------------ 首页缺陷演示 */
function initIndexDefects() {
  const submitOrder = $('[data-submit-order]');
  if (submitOrder) {
    submitOrder.addEventListener('click', () => {
      throw new Error('提交订单失败：订单服务未响应(502)');
    });
  }
  const status = $('[data-refresh-status]');
  if (status) {
    status.addEventListener('click', () => {
      fetch('/api/refresh', { method: 'POST', headers: { 'content-type': 'application/json' }, body: '{}' })
        .then((response) => {
          if (!response.ok) throw new Error('HTTP ' + response.status);
          return response.json();
        })
        .then(() => {
          const result = $('[data-refresh-result]');
          if (result) result.textContent = '刷新完成';
          toast('服务状态已刷新', 'success');
        })
        .catch(() => toast('刷新失败', 'error'));
    });
  }
  const deleteAll = $('[data-delete-all]');
  if (deleteAll) {
    deleteAll.addEventListener('click', () => {
      const result = $('[data-order-result]');
      if (result) result.textContent = '全部数据已删除（危险操作被执行）';
      toast('危险操作：删除全部数据', 'warning');
    });
  }
  const sync = $('[data-sync-data]');
  if (sync) {
    sync.addEventListener('click', () => {
      const result = $('[data-order-result]');
      if (result) result.textContent = '同步完成';
      toast('数据同步完成', 'success');
    });
  }
}

/* ------------------------------------------------------------------- 启动 */
document.addEventListener('DOMContentLoaded', () => {
  setActiveNav();
  initPopups();
  initSelects();
  initDatepickers();
  initTimepickers();
  initCascaders();
  initTrees();
  initModals();
  initContextMenu();
  initTooltips();
  initOverlays();
  initTabs();
  initAccordions();
  initCarousels();
  initWizards();
  initTables();
  initTransfer();
  initTags();
  initRatings();
  initRanges();
  initDragDrop();
  initSplit();
  initInfiniteScroll();
  initAsyncStates();
  initCopy();
  initAutocomplete();
  initCommandPalette();
  initFormValidation();
  initIndexDefects();
});
