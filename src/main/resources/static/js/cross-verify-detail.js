/* 통합검색 > 서식간 검증 결과 상세(templates/module/ocr/cross-verify-detail.html).
   판정은 Agent 가 저장한 결과를 그대로 보이고, 값과 근거는 지금 값으로 계산한 것을 보인다.
   카드는 규칙 관리 화면과 같은 cross-rule-cards.js 로 그린다. 값을 저장하면 Agent 가 다시 판정한 결과를 다시 읽는다. */
(function () {
  'use strict';
  var page = document.getElementById('cfv-page');
  if (!page) return;
  var transId = page.dataset.transId;
  var base = contextPath + 'module/ocr/crossVerify/' + encodeURIComponent(transId);
  // 상세에서 돌아올 때 조회 조건과 페이지를 복원한다. 목적지와 허용 키는 고정한다.
  var back = page.querySelector('[data-cfv-back]');
  var list = new URLSearchParams(new URLSearchParams(window.location.search).get('list') || '');
  if (back && list.size) {
    var address = new URL(back.href);
    ['menuId', 'wkId', 'result', 'from', 'to', 'page', 'pageSize'].forEach(function (key) {
      if (list.has(key)) address.searchParams.set(key, list.get(key));
    });
    back.href = address;
  }
  var cards = window.CrossRuleCards;
  var sentences = window.CrossRuleSentence.create({ forms: function () { return state.detail.forms || []; } });
  var RESULT = {
    MISMATCH: { label: '불일치', pill: 'status-pill status-pill--error-fill status-pill--fill', key: 'ng' },
    UNVERIFIABLE: { label: '검증불가', pill: 'status-pill status-pill--neutral-fill', key: 'na' },
    MATCH: { label: '일치', pill: 'status-pill status-pill--success', key: 'ok' },
    NOT_APPLICABLE: { label: '해당없음', pill: 'status-pill status-pill--neutral-fill', key: 'na' }
  };
  var PENDING = { label: '미판정', pill: 'status-pill status-pill--neutral-fill', key: 'na' };
  var ORDER = ['MISMATCH', 'UNVERIFIABLE', 'MATCH', 'NOT_APPLICABLE', 'PENDING'];
  var state = { detail: null, ruleId: null, filter: 'ALL' };
  var $ = function (selector, scope) { return (scope || page).querySelector(selector); };

  function codeOf(rule) { return RESULT[rule.result] ? rule.result : 'PENDING'; }
  function resultOf(code) { return RESULT[code] || PENDING; }

  function showMessage(text) {
    var message = $('[data-cfv-message]');
    message.textContent = text || '';
    message.hidden = !text;
  }

  function load() {
    return cards.request(base + '/detail').then(render).catch(function (error) {
      showMessage(error.message);
      $('[data-cfv-title]').textContent = '서식간 검증 결과를 불러오지 못했습니다.';
    });
  }

  function render(detail) {
    state.detail = detail;
    showMessage('');
    $('[data-cfv-workflow]').textContent = detail.workflowName || detail.workflowId || '-';
    var counts = { ALL: detail.rules.length, MATCH: 0, MISMATCH: 0, UNVERIFIABLE: 0, NOT_APPLICABLE: 0, PENDING: 0 };
    var latest = '';
    detail.rules.forEach(function (rule) {
      counts[codeOf(rule)]++;
      if (rule.evaluatedAt && rule.evaluatedAt > latest) latest = rule.evaluatedAt;
    });
    Object.keys(counts).forEach(function (key) { $('[data-cfv-count="' + key + '"]').textContent = counts[key]; });
    $('[data-cfv-pending-stat]').hidden = !counts.PENDING;
    $('[data-cfv-evaluated]').textContent = latest || '-';
    $('[data-cfv-evaluate]').disabled = !detail.rules.length;
    var review = $('[data-cfv-review-link]');
    if (detail.workflowId) {
      review.href = contextPath + 'module/ocr/result?wkId=' + encodeURIComponent(detail.workflowId) + '&transId=' + encodeURIComponent(transId);
      review.hidden = false;
    }
    if (!detail.rules.length) {
      $('[data-cfv-total]').textContent = 0;
      $('[data-cfv-rule-list]').replaceChildren();
      $('[data-cfv-title]').textContent = '이 업무에 사용 중인 서식간 규칙이 없습니다.';
      $('[data-cfv-reason]').textContent = '신청관리 > 서식간 규칙 관리에서 규칙을 켜면 다음 판독부터 판정합니다.';
      clearCards();
      return;
    }
    renderList();
  }

  /* 건수 칸으로 고른 결과만 보인다. 고른 규칙이 빠지면 첫 규칙을 고른다 */
  function renderList() {
    page.querySelectorAll('[data-cfv-filter]').forEach(function (button) {
      var active = button.dataset.cfvFilter === state.filter;
      button.classList.toggle('is-active', active);
      button.setAttribute('aria-pressed', String(active));
    });
    var shown = state.detail.rules.filter(function (rule) {
      return state.filter === 'ALL' || codeOf(rule) === state.filter;
    }).sort(function (a, b) { return ORDER.indexOf(codeOf(a)) - ORDER.indexOf(codeOf(b)); });
    $('[data-cfv-total]').textContent = shown.length;
    var list = $('[data-cfv-rule-list]');
    list.replaceChildren();
    shown.forEach(function (rule) {
      var result = resultOf(codeOf(rule));
      var button = document.createElement('button');
      button.type = 'button';
      button.className = 'cfv-rule';
      button.dataset.result = result.key;
      button.dataset.ruleId = rule.ruleId;
      button.setAttribute('role', 'option');
      var head = document.createElement('span');
      head.className = 'cfv-rule__head';
      var name = document.createElement('span');
      name.className = 'cfv-rule__name';
      name.textContent = rule.name;
      var pill = document.createElement('span');
      pill.className = result.pill;
      pill.textContent = result.label;
      head.append(name, pill);
      button.append(head);
      if (rule.stale) {
        var stale = document.createElement('span');
        stale.className = 'cfv-stale';
        stale.textContent = '규칙 변경됨';
        button.append(stale);
      }
      button.addEventListener('click', function () { select(rule.ruleId); });
      list.append(button);
    });
    if (!shown.length) {
      $('[data-cfv-title]').textContent = '이 결과의 규칙이 없습니다.';
      $('[data-cfv-result]').className = '';
      $('[data-cfv-result]').textContent = '';
      $('[data-cfv-stale]').hidden = true;
      $('[data-cfv-summary]').textContent = '';
      $('[data-cfv-reason]').textContent = '';
      clearCards();
      return;
    }
    select((shown.filter(function (rule) { return rule.ruleId === state.ruleId; })[0] || shown[0]).ruleId);
  }

  function clearCards() {
    var container = $('[data-cfv-viewers]');
    $('[data-cfv-compare]').hidden = true;
    cards.release(container);
    container.replaceChildren();
  }

  function select(ruleId) {
    state.ruleId = ruleId;
    var rule = state.detail.rules.filter(function (entry) { return entry.ruleId === ruleId; })[0];
    page.querySelectorAll('.cfv-rule').forEach(function (button) {
      var active = Number(button.dataset.ruleId) === ruleId;
      button.classList.toggle('is-active', active);
      button.setAttribute('aria-selected', String(active));
    });
    var result = resultOf(codeOf(rule));
    $('[data-cfv-title]').textContent = rule.name;
    var pill = $('[data-cfv-result]');
    pill.className = result.pill;
    pill.textContent = result.label;
    $('[data-cfv-stale]').hidden = !rule.stale;
    $('[data-cfv-summary]').textContent = sentences.ruleSentence({ ruleId: rule.ruleId, ruleNm: rule.name, defJson: rule.definition });
    var reason = rule.result == null ? '아직 판정하지 않은 규칙입니다. 다시 판정을 눌러 주세요.' : rule.reason || '';
    $('[data-cfv-reason]').textContent = reason + (rule.stale ? ' 판정 뒤 규칙이 바뀌었습니다. 다시 판정해 주세요.' : '');
    clearCards();
    var count = cards.render($('[data-cfv-viewers]'), {
      result: cards.screenResult(rule.live), def: JSON.parse(rule.definition),
      forms: state.detail.forms, documents: state.detail.documents,
      wkId: state.detail.workflowId, transId: transId, userId: page.dataset.userId,
      onSaved: function () {
        /* 고친 규칙의 결과가 바뀌어도 화면에서 사라지지 않게 전체로 돌린다 */
        state.filter = 'ALL';
        return cards.request(base + '/detail').then(render).then(function () { openSuccessModal('저장하고 다시 판정했습니다.'); });
      }
    });
    $('[data-cfv-compare]').hidden = count < 2;
    $('[data-cfv-viewers]').scrollLeft = 0;
    markOverflow();
  }

  /** 오른쪽에 가려진 칸이 있으면 가장자리를 흐리게 해 가로 스크롤을 알린다 */
  function markOverflow() {
    var container = $('[data-cfv-viewers]');
    container.classList.toggle('has-more', container.scrollLeft + container.clientWidth < container.scrollWidth - 1);
  }

  page.querySelectorAll('[data-cfv-filter]').forEach(function (button) {
    button.addEventListener('click', function () {
      state.filter = button.dataset.cfvFilter;
      if (state.detail && state.detail.rules.length) renderList();
    });
  });

  $('[data-cfv-compare]').addEventListener('click', function () {
    cards.openCompare($('[data-cfv-title]').textContent);
  });

  $('[data-cfv-evaluate]').addEventListener('click', function () {
    var button = this;
    button.disabled = true;
    cards.request(base + '/evaluate', { method: 'POST' }).then(render).catch(function (error) {
      openErrorModal(error.message);
    }).then(function () { button.disabled = !(state.detail && state.detail.rules.length); });
  });

  $('[data-cfv-viewers]').addEventListener('scroll', markOverflow, { passive: true });
  window.addEventListener('resize', markOverflow);
  load();
})();
