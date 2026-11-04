/* 서식간 규칙 관리 화면. 편집 상태(check)와 저장 정의(DEF_JSON)의 변환은 cross-rule-sentence.js 의 toDef·fromDef 에서만 한다. */
(function () {
  'use strict';
  var page = document.getElementById('crb-page');
  var wkId = page.getAttribute('data-wk-id');
  var base = contextPath + 'module/apply/crossRule';
  var root = document.getElementById('crb-root');
  // 연습 화면에서는 실제 업무용 시작 메뉴를 숨겨 가이드를 다시 여는 동작을 막는다.
  var practice = page.getAttribute('data-practice') === 'true';
  var starters = window.CrossRuleStarters;

  /* 비교 유형은 값의 형식으로 세 묶음으로 나눈다. 묶음을 누르면 첫 종류가 선택된다.
     types 의 코드는 저장 형식의 uiType 이다. 묶음은 화면 구분일 뿐 저장하지 않으므로 기존 규칙은 그대로 열린다. */
  var TYPE_GROUPS = [
    { id: 'value', name: '값', label: '어떻게 확인할까요', types: [['compare', '다른 서류 항목'], ['fixed', '정해진 값'], ['list', '목록에 있는지'], ['presence', '값 있음·없음'], ['pattern', '형식·자릿수']] },
    { id: 'date', name: '날짜', label: '어떤 날짜와 비교할까요', types: [['dateCompare', '다른 서류 날짜'], ['baseDate', '접수일'], ['date', '접수일부터 기간 이내']] },
    { id: 'number', name: '숫자·건수', label: '무엇을 비교할까요', types: [['number', '값 그대로'], ['aggregate', '표 값 합치기'], ['count', '서류 건수']] }
  ];
  function groupOfType(type) {
    return TYPE_GROUPS.filter(function (g) { return g.types.some(function (t) { return t[0] === type; }); })[0];
  }
  /* 각 비교의 종류를 같은 위치에서 고른다. 묶음 이동은 입력을 버리는 동작이 아니다. */
  function typePicker(type, i) {
    var active = groupOfType(type);
    return '<div class="crb-kind-picker" role="group" aria-label="비교 ' + (i + 1) + ' 값의 종류">' + TYPE_GROUPS.map(function (g) {
      return '<button type="button" class="' + (g === active ? 'is-active' : '') + '" aria-pressed="' + (g === active) + '" data-i="' + i + '" data-type-group="' + g.id + '">' + g.name + '</button>';
    }).join('') + '</div>';
  }
  var ROUNDS = [['NONE', '그대로'], ['ROUND', '반올림'], ['FLOOR', '버림']];
  var CALC_MAX_STEPS = 3;
  /* 통과 방법 상한. Portal·Agent 검사의 MAX_METHODS 와 같다 */
  var MAX_METHODS = 5;
  var MATCHES = [['ALL', '모든 값'], ['ANY', '하나 이상'], ['ONE', '정확히 하나'], ['NONE', '일치하는 값 없음']];
  var NORMALIZE = [['IGNORE_SPACE', '공백 무시'], ['IGNORE_HYPHEN', '하이픈 무시'], ['IGNORE_CORP', '(주)·주식회사 같게 보기'], ['IGNORE_CASE', '대소문자 무시']];
  var STATUS = { PASS: '일치', FAIL: '불일치', UNKNOWN: '검증불가', NOT_APPLICABLE: '해당없음' };
  var ORDER = ['FAIL', 'UNKNOWN', 'PASS', 'NOT_APPLICABLE'];

  /* 도움말 문구. 예시 값은 모두 가짜다 */
  var HELP = {
    compare: '두 서류의 값이 같은지 봅니다.\n예: 신분증의 성명이 등기부의 대표자와 같다.',
    list: '비교할 값이 대조할 값 목록에 있는지 봅니다.\n예: 등본의 세대원이 모두 가족관계증명서의 성명 목록에 있다.',
    fixed: '값을 직접 입력한 값과 비교합니다.\n예: 위임장의 날인 여부가 "있음"과 같다.',
    date: '날짜가 접수일로부터 정한 기간 안인지 봅니다. 접수일보다 미래면 불일치입니다.\n예: 등기부 발급일이 접수일 기준 3개월 이내이다.',
    dateCompare: '두 서류의 날짜 순서를 봅니다. 표기가 달라도 날짜로 비교합니다.\n예: 위임장 작성일이 인감증명서 발급일 이후이다.',
    count: '서류가 몇 건 제출됐는지 봅니다.\n예: 신분증이 1건 이상 2건 이하이다.',
    number: '숫자나 금액을 비교합니다. 계산 후 비교할 수도 있습니다.\n예: 신고 소득이 소득금액증명의 금액 × 1.1 이하이다.',
    presence: '항목에 값이 적혀 있는지 봅니다. 서류가 없으면 검증불가입니다.\n예: 위임장의 수임자 성명에 값이 있다.',
    pattern: '값의 형식이나 일부 자리를 확인합니다. 자리는 공백과 하이픈을 빼고 셉니다.\n예: 주민등록번호 7번째 자리가 5, 6, 7, 8 중 하나이다.',
    regex: '정규식으로 형식을 적습니다. 값 전체를 보려면 ^ 로 시작하고 $ 로 끝냅니다.\n예: ^\\d{3}-\\d{2}-\\d{5}$ 는 000-00-00000 형식입니다.',
    baseDate: '날짜를 접수일(스캔일) 기준 날짜와 비교합니다.\n예: 체류 만료일이 기준일과 같거나 이후이다. 생년월일이 기준일 14년 전과 같거나 이전이다(만 14세 이상).',
    aggregate: '표의 여러 값을 합계·개수·최댓값·최솟값으로 줄여 비교합니다. 값을 하나라도 못 읽으면 검증불가입니다.\n예: 주주명부 지분율의 합계가 100과 같다.',
    scope: '같은 서식이 여러 건일 때 서류마다 따로 계산할지 전체를 한 번에 계산할지 정합니다.\n예: 주주명부 2건의 합계를 각각 구한다.',
    sort: '기준 항목의 순위로 값을 좁힙니다. 서류마다 따로 정렬합니다. 기준 값을 못 읽거나 상위 건수 경계에서 값이 같으면 검증불가입니다.\n예: 소유주식수가 큰 순으로 상위 1건의 주주명.',
    condition: '이 서류의 표에서 조건에 맞는 행만 사용합니다. 같은 행의 값끼리 봅니다.\n예: 가족사항 중 구분이 "배우자"인 행의 성명만 비교.',
    calc: '대조할 값을 계산한 뒤 비교합니다. 위에서부터 차례로 계산합니다.\n예: 소득금액 × 12 ÷ 100',
    calcResult: '소수점 처리 방법입니다.\n예: 1,234.5 → 반올림 1,235, 버림 1,234',
    tolerance: '이만큼 차이는 같다고 봅니다. 값과 % 중 더 큰 쪽을 씁니다.\n예: 기준 100,000원, 오차 500원 또는 1%면 1,000원 차이까지 일치.',
    match: '비교할 값 각각의 판정을 합치는 기준입니다.\n모든 값: 전부 맞아야 일치\n하나 이상: 하나만 맞아도 일치\n정확히 하나: 딱 하나만 맞아야 일치\n일치하는 값 없음: 맞는 값이 없어야 일치',
    IGNORE_SPACE: '"김 가명"과 "김가명"을 같게 봅니다.',
    IGNORE_HYPHEN: '"000-00-12345"와 "0000012345"를 같게 봅니다.',
    IGNORE_CORP: '"주식회사 한빛"과 "(주)한빛"을 같게 봅니다.',
    IGNORE_CASE: '"ABC"와 "abc"를 같게 봅니다.',
    pairs: '여러 항목을 묶어서 같은 행의 값끼리 비교합니다. 다른 사람의 값이 섞여 맞는 것을 막습니다.\n예: 신분증의 성명·생년월일이 임원 표에서 대표자 한 사람의 성명·생년월일과 같다.',
    pick: '같은 서류가 여러 건 제출됐을 때, 조건에 맞는 서류의 값만 씁니다.\n예: 신분증이 2건일 때, 성명이 위임장의 대리인과 같은 신분증만 사용.',
    union: '다른 항목의 값을 합쳐 하나의 목록으로 비교합니다. 그중 하나와 맞으면 됩니다.\n예: 등본의 세대원이 가족관계증명서의 "본인 성명 + 가족 성명" 목록에 있다.',
    when: '조건에 맞을 때만 비교합니다. 맞지 않으면 해당없음으로 처리하고 경고로 세지 않습니다. 조건을 판단할 수 없으면 검증불가입니다.\n예: 위임장이 있을 때만 대리인 성명을 비교.',
    branch: '조건에 따라 다른 비교를 합니다. 위에서부터 보고 처음 맞는 경우만 판정합니다.\n예: 혼인 여부가 "기혼"이면 배우자 성명 비교, 그 외에는 해당없음.',
    docCount: '값마다 오른쪽 서류 몇 건에서 찾았는지 셉니다. 범위를 벗어나면 불일치입니다.\n예: 본인 성명은 등본 1건에서만 나와야 한다(1~1건). 자녀가 두 등본에 중복되면 안 된다(0~1건).',
    unionLeft: '다른 항목의 값을 합쳐 비교할 값으로 씁니다.\n예: 가족관계증명서의 "본인 성명 + 가족 성명"이 모두 등본 세대원에 있다.',
    method: '여러 방법 중 하나만 맞으면 일치입니다. 한 방법 안의 비교는 모두 맞아야 합니다.\n예: 신분증 성명이 부 성명과 같거나(방법 1), 모 성명과 같다(방법 2).',
    use: '끄면 규칙은 남지만 판정하지 않습니다.',
    PASS: '규칙을 만족합니다.',
    FAIL: '값이 규칙과 다릅니다. 값이나 서류를 확인해 주세요.',
    UNKNOWN: '값이 없거나 마스킹됐거나 날짜·숫자로 읽을 수 없어 판단하지 못했습니다.',
    NOT_APPLICABLE: '적용 조건에 맞는 서류가 없어 이 건에는 적용하지 않았습니다.'
  };
  function help(key) {
    return '<button type="button" class="crb-help" aria-label="' + esc((HELP[key] || '').split('.')[0]) + ' 도움말" data-help="' + esc(HELP[key]) + '">?</button>';
  }
  /* 도움말의 '예:' 줄을 화면에 항상 보여 준다 */
  function example(key) {
    var m = HELP[key].match(/예: (.*)$/m);
    return m ? '<p class="crb-example">예: ' + esc(m[1]) + '</p>' : '';
  }
  function bindHelp() {
    var tip = document.createElement('div'), pinned = null;
    tip.className = 'crb-tip';
    tip.setAttribute('role', 'tooltip');
    tip.hidden = true;
    document.body.append(tip);
    function show(el) {
      tip.textContent = el.getAttribute('data-help');
      tip.hidden = false;
      var box = el.getBoundingClientRect(), width = tip.offsetWidth, height = tip.offsetHeight;
      tip.style.left = Math.max(8, Math.min(box.left, window.innerWidth - width - 8)) + 'px';
      tip.style.top = (box.bottom + height + 14 > window.innerHeight ? Math.max(8, box.top - height - 6) : box.bottom + 6) + 'px';
    }
    function hide() { tip.hidden = true; pinned = null; }
    page.addEventListener('mouseover', function (e) { var el = e.target.closest('[data-help]'); if (el) show(el); });
    page.addEventListener('mouseout', function (e) { if (!pinned && e.target.closest('[data-help]')) tip.hidden = true; });
    page.addEventListener('focusin', function (e) { var el = e.target.closest('[data-help]'); if (el) show(el); });
    page.addEventListener('focusout', function (e) { if (e.target.closest('[data-help]')) hide(); });
    document.addEventListener('click', function (e) {
      var el = e.target.closest('.crb-help');
      if (!el) { hide(); return; }
      e.preventDefault();
      if (pinned === el) { hide(); return; }
      pinned = el;
      show(el);
    });
    document.addEventListener('keydown', function (e) { if (e.key === 'Escape') hide(); });
    window.addEventListener('scroll', hide, true);
  }

  var state = { view: 'rules', forms: [], transactions: [], rules: [], roles: [], txIndex: 0, selected: null, filter: 'ALL', results: {}, draft: null, search: '', error: '' };
  var editorBaseline = '', editorSaving = false, editorError = null, roleReturn = false;
  var editorGroups = new WeakMap();

  function esc(v) {
    return String(v == null ? '' : v).replace(/[&<>"']/g, function (c) {
      return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c];
    });
  }
  var request = window.CrossRuleCards.request;

  function toast(message) {
    var t = document.getElementById('crb-toast');
    t.textContent = message;
    t.classList.add('is-show');
    clearTimeout(toast.timer);
    toast.timer = setTimeout(function () { t.classList.remove('is-show'); }, 2500);
  }
  function badge(status) { return '<span class="crb-badge crb-badge--' + status + '" data-help="' + esc(HELP[status]) + '">' + STATUS[status] + '</span>'; }
  function options(items, value) {
    return items.map(function (x) {
      return '<option value="' + esc(x[0]) + '"' + (String(x[0]) === String(value) ? ' selected' : '') + '>' + esc(x[1]) + '</option>';
    }).join('');
  }
  /* 최근 판독에 없는 항목(표 항목 등)을 직접 입력하면 그 서식의 목록에 더한다 */
  var CUSTOM_KEY = '__custom__', customKeys = {};
  function keysOf(formId) {
    var f = form(formId), keys = f ? f.keys : [], added = customKeys[formId] || [];
    return keys.concat(added.filter(function (k) { return keys.indexOf(k) < 0; }));
  }
  function applyContext(context) {
    state.forms = context.forms;
    state.transactions = context.transactions;
    state.roles = context.roles;
  }
  var def = window.CrossRuleSentence.create({ forms: function () { return state.forms; }, keysOf: keysOf });
  var OPS = def.OPS,
      CALC_OPS = def.CALC_OPS,
      PICK_OPS = def.PICK_OPS,
      CONDITION_OPS = def.CONDITION_OPS,
      AGGREGATE_FNS = def.AGGREGATE_FNS,
      DATE_UNITS = def.DATE_UNITS,
      SORT_ORDERS = def.SORT_ORDERS,
      SORT_TYPES = def.SORT_TYPES,
      newSort = def.newSort,
      RIGHT_ITEM_TYPES = def.RIGHT_ITEM_TYPES,
      PAIR_TYPES = def.PAIR_TYPES,
      WHEN_TYPES = def.WHEN_TYPES,
      WHEN_OPS = def.WHEN_OPS,
      form = def.form,
      formName = def.formName,
      firstKey = def.firstKey,
      newStep = def.newStep,
      newPick = def.newPick,
      newCheck = def.newCheck,
      newWhen = def.newWhen,
      newBranch = def.newBranch,
      newOtherwise = def.newOtherwise,
      activeCase = def.activeCase,
      selectMethod = def.selectMethod,
      selectBranch = def.selectBranch,
      allChecks = def.allChecks,
      checkById = def.checkById,
      pairKeys = def.pairKeys,
      negativeOp = def.negativeOp,
      fromDef = def.fromDef,
      toDef = def.toDef,
      splitList = def.splitList,
      josa = def.josa,
      sentence = def.sentence,
      draftSentence = def.draftSentence,
      ruleSentence = def.ruleSentence;

  /* ---------- 공통 ---------- */
  function currentTx() { return state.transactions[state.txIndex]; }
  function txLabel(tx, i) {
    var s = tx.scanDateTime || '';
    var when = s.length >= 12 ? s.slice(0, 4) + '-' + s.slice(4, 6) + '-' + s.slice(6, 8) + ' ' + s.slice(8, 10) + ':' + s.slice(10, 12) : '스캔일 없음';
    return when + ' · 서류 ' + tx.documents.length + '건' + (i === 0 ? ' · 최근' : '');
  }
  function txSelect(id) {
    if (!state.transactions.length) return '<p class="crb-muted">이 업무에는 판독 건이 없어 값을 확인할 수 없습니다.</p>';
    return '<select class="input" id="' + id + '" aria-label="판독 건">' + options(state.transactions.map(function (t, i) { return [i, txLabel(t, i)]; }), state.txIndex) + '</select>';
  }
  var screenResult = window.CrossRuleCards.screenResult;
  function judge(rules) {
    return request(base + '/judge?wkId=' + encodeURIComponent(wkId) + '&transId=' + encodeURIComponent(currentTx().transactionId), {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(rules)
    }).then(function (results) { return results.map(function (r) { return screenResult(r.result); }); });
  }
  /* 목록과 결과 화면이 보는 규칙별 판정을 지금 판독 건으로 다시 받는다 */
  function loadResults() {
    if (!currentTx() || !state.rules.length) { state.results = {}; return Promise.resolve(); }
    var rules = state.rules;
    return judge(rules.map(function (r) { return { ruleId: r.ruleId, defJson: r.defJson }; })).then(function (results) {
      state.results = {};
      rules.forEach(function (r, i) { state.results[r.ruleId] = results[i]; });
    });
  }
  function resultOf(rule) { return state.results[rule.ruleId]; }
  function display(v) { return v.value == null || String(v.value).trim() === '' ? '값 없음' : String(v.value); }
  function joinValues(list) { return list.length ? list.map(display).join(', ') : '값 없음'; }

  function render() {
    releaseViewers();
    root.classList.toggle('is-editing', state.view === 'editor');
    if (state.view !== 'editor') { clearTimeout(previewTimer); previewSeq++; }
    if (state.error) { root.innerHTML = '<p class="crb-error">' + esc(state.error) + '</p>'; return; }
    if (state.view === 'editor') { renderEditor(); return; }
    root.innerHTML = '<div class="crb-tabs" role="tablist">'
      + '<button type="button" data-view="rules" class="' + (state.view === 'rules' ? 'is-active' : '') + '">규칙 <span>' + state.rules.length + '</span></button>'
      + '<button type="button" data-view="results" class="' + (state.view === 'results' ? 'is-active' : '') + '">검증 결과</button>'
      + '<button type="button" data-view="roles" class="' + (state.view === 'roles' ? 'is-active' : '') + '">문서 역할 <span>' + state.roles.length + '</span></button></div>'
      + '<div id="crb-view" style="display:flex;flex-direction:column;gap:12px;margin-top:12px"></div>';
    root.querySelectorAll('[data-view]').forEach(function (b) {
      b.addEventListener('click', function () { leaveEditor(function () { roleReturn = false; state.view = b.getAttribute('data-view'); render(); }); });
    });
    if (state.view === 'rules') renderRules(); else if (state.view === 'roles') renderRoles(); else renderResults();
  }

  /* ---------- 규칙 목록 ---------- */
  // 검색과 편집 화면 전환 뒤에도 페이지 크기와 현재 위치를 유지한다.
  var rulePaging = { page: 1, pageSize: 15 };
  function renderRules() {
    var view = document.getElementById('crb-view');
    var query = state.search.trim().toLowerCase();
    var filtered = state.rules.filter(function (r) { return !query || (r.ruleNm + ' ' + ruleSentence(r)).toLowerCase().indexOf(query) >= 0; });
    view.innerHTML = '<div class="crb-bar"><div class="crb-bar__group"><span class="crb-label">판독 건</span>' + txSelect('crb-tx') + '</div>'
      + '<input class="input" style="width:220px" id="crb-search" aria-label="규칙 검색" placeholder="규칙 검색" value="' + esc(state.search) + '">'
      + '<div class="crb-actions" style="margin-left:auto">'
      + (!practice && starters ? '<button type="button" class="crb-link" id="crb-guide">작성 가이드</button><button type="button" class="btn-outline-primary" id="crb-templates">예제로 시작</button>' : '')
      + '<button type="button" class="btn btn-primary" id="crb-new">새 규칙</button></div></div>'
      + '<div class="crb-panel" id="crb-rule-table"></div><div class="list-footer" id="crb-rule-pagination"></div>';
    bindTx();
    document.getElementById('crb-new').addEventListener('click', function () { openEditor(null); });
    bindStarters();
    var search = document.getElementById('crb-search');
    search.addEventListener('input', function () {
      var at = search.selectionStart;
      state.search = search.value;
      rulePaging.page = 1;
      renderRules();
      var again = document.getElementById('crb-search');
      again.focus();
      again.setSelectionRange(at, at);
    });
    // 공통 페이지네이션이 건수를 기준으로 범위를 보정한 뒤 현재 페이지만 그린다.
    window.ListPagination.create({
      container: document.getElementById('crb-rule-pagination'),
      totalCount: filtered.length,
      page: rulePaging.page,
      pageSize: rulePaging.pageSize,
      onChange: function (pager) {
        rulePaging.page = pager.page;
        rulePaging.pageSize = pager.pageSize;
        var rows = filtered.slice(pager.start, pager.end).map(function (r) {
          var on = r.useYn === 'Y', problems = ruleProblems(JSON.parse(r.defJson), state.forms);
          var cell = on && resultOf(r) ? badge(resultOf(r).status) : '<span class="crb-muted">-</span>';
          return '<tr><td><button type="button" class="crb-switch" role="switch" aria-checked="' + on + '" aria-label="' + esc(r.ruleNm) + ' 사용" data-use="' + r.ruleId + '"' + (isBlocked(problems) && !on ? ' disabled data-help="고친 뒤에 켤 수 있습니다."' : '') + '></button></td>'
            + '<td><button type="button" class="crb-link" data-edit="' + r.ruleId + '">' + esc(r.ruleNm) + '</button><div class="crb-muted">v' + r.curVer + '</div>' + (problems.length ? '<div class="crb-reason crb-problem">' + esc(problemText(problems)) + '</div>' : '') + '</td>'
            + '<td class="crb-sentence">' + esc(ruleSentence(r)) + '</td><td>' + cell + '</td>'
            + '<td><div class="crb-actions"><button type="button" class="btn-outline-primary" data-edit="' + r.ruleId + '">수정</button>'
            + '<button type="button" class="btn-neutral btn-neutral--fill" data-delete="' + r.ruleId + '">삭제</button></div></td></tr>';
        }).join('');
        document.getElementById('crb-rule-table').innerHTML = rows
          ? '<div class="crb-table-wrap"><table class="crb-table"><thead><tr><th>사용</th><th>규칙명</th><th>규칙 내용</th><th>판정</th><th></th></tr></thead><tbody>' + rows + '</tbody></table></div>'
          : '<p class="crb-empty">' + (query ? '검색 결과가 없습니다.' : '등록된 규칙이 없습니다.') + '</p>';
        view.querySelectorAll('[data-edit]').forEach(function (b) { b.addEventListener('click', function () { openEditor(Number(b.getAttribute('data-edit'))); }); });
        view.querySelectorAll('[data-use]').forEach(function (b) {
          b.addEventListener('click', function () {
            var id = Number(b.getAttribute('data-use')), on = b.getAttribute('aria-checked') !== 'true';
            request(base + '/rules/' + id + '/use?wkId=' + encodeURIComponent(wkId) + '&value=' + on, { method: 'POST' })
              .then(loadRules).then(function () { toast(on ? '규칙을 켰습니다.' : '규칙을 껐습니다.'); })
              .catch(function (e) { openErrorModal(e.message); });
          });
        });
        view.querySelectorAll('[data-delete]').forEach(function (b) {
          b.addEventListener('click', function () {
            var id = Number(b.getAttribute('data-delete')), rule = state.rules.filter(function (r) { return r.ruleId === id; })[0];
            openConfirmModal('"' + rule.ruleNm + '" 규칙을 삭제할까요?', function () {
              request(base + '/rules/' + id + '/delete?wkId=' + encodeURIComponent(wkId), { method: 'POST' })
                .then(loadRules).then(function () { toast('삭제했습니다.'); })
                .catch(function (e) { openErrorModal(e.message); });
            });
          });
        });
      }
    });
  }
  /* 지금 업무에 없는 서식(blocking: 서버도 켜기를 막는다)과, 최근 판독 결과에서 본 적 없는 항목(경고만: 드문 항목일 수 있다)을 찾는다.
     항목 목록을 모르는 서식(판독 건 없음)은 항목을 보지 않는다 */
  function ruleProblems(def, forms) {
    var problems = [];
    function add(text, blocking) { if (!problems.some(function (x) { return x.text === text; })) problems.push({ text: text, blocking: blocking }); }
    function linked(id) { return forms.filter(function (f) { return f.formId === id; })[0]; }
    def.sources.forEach(function (source) {
      if (!linked(source.formId)) add('이 업무에 없는 서식: 서식 ' + source.formId + '.', true);
    });
    (function walk(node) {
      if (!node || typeof node !== 'object') return;
      if (node.kind === 'item' && node.source !== '$self') {
        var source = def.sources.filter(function (s) { return s.alias === node.source; })[0], f = source && linked(source.formId);
        if (f && f.keys.length) (node.keys || []).concat((node.where || []).map(function (w) { return w.key; }), node.sort ? [node.sort.by] : []).forEach(function (key) {
          if (f.keys.indexOf(key) < 0) add(f.formName + '의 최근 판독에 없는 항목: ' + key + '.', false);
        });
      }
      Object.keys(node).forEach(function (k) { walk(node[k]); });
    })({ cases: def.cases, otherwise: def.otherwise, sources: def.sources });
    return problems;
  }
  function isBlocked(problems) { return problems.some(function (x) { return x.blocking; }); }
  function problemText(problems) { return problems.map(function (x) { return x.text; }).join(' '); }
  function bindTx() {
    var select = document.getElementById('crb-tx');
    if (select) select.addEventListener('change', function () {
      state.txIndex = Number(select.value);
      loadResults().then(render).catch(function (e) { openErrorModal(e.message); });
    });
  }

  /* ---------- 문서 역할 ---------- */
  var ROLE_METHODS = [['CUSTOMER_NAME', '단말 고객명과 같은 서류'], ['REMAINDER', '다른 역할을 뺀 나머지 1건'], ['VALUE_CONTAINS', '항목 값에 역할 이름이 있는 서류'], ['MANUAL', '담당자가 지정']];
  function roleMethodLabel(code) { return (ROLE_METHODS.filter(function (m) { return m[0] === code; })[0] || ['', code])[1]; }
  function usesRoleKey(method) { return method === 'CUSTOMER_NAME' || method === 'VALUE_CONTAINS'; }
  var roleDraft = { formId: null, roleNm: '', methodCd: 'CUSTOMER_NAME', fieldKey: '' };
  function renderRoles() {
    var view = document.getElementById('crb-view');
    if (!form(roleDraft.formId)) { roleDraft.formId = state.forms[0] ? state.forms[0].formId : null; roleDraft.fieldKey = firstKey(roleDraft.formId); }
    var rows = state.roles.map(function (r, n) {
      return '<tr><td>' + esc(formName(r.formId)) + '</td><td>' + esc(r.roleNm) + '</td><td>' + esc(roleMethodLabel(r.methodCd)) + '</td><td>' + esc(r.fieldKey || '-') + '</td>'
        + '<td><div class="crb-actions"><button type="button" class="btn-neutral btn-neutral--fill" data-role-delete="' + n + '">삭제</button></div></td></tr>';
    }).join('');
    view.innerHTML = '<div class="crb-panel"><div class="crb-section"><p class="crb-example">예: 주민등록증 2장이면 신청인(단말 고객명과 같은 서류)과 관계인(다른 역할을 뺀 나머지 1건)을 등록합니다. 서식은 그대로 1개입니다.</p>'
      + '<div class="crb-row"><select class="input" aria-label="서식" id="crb-role-form">' + formOptions(roleDraft.formId) + '</select>'
      + '<input class="input" maxlength="33" aria-label="역할 이름" placeholder="역할 이름" id="crb-role-name" value="' + esc(roleDraft.roleNm) + '">'
      + '<select class="input" aria-label="구분 방식" id="crb-role-method">' + options(ROLE_METHODS, roleDraft.methodCd) + '</select>'
      + (usesRoleKey(roleDraft.methodCd) ? keyControl(roleDraft.formId, roleDraft.fieldKey, ' id="crb-role-key"', '구분 항목') : '')
      + '<button type="button" class="btn btn-primary" id="crb-role-save">저장</button></div></div></div>'
      + '<div class="crb-panel">' + (rows
        ? '<div class="crb-table-wrap"><table class="crb-table"><thead><tr><th>서식</th><th>역할</th><th>구분 방식</th><th>구분 항목</th><th></th></tr></thead><tbody>' + rows + '</tbody></table></div>'
        : '<p class="crb-empty">등록된 역할이 없습니다.</p>') + '</div>';
    if (roleReturn) {
      view.insertAdjacentHTML('afterbegin', '<button type="button" class="crb-link" id="crb-return-editor">‹ 작성 중인 규칙으로 돌아가기</button>');
      document.getElementById('crb-return-editor').addEventListener('click', function () { roleReturn = false; state.view = 'editor'; render(); });
    }
    document.getElementById('crb-role-name').addEventListener('input', function (e) { roleDraft.roleNm = e.target.value; });
    document.getElementById('crb-role-form').addEventListener('change', function (e) { roleDraft.formId = Number(e.target.value); roleDraft.fieldKey = firstKey(roleDraft.formId); renderRoles(); });
    document.getElementById('crb-role-method').addEventListener('change', function (e) { roleDraft.methodCd = e.target.value; renderRoles(); });
    var key = document.getElementById('crb-role-key');
    if (key) key.addEventListener('change', function (e) { roleDraft.fieldKey = e.target.value; });
    document.getElementById('crb-role-save').addEventListener('click', function () {
      postRoles('/roles', { wkId: wkId, formId: roleDraft.formId, roleNm: roleDraft.roleNm, methodCd: roleDraft.methodCd, fieldKey: roleDraft.fieldKey }, '역할을 저장했습니다. 기존 판독 건은 다시 판정해야 반영됩니다.', function () { roleDraft.roleNm = ''; });
    });
    view.querySelectorAll('[data-role-delete]').forEach(function (b) {
      b.addEventListener('click', function () {
        var r = state.roles[Number(b.getAttribute('data-role-delete'))];
        openConfirmModal('"' + r.roleNm + '" 역할을 삭제할까요?', function () {
          postRoles('/roles/delete', { wkId: wkId, formId: r.formId, roleNm: r.roleNm }, '삭제했습니다.');
        });
      });
    });
  }
  function postRoles(path, body, done, after) {
    request(base + path, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) })
      .then(function (roles) { state.roles = roles; if (after) after(); render(); toast(done); })
      .catch(function (e) { openErrorModal(e.message); });
  }

  /* ---------- 예제로 시작·작성 가이드 ---------- */
  function bindStarters() {
    var templates = document.getElementById('crb-templates'), guide = document.getElementById('crb-guide');
    if (templates) templates.addEventListener('click', openTemplates);
    if (guide) guide.addEventListener('click', openGuide);
  }
  /**
   * 작성 상태와 DOM을 보존한 채 native dialog를 연다. 닫으면 호출 버튼으로 초점을 돌린다.
   * @param {string} id 대화상자 ID. 한 번에 하나만 연다.
   * @param {string} title 화면 제목.
   * @param {string} className 대화상자의 크기·본문 종류.
   * @param {string} body 내부에서 만든 이스케이프된 HTML.
   * @returns {HTMLDialogElement} top layer에 열린 대화상자.
   */
  function learningDialog(id, title, className, body) {
    var opener = document.activeElement, dialog = document.createElement('dialog');
    dialog.id = id;
    dialog.className = 'crb-learning-dialog ' + className;
    dialog.setAttribute('aria-labelledby', id + '-title');
    dialog.innerHTML = '<header class="crb-learning-head"><h2 id="' + id + '-title">' + title + '</h2>'
      + '<button type="button" class="btn-outline-primary" data-close-dialog>닫기</button></header>' + body;
    page.appendChild(dialog);
    dialog.querySelector('[data-close-dialog]').addEventListener('click', function () { dialog.close(); });
    dialog.addEventListener('close', function () {
      dialog.remove();
      if (opener && opener.isConnected) opener.focus({ preventScroll: true });
    });
    dialog.addEventListener('keydown', function (event) {
      if (event.key !== 'Tab') return;
      // native dialog의 브라우저 주소창 순회 전에 본문의 첫·마지막 조작부를 연결한다.
      var controls = Array.prototype.filter.call(dialog.querySelectorAll('button, input, select, textarea, iframe, [tabindex]'), function (control) {
        return !control.disabled && control.tabIndex >= 0 && control.getClientRects().length;
      });
      var first = controls[0], last = controls[controls.length - 1];
      if ((event.shiftKey && document.activeElement === first) || (!event.shiftKey && document.activeElement === last)) {
        event.preventDefault();
        (event.shiftKey ? last : first).focus();
      }
    });
    dialog.showModal();
    return dialog;
  }
  /** 실제 작성 내용은 부모 화면에 두고, 별도의 합성 데이터 연습 화면만 연다. */
  function openGuide() {
    learningDialog('crb-guide-dialog', '작성 가이드 · 연습', 'crb-guide-dialog',
      '<iframe title="규칙 등록 연습" src="' + esc(contextPath + 'guide/cross-rule-tutorial.html') + '" sandbox="allow-scripts"></iframe>');
  }
  /** 현재 업무의 항목을 연결한 뒤, 저장 전 새 규칙 초안으로 여는 시작 메뉴다. */
  function openTemplates() {
    var selected = null, mappings = {}, custom = {};
    var dialog = learningDialog('crb-template-dialog', '예제로 시작', 'crb-template-dialog',
      '<div class="crb-learning-body"><div class="crb-template-cards" role="group" aria-label="예시 규칙">'
      + starters.templates.map(function (template, i) {
        return '<button type="button" class="crb-template-card" data-template="' + template.id + '" aria-pressed="false"><span class="crb-template-index">0' + (i + 1) + '</span><strong>' + esc(template.title) + '</strong><span>' + esc(template.description) + '</span><small>예: ' + esc(template.example) + '</small></button>';
      }).join('') + '</div><form id="crb-template-form" novalidate><div id="crb-template-fields"></div></form></div>'
      + '<footer class="crb-learning-foot"><button type="button" class="btn-outline-primary" id="crb-template-cancel">취소</button><button type="submit" form="crb-template-form" class="btn btn-primary" id="crb-template-open" disabled>규칙 초안 열기</button></footer>');
    var fields = dialog.querySelector('#crb-template-fields'), submit = dialog.querySelector('#crb-template-open');
    dialog.querySelector('#crb-template-cancel').addEventListener('click', function () { dialog.close(); });
    function values() { return selected ? mappings[selected.id] : {}; }
    function updateSubmit() {
      submit.disabled = !selected || !state.forms.length || selected.fields.some(function (field) { return String(values()[field.name] == null ? '' : values()[field.name]).trim() === ''; });
    }
    function renderFields(focusName) {
      var mapping = values();
      fields.innerHTML = '<h3>내 업무의 서류·항목 연결</h3>'
        + (!state.forms.length ? '<p class="crb-empty" role="status">이 업무에 연결된 서식이 없습니다. 업무·서식 연결을 먼저 확인해 주세요.</p>' : '')
        + '<div class="crb-template-fields">' + selected.fields.map(function (field) {
          var name = field.name, value = mapping[name], id = 'crb-template-field-' + name;
          var attr = ' id="' + id + '" data-template-field="' + name + '" aria-describedby="' + id + '-error" required';
          var control;
          if (field.kind === 'form') {
            control = '<select class="input"' + attr + (!state.forms.length ? ' disabled' : '') + '>' + options([['', '서류 선택']].concat(state.forms.map(function (form) { return [form.formId, form.formName]; })), value) + '</select>';
          } else if (field.kind === 'key') {
            var formId = mapping[field.side + 'Form'], keys = keysOf(formId), manual = custom[selected.id][name];
            if (manual || !keys.length) control = '<input class="input" type="text"' + attr + (!formId ? ' disabled' : '') + ' placeholder="항목 키 입력" value="' + esc(value) + '">';
            else control = '<select class="input"' + attr + '>' + options([['', '항목 선택']].concat(keys.map(function (key) { return [key, key]; })).concat([[CUSTOM_KEY, '직접 입력…']]), value) + '</select>';
          } else control = '<input class="input" type="' + (field.kind === 'number' ? 'number' : 'text') + '"' + attr + (field.min != null ? ' min="' + field.min + '"' : '') + (field.step != null ? ' step="' + field.step + '"' : '') + ' value="' + esc(value) + '">';
          return '<div class="crb-template-field"><label for="' + id + '">' + esc(field.label) + '</label>' + control + '<p class="crb-field-error" id="' + id + '-error" hidden></p></div>';
        }).join('') + '</div><p id="crb-template-error" class="crb-problem" role="alert" hidden></p>';
      fields.querySelectorAll('[data-template-field]').forEach(function (control) {
        var name = control.getAttribute('data-template-field');
        var field = selected.fields.filter(function (entry) { return entry.name === name; })[0];
        control.addEventListener(control.tagName === 'SELECT' ? 'change' : 'input', function () {
          mapping[name] = control.value;
          control.removeAttribute('aria-invalid');
          fields.querySelector('#' + control.id + '-error').hidden = true;
          fields.querySelector('#crb-template-error').hidden = true;
          if (field.kind === 'form') {
            // 서류를 바꾸면 그 서류의 항목만 남긴다. 이전 서류의 키를 새 서류에 붙이지 않는다.
            selected.fields.filter(function (entry) { return entry.kind === 'key' && entry.side === field.side; }).forEach(function (entry) {
              if (keysOf(control.value).indexOf(mapping[entry.name]) < 0) mapping[entry.name] = '';
              custom[selected.id][entry.name] = false;
            });
            renderFields(name);
          } else if (field.kind === 'key' && control.value === CUSTOM_KEY) {
            custom[selected.id][name] = true;
            mapping[name] = '';
            renderFields(name);
          }
          updateSubmit();
        });
      });
      updateSubmit();
      if (focusName) fields.querySelector('[data-template-field="' + focusName + '"]').focus({ preventScroll: true });
    }
    dialog.querySelectorAll('[data-template]').forEach(function (button) {
      button.addEventListener('click', function () {
        selected = starters.templates.filter(function (entry) { return entry.id === button.getAttribute('data-template'); })[0];
        if (!mappings[selected.id]) mappings[selected.id] = starters.suggest(selected.id, state.forms.map(function (form) { return Object.assign({}, form, { keys: keysOf(form.formId) }); }));
        if (!custom[selected.id]) custom[selected.id] = {};
        dialog.querySelectorAll('[data-template]').forEach(function (card) { card.setAttribute('aria-pressed', String(card === button)); });
        renderFields();
      });
    });
    dialog.querySelector('#crb-template-form').addEventListener('submit', function (event) {
      event.preventDefault();
      if (!selected || submit.disabled) return;
      var invalid = selected.fields.filter(function (field) { return !fields.querySelector('[data-template-field="' + field.name + '"]').checkValidity(); })[0];
      if (invalid) {
        var control = fields.querySelector('[data-template-field="' + invalid.name + '"]');
        control.setAttribute('aria-invalid', 'true');
        var error = fields.querySelector('#' + control.id + '-error');
        error.textContent = invalid.name === 'months' ? '기간은 1 이상의 정수로 입력해 주세요.' : invalid.label + '에 유효한 값을 입력해 주세요.';
        error.hidden = false;
        control.focus();
        return;
      }
      try {
        var draft = starters.createDraft(selected.id, values(), def);
        // 초안도 사용자가 만든 미저장 입력이다. 목록으로 이동할 때 기존 확인 흐름을 적용한다.
        state.draft = draft;
        editorBaseline = '';
        editorError = null;
        roleReturn = false;
        state.view = 'editor';
        dialog.close();
        render();
        document.getElementById('crb-name').focus();
      } catch (error) {
        var summary = fields.querySelector('#crb-template-error');
        console.warn('[cross-rule] 예시 초안 생성 실패', { templateId: selected.id, wkId: wkId, error: error });
        summary.textContent = error.message;
        summary.hidden = false;
      }
    });
  }

  /* ---------- 규칙 편집 ---------- */
  function openEditor(ruleId) {
    var rule = ruleId && state.rules.filter(function (r) { return r.ruleId === ruleId; })[0];
    if (rule) state.draft = fromDef(rule);
    else {
      state.draft = { ruleId: null, name: '', useYn: 'Y', branches: [newBranch('none')], otherwise: newOtherwise() };
      selectBranch(state.draft, 0);
    }
    editorError = null;
    editorBaseline = draftFingerprint();
    state.view = 'editor';
    render();
  }
  function formField(c, i, side) {
    var prop = side + 'Form', keyProp = side + 'Key';
    return '<select class="input" aria-label="' + (side === 'left' ? '비교할 서류' : '대조할 서류') + '" data-i="' + i + '" data-prop="' + prop + '">'
      + formOptions(c[prop]) + '</select><span>의</span>'
      + keyField(c[prop], c[keyProp], i, keyProp, side === 'left' ? '비교할 항목' : '대조할 항목');
  }
  /* 항목 칸. 판독 건이 없어 항목 목록을 모르는 서식은 직접 입력한다. attr 은 칸을 구분하는 data 속성이다 */
  function keyControl(formId, value, attr, label) {
    var keys = keysOf(formId).slice();
    if (!keys.length) return '<input class="input" aria-label="' + label + '" placeholder="항목 키 입력"' + attr + ' value="' + esc(value) + '">';
    if (value && keys.indexOf(value) < 0) keys.unshift(value);
    return '<select class="input" data-key-form="' + esc(formId) + '" aria-label="' + label + '"' + attr + '>' + options(keys.map(function (k) { return [k, k]; }).concat([[CUSTOM_KEY, '직접 입력…']]), value) + '</select>';
  }
  function keyField(formId, value, i, prop, label) { return keyControl(formId, value, ' data-i="' + i + '" data-prop="' + prop + '"', label); }
  /* 저장 유형은 유지하고, 값 비교의 대조 출처는 비교 대상 카드에서 고른다. */
  function checkEditor(c, i) {
    var group = groupOfType(c.type), family = ['compare', 'fixed', 'list'].indexOf(c.type) >= 0;
    var subtypes = group.id === 'value' ? [['compare', '값 비교'], ['presence', '값 있음·없음'], ['pattern', '형식·자릿수']] : group.types;
    var subtype = family ? 'compare' : c.type;
    var html = '<section class="crb-check" data-check-index="' + i + '"><div class="crb-check__head"><h2>비교 ' + (i + 1) + (i ? ' · 그리고' : '') + '</h2>'
      + typePicker(c.type, i) + (i ? '<button type="button" class="crb-remove" aria-label="비교 ' + (i + 1) + ' 삭제" data-remove="' + i + '">✕</button>' : '') + '</div>'
      + '<div class="crb-subtype-row"><label>확인할 내용 <select class="input" aria-label="비교 유형" data-i="' + i + '" data-retype="1">' + options(subtypes, subtype) + '</select></label>' + help(c.type) + '</div>';
    if (c.type === 'count') {
      html += '<div class="crb-operand-grid"><section class="crb-operand"><h3 class="crb-operand__head">제출 서류</h3><select class="input" aria-label="건수를 확인할 서류" data-i="' + i + '" data-prop="leftForm">' + formOptions(c.leftForm) + '</select></section>'
        + '<span class="crb-operand-arrow" aria-hidden="true">↔</span><section class="crb-operand"><h3 class="crb-operand__head">제출 건수 범위</h3><div class="crb-row">'
        + '<input class="input input--num" type="number" min="0" aria-label="최소 건수" data-i="' + i + '" data-prop="min" value="' + c.min + '"><span>건 이상</span>'
        + '<input class="input input--num" type="number" min="0" aria-label="최대 건수" data-i="' + i + '" data-prop="max" value="' + c.max + '"><span>건 이하</span></div></section></div>';
    } else {
      html += '<div class="crb-operand-grid"><section class="crb-operand"><h3 class="crb-operand__head">비교할 값</h3><div class="crb-operand__fields">' + formField(c, i, 'left') + '</div>';
      if (c.type === 'aggregate') html += aggregateSettings(c, i);
      html += targetSettings(c, i, 'left') + '</section><span class="crb-operand-arrow" aria-hidden="true">↔</span><section class="crb-operand"><div class="crb-operand__head"><h3>비교 대상</h3>';
      if (family) html += '<select class="input" aria-label="비교 대상 종류" data-i="' + i + '" data-source-type>' + options([['compare', '서류 항목'], ['fixed', '직접 입력'], ['list', '서류의 값 목록']], c.type) + '</select>';
      html += '</div><div class="crb-operand__fields">';
      if (c.type === 'date') html += '<span>접수일 이전</span><input class="input input--num" type="number" min="0" aria-label="기간" data-i="' + i + '" data-prop="months" value="' + c.months + '">'
        + '<select class="input input--num" aria-label="기간 단위" data-i="' + i + '" data-prop="dateUnit">' + options(DATE_UNITS, c.dateUnit) + '</select><span>이내</span>';
      else if (c.type === 'presence') html += '<span class="crb-muted">항목의 입력 여부를 확인합니다.</span>';
      else if (c.type === 'baseDate') html += offsetEditor(c, i);
      else if (c.type === 'pattern') html += patternEditor(c, i);
      else if (c.type === 'aggregate') html += aggregateEditor(c, i);
      else if (c.type === 'fixed') html += '<input class="input" aria-label="정해진 값" placeholder="비교할 값" data-i="' + i + '" data-prop="fixed" value="' + esc(c.fixed) + '">';
      else html += formField(c, i, 'right');
      html += '</div>';
      if (RIGHT_ITEM_TYPES.indexOf(c.type) >= 0) html += targetSettings(c, i, 'right');
      if (c.type === 'number') html += '<details class="crb-value-settings" data-detail="calc-' + i + '"><summary>계산·반올림<span class="crb-setting-summary">' + esc(c.calc.length + '단계 · ' + (ROUNDS.filter(function (r) { return r[0] === c.round; })[0] || ['', '그대로'])[1]) + '</span></summary>' + calcEditor(c, i) + '</details>';
      html += '</section></div>';
      if (OPS[c.type]) html += '<div class="crb-criterion"><label>비교 방법 <select class="input" aria-label="비교 방법" data-i="' + i + '" data-prop="op">' + options(OPS[c.type], c.op) + '</select></label></div>';
      html += '<div class="crb-match-row"><label>비교할 값 중 <select class="input" aria-label="여러 값의 통과 기준" data-i="' + i + '" data-prop="match">' + options(MATCHES, c.match) + '</select></label>' + help('match') + '</div>';
      html += valueSettings(c, i);
    }
    return html + '</section>';
  }
  /* 집계 방법과 계산 범위는 집계할 항목에 붙인다. 통과 기준의 ALL과 계산 범위의 ALL은 별개다. */
  function aggregateSettings(c, i) {
    return '<div class="crb-row"><label>값 사용 <select class="input input--kind" aria-label="집계 방법" data-i="' + i + '" data-prop="fn">' + options(AGGREGATE_FNS, c.fn) + '</select></label>'
      + ((c.fn === 'MAX' || c.fn === 'MIN') ? '<select class="input input--kind" aria-label="값 형식" data-i="' + i + '" data-prop="aggregateAs">' + options(SORT_TYPES, c.aggregateAs) + '</select>' : '')
      + '<label>계산 범위 <select class="input input--kind" aria-label="집계 범위" data-i="' + i + '" data-prop="scope">' + options([['DOCUMENT', '서류마다'], ['ALL', '전체 서류']], c.scope) + '</select></label>' + help('scope') + '</div>';
  }
  /* 서류·행·순위의 제한을 해당 서류 카드 안에서만 설정한다. 저장 의미와 지원 범위는 바꾸지 않는다. */
  function targetSettings(c, i, side) {
    var conditionSide = c.type === 'aggregate' ? 'left' : 'right';
    var html = pickEditor(c, i, side, side === 'left' ? '비교할' : '대조할');
    if (RIGHT_ITEM_TYPES.indexOf(c.type) >= 0 || (c.type === 'aggregate' && side === 'left')) html += sortEditor(c, i, side, side === 'left' ? '비교할' : '대조할');
    if (side === conditionSide && (RIGHT_ITEM_TYPES.indexOf(c.type) >= 0 || c.type === 'aggregate')) html = conditionEditor(c, i) + html;
    return '<details class="crb-target-settings" data-detail="target-' + side + '-' + i + '"><summary>대상 선택<span class="crb-setting-summary" data-setting-summary="' + side + '" data-i="' + i + '">' + esc(targetSummary(c, side)) + '</span></summary>' + html + '</details>';
  }
  function targetSummary(c, side) {
    var selected = [], pick = c[side + 'Pick'], role = c[side + 'Role'], sort = c[side + 'Sort'];
    if (c.condition && side === (c.type === 'aggregate' ? 'left' : 'right')) selected.push(c.conditionKey + ' ' + (c.conditionValue || '값 입력'));
    if (pick) selected.push('서류 조건');
    if (role) selected.push(role.name);
    if (sort) selected.push('상위 ' + sort.limit + '행');
    return selected.join(' · ') || '전체 값';
  }
  /* 표기 차이와 여러 항목의 관계는 대상 선택에서 분리한다. 행 묶기와 목록 합치기의 의미를 구분한다. */
  function valueSettings(c, i) {
    var canNormalize = ['compare', 'list', 'fixed'].indexOf(c.type) >= 0 || (c.type === 'pattern' && (c.sliceOn || c.patternMode === 'list'));
    var html = canNormalize ? '<div class="crb-advanced__body">' + NORMALIZE.map(function (n) {
      return '<span class="crb-help-group"><label class="crb-check-label"><input type="checkbox" data-i="' + i + '" data-normalize="' + n[0] + '"' + (c.normalize.indexOf(n[0]) >= 0 ? ' checked' : '') + '>' + n[1] + '</label>' + help(n[0]) + '</span>';
    }).join('') + '</div>' : '';
    if (c.type === 'aggregate') html += '<div class="crb-row"><label>허용 오차 <input class="input input--num" type="number" min="0" step="any" aria-label="허용 오차 값" data-i="' + i + '" data-prop="tolerance" value="' + c.tolerance + '"></label><label>또는 <input class="input input--num" type="number" min="0" step="any" aria-label="허용 오차 비율" data-i="' + i + '" data-prop="tolerancePct" value="' + c.tolerancePct + '">%</label>' + help('tolerance') + '</div>';
    if (RIGHT_ITEM_TYPES.indexOf(c.type) >= 0) html += pairEditor(c, i) + extrasEditor(c, i, 'leftExtras', '비교할 목록에 값 추가', 'unionLeft', c.leftForm) + extrasEditor(c, i, 'extras', '대조할 목록에 값 추가', 'union', c.rightForm) + docCountEditor(c, i);
    if (!html) return '';
    var summary = canNormalize ? c.normalize.map(function (n) { return (NORMALIZE.filter(function (x) { return x[0] === n; })[0] || ['', n])[1]; }).join(' · ') : '';
    return '<details class="crb-value-settings" data-detail="values-' + i + '"><summary>값 가공·추가 기준<span class="crb-setting-summary" data-normalize-summary="' + i + '">' + esc(summary) + '</span></summary>' + html + '</details>';
  }
  /* 연결이 풀린 서식도 지금 값으로 보이게 해 다른 서식으로 잘못 읽히지 않게 한다 */
  function formOptions(value) {
    var list = state.forms.map(function (f) { return [f.formId, f.formName]; });
    if (value != null && !form(value)) list.unshift([value, '(연결 해제된 서식)']);
    return options(list, value);
  }
  /* 표에서 조건에 맞는 행만 쓴다. 집계는 왼쪽(집계 대상) 서류, 그 밖의 유형은 오른쪽(대조할) 서류의 행이다 */
  function conditionEditor(c, i) {
    var formId = c.type === 'aggregate' ? c.leftForm : c.rightForm;
    return c.condition
      ? '<div class="crb-condition"><div class="crb-row"><span>행 선택</span>' + help('condition') + keyField(formId, c.conditionKey, i, 'conditionKey', '조건 항목')
        + '<input class="input" aria-label="조건 값" placeholder="예: 대표" data-i="' + i + '" data-prop="conditionValue" value="' + esc(c.conditionValue) + '">'
        + '<select class="input" aria-label="조건 방법" data-i="' + i + '" data-prop="conditionOp">' + options(CONDITION_OPS, c.conditionOp) + '</select><span>행만</span>'
        + '<button type="button" class="crb-remove" aria-label="조건 삭제" data-uncondition="' + i + '">✕</button></div></div>'
      : '<button type="button" class="crb-add" data-condition="' + i + '">+ 조건에 맞는 행 선택</button>' + help('condition');
  }
  /* 화면은 양수 기간과 전·후로 받으며, 저장할 때는 기존 부호 있는 offset을 유지한다. */
  function offsetEditor(c, i) {
    return '<span>접수일</span><input class="input input--num" type="number" min="0" step="1" aria-label="접수일에서 이동할 기간" data-i="' + i + '" data-offset-amount value="' + Math.abs(c.offset) + '">'
      + '<select class="input input--num" aria-label="기준일 단위" data-i="' + i + '" data-prop="dateUnit">' + options(DATE_UNITS, c.dateUnit) + '</select>'
      + '<select class="input input--num" aria-label="기준일 이동 방향" data-i="' + i + '" data-offset-direction>' + options([['before', '전'], ['after', '후']], c.offset < 0 ? 'before' : c.offset > 0 ? 'after' : c.offsetDirection || 'before') + '</select>';
  }
  function patternEditor(c, i) {
    var html = '<label class="crb-check-label"><input type="checkbox" data-i="' + i + '" data-slice-toggle' + (c.sliceOn ? ' checked' : '') + '>일부 자리만 보기</label>';
    if (c.sliceOn) {
      html += '<input class="input input--num" type="number" min="1" aria-label="시작 자리" data-i="' + i + '" data-prop="sliceStart" value="' + c.sliceStart + '"><span>번째부터</span>'
        + '<input class="input input--num" type="number" min="1" aria-label="자리 수" data-i="' + i + '" data-prop="sliceLength" value="' + c.sliceLength + '"><span>자리가</span>';
    }
    html += '<select class="input input--kind" aria-label="확인 방법" data-i="' + i + '" data-prop="patternMode">' + options([['regex', '형식(정규식)'], ['list', '값 목록']], c.patternMode) + '</select>';
    html += c.patternMode === 'regex'
      ? '<input class="input" aria-label="정규식" placeholder="^\\d{3}-\\d{2}$" maxlength="200" data-i="' + i + '" data-prop="pattern" value="' + esc(c.pattern) + '"><span>에 맞다</span>' + help('regex')
      : '<input class="input" aria-label="값 목록(쉼표로 구분)" placeholder="5, 6, 7, 8" data-i="' + i + '" data-prop="valueList" value="' + esc(c.valueList) + '"><span>중 하나이다</span>';
    return html;
  }
  function aggregateEditor(c, i) {
    var date = (c.fn === 'MAX' || c.fn === 'MIN') && c.aggregateAs === 'DATE';
    var kinds = [['number', '숫자 입력'], ['item', '서류 항목'], ['aggregate', '서류 항목 집계']].filter(function (k) { return !date || k[0] !== 'number'; }).concat(date ? [['baseDate', '접수일']] : []);
    var html = '<select class="input input--kind" aria-label="비교 대상" data-i="' + i + '" data-prop="targetKind">' + options(kinds, c.targetKind) + '</select>';
    if (c.targetKind === 'item' || c.targetKind === 'aggregate') {
      if (c.targetKind === 'aggregate') html += '<select class="input input--num" aria-label="대조 집계 방법" data-i="' + i + '" data-prop="rightFn">' + options(AGGREGATE_FNS.filter(function (f) { return !date || f[0] === 'MAX' || f[0] === 'MIN'; }), c.rightFn) + '</select>';
      html += formField(c, i, 'right');
    } else if (c.targetKind === 'baseDate') html += offsetEditor(c, i);
    else html += '<input class="input input--num" type="number" step="any" aria-label="비교 값" data-i="' + i + '" data-prop="targetValue" value="' + esc(c.targetValue) + '">';
    return html;
  }
  /* 기준 항목의 순위로 값을 좁힌다. 화면에서는 sort 라는 말을 쓰지 않는다 */
  function sortEditor(c, i, side, label) {
    var sort = c[side + 'Sort'], formId = c[side + 'Form'];
    var html = '<div class="crb-extra"><label class="crb-check-label"><input type="checkbox" data-i="' + i + '" data-sort-toggle="' + side + '"' + (sort ? ' checked' : '') + '>' + label + ' 값을 순위로 좁히기</label>' + help('sort') ;
    if (sort) {
      var attr = ' data-i="' + i + '" data-sort="' + side + '" data-sort-prop=';
      html += '<div class="crb-row crb-pick"><span>기준</span>' + keyControl(formId, sort.by, attr + '"by"', label + ' 정렬 기준 항목')
        + '<select class="input input--kind" aria-label="' + label + ' 정렬 형식"' + attr + '"as">' + options(SORT_TYPES, sort.as) + '</select>'
        + '<select class="input input--num" aria-label="' + label + ' 정렬 방향"' + attr + '"order">' + options(SORT_ORDERS, sort.order) + '</select>'
        + '<span>으로 상위</span><input class="input input--num" type="number" min="1" aria-label="' + label + ' 상위 건수"' + attr + '"limit" value="' + sort.limit + '"><span>건</span></div>';
    }
    return html + '</div>';
  }
  function calcEditor(c, i) {
    return '<div class="crb-calc">' + example('calc') + c.calc.map(function (step, n) {
      var target = step.kind === 'item'
        ? '<select class="input" aria-label="계산 서류" data-i="' + i + '" data-step="' + n + '" data-step-prop="form">' + formOptions(step.form) + '</select>' + stepKey(step, i, n)
        : '<input class="input input--num" type="number" step="any" aria-label="계산 값" data-i="' + i + '" data-step="' + n + '" data-step-prop="value" value="' + esc(step.value) + '">';
      return '<div class="crb-row"><span>계산</span>' + (n === 0 ? help('calc') : '') + '<select class="input input--num" aria-label="계산 방법" data-i="' + i + '" data-step="' + n + '" data-step-prop="op">' + options(CALC_OPS, step.op) + '</select>'
        + '<select class="input input--kind" aria-label="계산 대상" data-i="' + i + '" data-step="' + n + '" data-step-prop="kind">' + options([['number', '숫자'], ['item', '서류 항목']], step.kind) + '</select>' + target
        + (c.calc.length > 1 ? '<button type="button" class="crb-remove" aria-label="계산 삭제" data-i="' + i + '" data-step-remove="' + n + '">✕</button>' : '') + '</div>';
    }).join('') + (c.calc.length < CALC_MAX_STEPS ? '<button type="button" class="crb-add" data-step-add="' + i + '">+ 계산 추가</button>' : '')
      + '<div class="crb-row" style="margin-top:8px"><span>계산 결과</span>' + help('calcResult') + '<select class="input input--kind" aria-label="계산 결과 처리" data-i="' + i + '" data-prop="round">' + options(ROUNDS, c.round) + '</select>'
      + '<span>허용 오차</span>' + help('tolerance') + '<input class="input input--num" type="number" min="0" step="any" aria-label="허용 오차 값" data-i="' + i + '" data-prop="tolerance" value="' + c.tolerance + '">'
      + '<span>또는</span><input class="input input--num" type="number" min="0" step="any" aria-label="허용 오차 비율" data-i="' + i + '" data-prop="tolerancePct" value="' + c.tolerancePct + '"><span>%</span></div>' + example('tolerance') + '</div>';
  }
  function stepKey(step, i, n) {
    return keyControl(step.form, step.key, ' data-i="' + i + '" data-step="' + n + '" data-step-prop="key"', '계산 항목');
  }
  function pickEditor(c, i, side, label) {
    var pick = c[side + 'Pick'], formId = c[side + 'Form'];
    var html = '<div class="crb-extra"><label class="crb-check-label"><input type="checkbox" data-i="' + i + '" data-pick-toggle="' + side + '"' + (pick ? ' checked' : '') + '>' + label + ' 서류 고르기 (' + esc(josa(formName(formId), '이', '가')) + ' 여러 건일 때)</label>' + help('pick') ;
    if (pick) {
      var attr = ' data-i="' + i + '" data-pick="' + side + '" data-pick-prop=';
      html += '<div class="crb-row crb-pick">' + keyControl(formId, pick.key, attr + '"key"', label + ' 고르기 항목') + '<span>값이</span>'
        + '<select class="input input--kind" aria-label="' + label + ' 고르기 대상"' + attr + '"kind">' + options([['text', '정해진 값'], ['item', '서류 항목']], pick.kind) + '</select>';
      if (pick.kind === 'item') {
        html += '<select class="input" aria-label="' + label + ' 고르기 서류"' + attr + '"form">' + formOptions(pick.form) + '</select>' + keyControl(pick.form, pick.valueKey, attr + '"valueKey"', label + ' 고르기 서류 항목');
      } else {
        html += '<input class="input" aria-label="' + label + ' 고르기 값"' + attr + '"value" value="' + esc(pick.value) + '">';
      }
      html += '<span>와(과)</span><select class="input input--kind" aria-label="' + label + ' 고르기 방법"' + attr + '"op">' + options(PICK_OPS, pick.op) + '</select><span>서류만</span></div>';
    }
    return html + '</div>' + roleEditor(c, i, side, label);
  }
  /* 같은 서식 여러 건 중 역할이 맞는 서류 1건만 비교한다. 역할과 구분 방식은 문서 역할 탭에서 서식마다 등록한다 */
  function roleEditor(c, i, side, label) {
    var role = c[side + 'Role'], names = rolesOf(c[side + 'Form']);
    if (role && names.indexOf(role.name) < 0) names = [role.name].concat(names);
    if (!names.length) return '<div class="crb-extra"><button type="button" class="crb-link" data-manage-roles>문서 역할 등록</button></div>';
    return '<div class="crb-extra"><label class="crb-check-label" style="white-space:nowrap">' + label + ' 서류 역할 <select class="input input--kind" data-i="' + i + '" data-role="' + side + '">'
      + options([['', '지정 안 함']].concat(names.map(function (n) { return [n, n]; })), role ? role.name : '') + '</select></label> <button type="button" class="crb-link" data-manage-roles>역할 관리</button></div>';
  }
  function rolesOf(formId) {
    return state.roles.filter(function (r) { return r.formId === Number(formId); }).map(function (r) { return r.roleNm; });
  }
  function pairEditor(c, i) {
    if (PAIR_TYPES.indexOf(c.type) < 0) return '';
    function field(formId, value, n, side, label) { return keyControl(formId, value, ' data-i="' + i + '" data-pair="' + n + '" data-pair-side="' + side + '"', label); }
    return '<div class="crb-extra"><span class="crb-check-label">같은 행의 항목 묶기</span>' + help('pairs') + example('pairs') + c.pairs.map(function (p, n) {
      return '<div class="crb-row crb-pick"><span>+</span>' + field(c.leftForm, p.left, n, 'left', '함께 비교할 항목') + '<span>↔</span>' + field(c.rightForm, p.right, n, 'right', '함께 대조할 항목')
        + '<button type="button" class="crb-remove" aria-label="함께 비교 삭제" data-i="' + i + '" data-pair-remove="' + n + '">✕</button></div>';
    }).join('') + '<div><button type="button" class="crb-add" data-pair-add="' + i + '">+ 같은 행의 항목 묶기</button></div></div>';
  }
  function extrasEditor(c, i, prop, title, helpKey, formId) {
    var attr = ' data-i="' + i + '" data-extra-list="' + prop + '"';
    return '<div class="crb-extra"><span class="crb-check-label">' + title + '</span>' + help(helpKey) + example(helpKey) + c[prop].map(function (x, n) {
      return '<div class="crb-row crb-pick"><span>+</span><select class="input" aria-label="합칠 서류"' + attr + ' data-extra="' + n + '" data-extra-prop="form">' + formOptions(x.form) + '</select><span>의</span>'
        + keyControl(x.form, x.key, attr + ' data-extra="' + n + '" data-extra-prop="key"', '합칠 항목')
        + '<button type="button" class="crb-remove" aria-label="합칠 항목 삭제"' + attr + ' data-extra-remove="' + n + '">✕</button></div>';
    }).join('') + '<div><button type="button" class="crb-add"' + attr + ' data-extra-form="' + formId + '" data-extra-add="1">+ 항목 합치기</button></div></div>';
  }
  function docCountEditor(c, i) {
    if (RIGHT_ITEM_TYPES.indexOf(c.type) < 0 || negativeOp(c)) return '';
    return '<div class="crb-extra"><label class="crb-check-label"><input type="checkbox" data-i="' + i + '" data-doc-count' + (c.useDocCount ? ' checked' : '') + '>일치한 대조 서류 수 제한</label>' + help('docCount') + example('docCount')
      + (c.useDocCount ? '<div class="crb-row crb-pick"><input class="input input--num" type="number" min="0" aria-label="찾은 서류 최소" data-i="' + i + '" data-prop="docMin" value="' + c.docMin + '"><span>~</span>'
        + '<input class="input input--num" type="number" min="0" aria-label="찾은 서류 최대" data-i="' + i + '" data-prop="docMax" value="' + c.docMax + '"><span>건의 서류에서 찾아야 함</span></div>' : '') + '</div>';
  }
  /* 언제 확인할까요: 경우가 하나면 적용 조건이고, 둘 이상이면 경우 나누기다. 저장 형식은 같다 */
  function whenEditor(d) {
    var multi = d.branches.length > 1 || d.otherwise.on, isElse = d.activeBranch >= d.branches.length;
    var html = '<div class="crb-section"><h2>적용 조건' + help('branch') + '</h2>';
    if (multi) {
      html += '<div class="crb-case-tabs" role="tablist">' + d.branches.map(function (b, n) {
        return '<button type="button" role="tab" aria-selected="' + (d.activeBranch === n) + '" class="' + (d.activeBranch === n ? 'is-active' : '') + '" data-branch="' + n + '">' + esc(b.label.trim() || '경우 ' + (n + 1)) + '</button>';
      }).join('') + '<button type="button" role="tab" aria-selected="' + isElse + '" class="' + (isElse ? 'is-active' : '') + '" data-branch="' + d.branches.length + '">그 외</button></div>';
    }
    if (isElse) {
      return html + '<div class="crb-row"><span>어느 경우에도 맞지 않으면</span><select class="input" style="width:170px" id="crb-else" aria-label="그 외 처리">'
        + options([['off', '해당없음'], ['on', '비교하기']], d.otherwise.on ? 'on' : 'off') + '</select></div></div>';
    }
    var b = d.branches[d.activeBranch];
    /* n 은 조건 순서다. 0 은 when, 그 뒤는 andWhen 이다. '항상'은 첫 조건에서 조건이 없을 때만 고른다 */
    function whenRow(w, n) {
      var attr = ' data-when-n="' + n + '"';
      var row = '<div class="crb-row">' + (n ? '<span>그리고</span>' : '') + '<select class="input" style="width:170px" aria-label="조건 종류" data-when="type"' + attr + '>' + options(multi || n ? WHEN_TYPES.slice(1) : WHEN_TYPES, w.type) + '</select>';
      if (w.type !== 'none') row += '<select class="input" aria-label="조건 서류" data-when="form"' + attr + '>' + formOptions(w.form) + '</select>';
      if (w.type === 'value') {
        row += '<span>의</span>' + keyControl(w.form, w.key, ' data-when="key"' + attr, '조건 항목') + '<span>이(가)</span>'
          + (w.op === 'EMPTY' || w.op === 'NOT_EMPTY' ? '' : '<input class="input" aria-label="조건 값" placeholder="예: 기혼" data-when="value"' + attr + ' value="' + esc(w.value) + '">')
          + '<select class="input input--kind" aria-label="조건 방법" data-when="op"' + attr + '>' + options(WHEN_OPS, w.op) + '</select>';
      }
      if (n) row += '<button type="button" class="crb-remove" aria-label="조건 ' + (n + 1) + ' 삭제" data-when-remove="' + n + '">✕</button>';
      return row + '</div>';
    }
    html += [b.when].concat(b.andWhen).map(whenRow).join('') + (b.when.type !== 'none' ? example(multi ? 'branch' : 'when') : '');
    if (b.when.type !== 'none') html += '<div><button type="button" class="crb-add" id="crb-when-add">+ 적용 조건 추가</button></div>';
    if (multi) {
      html += '<div class="crb-row"><span>경우 이름</span><input class="input" maxlength="30" aria-label="경우 이름" placeholder="예: 기혼" id="crb-branch-label" value="' + esc(b.label) + '">'
        + '<button type="button" class="crb-remove" aria-label="이 경우 삭제" id="crb-branch-remove">✕</button></div>';
    }
    return html + '<button type="button" class="crb-add" id="crb-branch-add">+ 경우 나누기</button></div>';
  }
  /* 통과 방법: 방법이 둘 이상이면 탭으로 옮겨 다니며 방법별 비교를 고친다 */
  function methodEditor(d) {
    var c = activeCase(d);
    if (c.methods.length < 2) return '';
    return '<p class="crb-example">방법 중 하나가 맞으면 일치합니다. 한 방법의 비교는 모두 맞아야 합니다.</p><div class="crb-case-tabs" role="tablist">' + c.methods.map(function (m, n) {
      return '<button type="button" role="tab" aria-selected="' + (c.activeMethod === n) + '" class="' + (c.activeMethod === n ? 'is-active' : '') + '" data-method="' + n + '">방법 ' + (n + 1) + '</button>';
    }).join('') + '<button type="button" class="crb-remove" aria-label="방법 ' + (c.activeMethod + 1) + ' 삭제" id="crb-method-remove">✕</button></div>';
  }
  function methodAdd(d) {
    if (activeCase(d).methods.length >= MAX_METHODS) return '';
    return '<div><button type="button" class="crb-add" id="crb-method-add">+ 또는 다른 통과 방법 추가</button>' + help('method') + '</div>';
  }
  /* 범위로 판정하는 유형은 기준을 한 문구로 보여 준다 */
  function criterion(c) {
    if (c.type === 'date') return '접수일 기준 ' + c.months + (DATE_UNITS.filter(function (u) { return u[0] === c.dateUnit; })[0] || ['', '개월'])[1] + ' 이내';
    if (c.type === 'presence') return c.op === 'EMPTY' ? '값이 없어야 함' : '값이 있어야 함';
    if (c.type === 'pattern' && c.patternMode === 'regex') return '형식 ' + c.pattern;
    if (c.type === 'count') return c.min + '~' + c.max + '건';
    return null;
  }
  function criterionOf(d, c) { var e = checkById(d, c.id); return e ? criterion(e) : null; }
  /* 값을 못 찾은 사유를 서류가 없는 경우와 서류는 있는데 값이 없는 경우로 나눈다. 합치기·고르기는 서식 하나로 말할 수 없어 Agent 사유를 그대로 둔다 */
  function missingText(e, side) {
    var formId = e[side + 'Form'], f = form(formId), extras = side === 'left' ? e.leftExtras : e.extras;
    if (!f || !f.formCode || (extras && extras.length) || e[side + 'Pick'] || e[side + 'Role']) return null;
    var found = currentTx().documents.some(function (x) { return window.CrossRuleCards.sameFormCode(x.formCode, f.formCode); });
    if (!found) return '이 판독 건에 ' + josa(f.formName, '이', '가') + ' 없습니다.';
    return josa(f.formName, '은', '는') + ' 있지만 ' + pairKeys(e, side).join('·') + ' 값을 찾지 못했습니다. 판독 결과를 확인해 주세요.';
  }
  function checkReasons(e, c) {
    return c.reasons.map(function (reason) {
      var side = reason === '오른쪽 값을 찾지 못했습니다.' ? 'right' : reason === '왼쪽 값을 찾지 못했습니다.' ? 'left' : null;
      return (e && side && missingText(e, side)) || reason;
    }).join(' ');
  }
  function caseLine(result) { return result.caseLabel ? '<p class="crb-reason">적용한 경우: ' + esc(result.caseLabel) + '</p>' : ''; }
  /* 묶음의 방법별 결과. 어느 방법이 맞았고 어느 방법이 왜 틀렸는지 보여 준다 */
  function methodLines(d, group) {
    return group.children.map(function (m, n) {
      return '<div class="crb-method"><div class="crb-method__head"><span class="crb-check-line__no">방법 ' + (n + 1) + '</span>' + badge(m.status) + '</div>' + m.children.map(function (c) {
        var reasons = checkReasons(checkById(d, c.id), c);
        return '<div class="crb-pair">' + esc(joinValues(c.left)) + ' <span class="crb-pair__arrow">↔</span> ' + esc(criterionOf(d, c) || joinValues(c.right)) + ' ' + badge(c.status)
          + (reasons ? '<div class="crb-reason">' + esc(reasons) + '</div>' : '') + '</div>';
      }).join('') + '</div>';
    }).join('');
  }
  function previewHtml(d, result) {
    return badge(result.status) + caseLine(result) + (result.reasons.length ? '<p class="crb-reason">' + esc(result.reasons.join(' ')) + '</p>' : '')
      + result.checks.map(function (c, i) {
        if (c.children.length) return methodLines(d, c);
        var reasons = checkReasons(checkById(d, c.id), c);
        return '<div class="crb-pair">' + esc(joinValues(c.left)) + ' <span class="crb-pair__arrow">↔</span> ' + esc(criterionOf(d, c) || joinValues(c.right)) + ' ' + badge(c.status)
          + (reasons ? '<div class="crb-reason">' + esc(reasons) + '</div>' : '') + '</div>';
      }).join('');
  }
  /* 이름·사용 여부와 저장 정의만 비교한다. 탭·접기·유형 캐시는 미저장 변경으로 세지 않는다. */
  function draftFingerprint() {
    var d = state.draft;
    return JSON.stringify({ name: d.name, useYn: d.useYn, definition: toDef(d) });
  }
  function hasUnsavedChanges() {
    return !!state.draft && (state.view === 'editor' || roleReturn) && draftFingerprint() !== editorBaseline;
  }
  function leaveEditor(action) {
    if (editorSaving) return;
    if (hasUnsavedChanges()) openConfirmModal('저장하지 않은 변경을 버리고 이동할까요?', action);
    else action();
  }
  function refreshLive() {
    var readback = document.getElementById('crb-readback');
    if (!readback) return;
    readback.textContent = draftSentence(state.draft);
    document.getElementById('crb-apply-summary').textContent = applySummary(state.draft);
    root.querySelectorAll('[data-setting-summary]').forEach(function (el) {
      el.textContent = targetSummary(state.draft.checks[Number(el.getAttribute('data-i'))], el.getAttribute('data-setting-summary'));
    });
    root.querySelectorAll('[data-normalize-summary]').forEach(function (el) {
      var c = state.draft.checks[Number(el.getAttribute('data-normalize-summary'))];
      el.textContent = c.normalize.map(function (n) { return (NORMALIZE.filter(function (x) { return x[0] === n; })[0] || ['', n])[1]; }).join(' · ');
    });
    var status = document.getElementById('crb-save-state');
    if (status) status.textContent = editorSaving ? '저장 중입니다.' : hasUnsavedChanges() ? '변경사항 있음' : '';
    schedulePreview();
  }
  /* 판독 건이 없거나 입력이 미완성이면 실제 판정을 꾸미지 않는다. 이전 결과는 즉시 지운다. */
  var previewTimer = null, previewSeq = 0;
  function schedulePreview() {
    clearTimeout(previewTimer);
    var seq = ++previewSeq, box = document.getElementById('crb-preview');
    if (!box) return;
    if (!currentTx()) { box.innerHTML = '<p class="crb-muted">이 업무에는 판독 건이 없어 값을 확인할 수 없습니다.</p>'; box.removeAttribute('aria-busy'); return; }
    box.innerHTML = '<p class="crb-preview-pending" role="status">변경 내용 확인 중…</p>';
    box.setAttribute('aria-busy', 'true');
    // 이름은 판정과 무관하다. 비교 입력의 오류만 미리보기를 보류한다.
    var issue = validateDraft(state.draft, true);
    if (issue) {
      box.innerHTML = '<p class="crb-muted">' + esc(issue.message) + '</p>';
      box.removeAttribute('aria-busy');
      return;
    }
    previewTimer = setTimeout(function () {
      var d = state.draft;
      // 전송한 입력의 복사본을 써서 나중에 바뀐 문장과 이전 결과가 섞이지 않게 한다.
      var snapshot = JSON.parse(JSON.stringify(d));
      judge([{ ruleId: d.ruleId, defJson: JSON.stringify(toDef(snapshot)) }]).then(function (results) {
        var currentBox = document.getElementById('crb-preview');
        if (seq !== previewSeq || !currentBox || state.view !== 'editor') return;
        currentBox.removeAttribute('aria-busy');
        currentBox.innerHTML = previewHtml(snapshot, results[0]);
      }).catch(function (e) {
        var currentBox = document.getElementById('crb-preview');
        if (seq !== previewSeq || !currentBox || state.view !== 'editor') return;
        currentBox.removeAttribute('aria-busy');
        currentBox.innerHTML = '<p class="crb-reason" role="alert">' + esc(e.message) + '</p><button type="button" class="crb-link" id="crb-preview-retry">다시 확인</button>';
        document.getElementById('crb-preview-retry').addEventListener('click', schedulePreview);
      });
    }, 300);
  }
  /* 바뀐 컨트롤의 식별 속성을 보존해 다른 행이나 비교로 초점이 옮겨가지 않게 한다. */
  function controlIdentity(el) {
    if (!el || !root.contains(el)) return null;
    var attrs = ['id', 'data-i', 'data-prop', 'data-retype', 'data-source-type', 'data-type-group', 'data-offset-amount', 'data-offset-direction', 'data-when', 'data-when-n', 'data-pick', 'data-pick-prop', 'data-pick-toggle', 'data-sort', 'data-sort-prop', 'data-sort-toggle', 'data-step', 'data-step-prop', 'data-normalize', 'data-role', 'data-pair', 'data-pair-side', 'data-extra-list', 'data-extra', 'data-extra-prop', 'data-doc-count', 'data-slice-toggle', 'data-branch', 'data-method'];
    var identity = {};
    attrs.forEach(function (name) { if (el.hasAttribute(name)) identity[name] = el.getAttribute(name); });
    return Object.keys(identity).length ? { tag: el.tagName, attrs: identity } : null;
  }
  function findControl(identity) {
    if (!identity) return null;
    return Array.prototype.find.call(root.querySelectorAll(identity.tag), function (el) {
      return Object.keys(identity.attrs).every(function (name) { return el.getAttribute(name) === identity.attrs[name]; });
    });
  }
  function applySummary(d) {
    if (d.branches.length > 1 || d.otherwise.on) return '경우 ' + d.branches.length + '개 · 위에서부터 적용';
    var w = d.branches[0].when;
    if (w.type === 'none') return '항상 적용';
    if (w.type === 'hasDoc') return formName(w.form) + '이 있을 때';
    if (w.type === 'noDoc') return formName(w.form) + '이 없을 때';
    return w.key + ' 조건' + (d.branches[0].andWhen.length ? ' 외 ' + d.branches[0].andWhen.length + '개' : '');
  }
  /* 비교를 중심에 두고 적용 조건은 필요할 때 연다. 저장·판정 정의는 기존 형식을 쓴다. */
  function renderEditor(focusSelector) {
    var d = state.draft, active = controlIdentity(document.activeElement), scrollTop = page.scrollTop;
    var open = Array.prototype.map.call(root.querySelectorAll('details'), function (el) { return { key: el.getAttribute('data-detail') || el.id, open: el.open }; });
    root.classList.add('is-editing');
    var multi = d.branches.length > 1 || d.otherwise.on;
    root.innerHTML = '<div class="crb-editor-topbar"><button type="button" class="crb-link" id="crb-back">‹ 규칙 목록</button><h2>' + (d.ruleId ? '규칙 수정' : '규칙 등록') + '</h2>'
      + (!practice && starters ? '<button type="button" class="crb-link crb-editor-guide" id="crb-guide">작성 가이드</button>' : '') + '</div>'
      + '<div class="crb-editor"><div class="crb-editor-main"><div class="crb-name-row"><label for="crb-name">규칙 이름</label><input class="input" id="crb-name" aria-required="true" maxlength="33" placeholder="예: 대표자 성명 확인" value="' + esc(d.name) + '"></div>'
      + (multi ? '<div class="crb-branch-context">편집 중: ' + esc(d.activeBranch < d.branches.length ? d.branches[d.activeBranch].label || '경우 ' + (d.activeBranch + 1) : '그 외') + '<button class="crb-link" type="button" id="crb-edit-apply">경우 선택</button></div>' : '')
      + (d.activeBranch >= d.branches.length && !d.otherwise.on ? '' : '<div class="crb-panel">' + methodEditor(d) + d.checks.map(checkEditor).join('')
      + '<div class="crb-group-tools"><button type="button" class="crb-add" id="crb-add-check">+ 그리고 비교 추가</button>' + methodAdd(d) + '</div></div>')
      + '<details class="crb-apply-settings" id="crb-apply-settings"><summary>규칙 적용<span class="crb-setting-summary" id="crb-apply-summary">' + esc(applySummary(d)) + '</span><span class="crb-link">조건 설정</span></summary>' + whenEditor(d) + '</details></div>'
      + '<aside class="crb-panel crb-preview"><section class="crb-preview-section"><h2>작성한 규칙</h2><div class="crb-readback" id="crb-readback"></div></section>'
      + '<section class="crb-preview-section"><h2>판독 건으로 확인</h2><label class="crb-label" for="crb-tx">판독 건</label>' + txSelect('crb-tx') + '<div id="crb-preview" aria-live="polite"></div></section></aside></div>'
      + '<div id="crb-save-error" class="crb-save-error" role="alert" hidden></div>'
      + '<div class="crb-savebar"><label class="crb-check-label"><input type="checkbox" id="crb-use"' + (d.useYn !== 'N' ? ' checked' : '') + '>저장 후 사용</label>' + help('use') + '<span id="crb-save-state" class="crb-save-state" role="status"></span><div class="crb-actions"><button type="button" class="btn-outline-primary" id="crb-cancel">취소</button><button type="button" class="btn btn-primary" id="crb-save">저장</button></div></div>';
    root.querySelectorAll('details').forEach(function (el) {
      var key = el.getAttribute('data-detail') || el.id;
      var found = open.filter(function (entry) { return entry.key === key; })[0];
      if (found) el.open = found.open;
    });
    refreshLive();
    bindEditor();
    applyEditorError(false);
    page.scrollTop = scrollTop;
    var focus = typeof focusSelector === 'string' ? root.querySelector(focusSelector) : findControl(active);
    if (!focus && active && active.attrs['data-i'] != null) focus = root.querySelector('[data-retype][data-i="' + active.attrs['data-i'] + '"]');
    if (focus) focus.focus({ preventScroll: true });
    if (editorSaving) setSaving(true);
  }
  function bindEditor() {
    var d = state.draft;
    bindStarters();
    function back() { leaveEditor(function () { roleReturn = false; state.view = 'rules'; render(); }); }
    document.getElementById('crb-back').addEventListener('click', back);
    document.getElementById('crb-cancel').addEventListener('click', back);
    document.getElementById('crb-save').addEventListener('click', save);
    document.getElementById('crb-name').addEventListener('input', function (e) { d.name = e.target.value; document.getElementById('crb-save-state').textContent = hasUnsavedChanges() ? '변경사항 있음' : ''; });
    document.getElementById('crb-use').addEventListener('change', function (e) { d.useYn = e.target.checked ? 'Y' : 'N'; refreshLive(); });
    var editApply = document.getElementById('crb-edit-apply');
    if (editApply) editApply.addEventListener('click', function () { var el = document.getElementById('crb-apply-settings'); el.open = true; el.scrollIntoView({ block: 'nearest' }); el.querySelector('button, select').focus(); });
    root.querySelectorAll('[data-manage-roles]').forEach(function (button) { button.addEventListener('click', function () { roleReturn = true; state.view = 'roles'; render(); }); });
    root.querySelectorAll('[data-branch]').forEach(function (b) {
      b.addEventListener('click', function () { selectBranch(d, Number(b.getAttribute('data-branch'))); renderEditor(); });
    });
    var branchAdd = document.getElementById('crb-branch-add');
    if (branchAdd) branchAdd.addEventListener('click', function () {
      // 경우가 둘이면 '항상'은 뒤의 경우를 막으므로 조건을 정하게 한다.
      if (d.branches[0].when.type === 'none') d.branches[0].when = newWhen('value');
      d.branches.push(newBranch('value'));
      selectBranch(d, d.branches.length - 1);
      renderEditor();
    });
    var branchRemove = document.getElementById('crb-branch-remove');
    if (branchRemove) branchRemove.addEventListener('click', function () {
      if (d.branches.length === 1) { d.otherwise.on = false; d.branches[0].label = ''; } else d.branches.splice(d.activeBranch, 1);
      selectBranch(d, 0);
      renderEditor();
    });
    var branchLabel = document.getElementById('crb-branch-label');
    if (branchLabel) branchLabel.addEventListener('input', function () {
      d.branches[d.activeBranch].label = branchLabel.value;
      var tab = root.querySelector('[data-branch="' + d.activeBranch + '"]');
      if (tab) tab.textContent = branchLabel.value.trim() || '경우 ' + (d.activeBranch + 1);
      refreshLive();
    });
    var elseMode = document.getElementById('crb-else');
    if (elseMode) elseMode.addEventListener('change', function () { d.otherwise.on = elseMode.value === 'on'; renderEditor(); });
    root.querySelectorAll('[data-when]').forEach(function (el) {
      el.addEventListener(el.tagName === 'SELECT' ? 'change' : 'input', function () {
        var b = d.branches[d.activeBranch], n = Number(el.getAttribute('data-when-n')), w = n ? b.andWhen[n - 1] : b.when, prop = el.getAttribute('data-when');
        w[prop] = prop === 'form' ? Number(el.value) : el.value;
        // '항상'은 조건이 없다는 뜻이므로 함께 걸어 둔 조건을 지운다.
        if (prop === 'type' && w.type === 'none') b.andWhen = [];
        if (prop === 'form') w.key = firstKey(w.form, w.key);
        if (prop === 'type' || prop === 'form' || prop === 'op') { renderEditor(); return; }
        refreshLive();
      });
    });
    var whenAdd = document.getElementById('crb-when-add');
    if (whenAdd) whenAdd.addEventListener('click', function () { d.branches[d.activeBranch].andWhen.push(newWhen('value')); renderEditor(); });
    root.querySelectorAll('[data-when-remove]').forEach(function (b) {
      b.addEventListener('click', function () { d.branches[d.activeBranch].andWhen.splice(Number(b.getAttribute('data-when-remove')) - 1, 1); renderEditor(); });
    });
    root.querySelectorAll('[data-doc-count]').forEach(function (el) {
      el.addEventListener('change', function () { d.checks[Number(el.getAttribute('data-i'))].useDocCount = el.checked; renderEditor(); });
    });
    function nextCheck() {
      var first = d.checks[0], next = newCheck('compare');
      if (first.type !== 'count') {
        next.leftForm = first.leftForm;
        next.leftKey = first.leftKey;
        // 아직 쓰지 않은 서류를 먼저 고른다.
        var used = d.checks.map(function (c) { return c.rightForm; }).concat(first.leftForm);
        var unused = state.forms.filter(function (f) { return used.indexOf(f.formId) < 0; })[0];
        if (unused) { next.rightForm = unused.formId; next.rightKey = firstKey(unused.formId); next.conditionKey = next.rightKey; }
      }
      return next;
    }
    var addCheck = document.getElementById('crb-add-check');
    if (addCheck) addCheck.addEventListener('click', function () {
      d.checks.push(nextCheck());
      renderEditor('[data-check-index="' + (d.checks.length - 1) + '"] [data-prop="leftForm"]');
    });
    root.querySelectorAll('[data-method]').forEach(function (b) {
      b.addEventListener('click', function () { selectMethod(d, Number(b.getAttribute('data-method'))); renderEditor(); });
    });
    var methodNew = document.getElementById('crb-method-add');
    if (methodNew) methodNew.addEventListener('click', function () {
      var c = activeCase(d);
      c.methods.push([nextCheck()]);
      selectMethod(d, c.methods.length - 1);
      renderEditor();
    });
    var methodRemove = document.getElementById('crb-method-remove');
    if (methodRemove) methodRemove.addEventListener('click', function () {
      var c = activeCase(d);
      c.methods.splice(c.activeMethod, 1);
      selectMethod(d, 0);
      renderEditor();
    });
    var tx = document.getElementById('crb-tx');
    if (tx) tx.addEventListener('change', function () { state.txIndex = Number(tx.value); refreshLive(); });
    function retype(i, type) {
      var previous = d.checks[i], remembered = editorGroups.get(previous) || {};
      remembered[groupOfType(previous.type).id] = previous.type;
      d.checks[i] = def.retypeCheck(previous, type);
      remembered[groupOfType(type).id] = type;
      editorGroups.set(d.checks[i], remembered);
      renderEditor();
    }
    root.querySelectorAll('[data-type-group]').forEach(function (button) {
      button.addEventListener('click', function () {
        var i = Number(button.getAttribute('data-i')), c = d.checks[i];
        var group = TYPE_GROUPS.filter(function (g) { return g.id === button.getAttribute('data-type-group'); })[0];
        if (group !== groupOfType(c.type)) {
          var remembered = editorGroups.get(c) || {};
          retype(i, remembered[group.id] || group.types[0][0]);
        }
      });
    });
    root.querySelectorAll('[data-retype], [data-source-type]').forEach(function (select) {
      select.addEventListener('change', function () { retype(Number(select.getAttribute('data-i')), select.value); });
    });
    root.querySelectorAll('[data-offset-amount], [data-offset-direction]').forEach(function (el) {
      el.addEventListener(el.tagName === 'SELECT' ? 'change' : 'input', function () {
        var c = d.checks[Number(el.getAttribute('data-i'))], section = el.closest('.crb-operand');
        var amount = section.querySelector('[data-offset-amount]'), direction = section.querySelector('[data-offset-direction]').value;
        c.offsetDirection = direction;
        // 전·후 선택이 부호를 맡는다. 음수를 붙여 입력해도 이동 방향이 뒤집히지 않게 한다.
        if (amount.value !== '' && Number(amount.value) < 0) amount.value = String(Math.abs(Number(amount.value)));
        c.offset = amount.value === '' ? NaN : Number(amount.value) * (direction === 'before' ? -1 : 1);
        refreshLive();
      });
    });
    root.querySelectorAll('[data-remove]').forEach(function (b) {
      b.addEventListener('click', function () { d.checks.splice(Number(b.getAttribute('data-remove')), 1); renderEditor(); });
    });
    root.querySelectorAll('[data-condition]').forEach(function (b) {
      b.addEventListener('click', function () { d.checks[Number(b.getAttribute('data-condition'))].condition = true; renderEditor(); });
    });
    root.querySelectorAll('[data-uncondition]').forEach(function (b) {
      b.addEventListener('click', function () { d.checks[Number(b.getAttribute('data-uncondition'))].condition = false; renderEditor(); });
    });
    function checkOf(el) { return d.checks[Number(el.getAttribute('data-i'))]; }
    root.querySelectorAll('[data-step-add]').forEach(function (b) {
      b.addEventListener('click', function () { var c = d.checks[Number(b.getAttribute('data-step-add'))]; c.calc.push(newStep(c.rightForm)); renderEditor(); });
    });
    root.querySelectorAll('[data-step-remove]').forEach(function (b) {
      b.addEventListener('click', function () { checkOf(b).calc.splice(Number(b.getAttribute('data-step-remove')), 1); renderEditor(); });
    });
    root.querySelectorAll('[data-step-prop]').forEach(function (el) {
      el.addEventListener(el.tagName === 'SELECT' ? 'change' : 'input', function () {
        var step = checkOf(el).calc[Number(el.getAttribute('data-step'))], prop = el.getAttribute('data-step-prop');
        step[prop] = prop === 'form' ? Number(el.value) : prop === 'value' ? (el.value === '' ? NaN : Number(el.value)) : el.value;
        if (prop === 'form') step.key = firstKey(step.form);
        if (prop === 'form' || prop === 'kind') { renderEditor(); return; }
        refreshLive();
      });
    });
    root.querySelectorAll('[data-pick-toggle]').forEach(function (el) {
      el.addEventListener('change', function () {
        var c = checkOf(el), side = el.getAttribute('data-pick-toggle');
        c[side + 'Pick'] = el.checked ? newPick(c[side + 'Form']) : null;
        renderEditor();
      });
    });
    root.querySelectorAll('[data-pick-prop]').forEach(function (el) {
      el.addEventListener(el.tagName === 'SELECT' ? 'change' : 'input', function () {
        var pick = checkOf(el)[el.getAttribute('data-pick') + 'Pick'], prop = el.getAttribute('data-pick-prop');
        pick[prop] = prop === 'form' ? Number(el.value) : el.value;
        if (prop === 'form') pick.valueKey = firstKey(pick.form);
        if (prop === 'form' || prop === 'kind') { renderEditor(); return; }
        refreshLive();
      });
    });
    root.querySelectorAll('[data-sort-toggle]').forEach(function (el) {
      el.addEventListener('change', function () {
        var c = checkOf(el), side = el.getAttribute('data-sort-toggle');
        c[side + 'Sort'] = el.checked ? newSort(c[side + 'Form']) : null;
        renderEditor();
      });
    });
    root.querySelectorAll('[data-sort-prop]').forEach(function (el) {
      el.addEventListener(el.tagName === 'SELECT' ? 'change' : 'input', function () {
        var prop = el.getAttribute('data-sort-prop');
        checkOf(el)[el.getAttribute('data-sort') + 'Sort'][prop] = prop === 'limit' ? (el.value === '' ? NaN : Number(el.value)) : el.value;
        refreshLive();
      });
    });
    root.querySelectorAll('[data-slice-toggle]').forEach(function (el) {
      el.addEventListener('change', function () { checkOf(el).sliceOn = el.checked; renderEditor(); });
    });
    root.querySelectorAll('[data-role]').forEach(function (el) {
      el.addEventListener('change', function () {
        checkOf(el)[el.getAttribute('data-role') + 'Role'] = el.value ? { name: el.value } : null;
        refreshLive();
      });
    });
    root.querySelectorAll('[data-pair-add]').forEach(function (b) {
      b.addEventListener('click', function () {
        var c = d.checks[Number(b.getAttribute('data-pair-add'))];
        c.pairs.push({ left: firstKey(c.leftForm), right: firstKey(c.rightForm) });
        renderEditor();
      });
    });
    root.querySelectorAll('[data-pair-remove]').forEach(function (b) {
      b.addEventListener('click', function () { checkOf(b).pairs.splice(Number(b.getAttribute('data-pair-remove')), 1); renderEditor(); });
    });
    root.querySelectorAll('[data-pair]').forEach(function (el) {
      el.addEventListener(el.tagName === 'SELECT' ? 'change' : 'input', function () {
        checkOf(el).pairs[Number(el.getAttribute('data-pair'))][el.getAttribute('data-pair-side')] = el.value;
        refreshLive();
      });
    });
    function extrasOf(el) { return checkOf(el)[el.getAttribute('data-extra-list')]; }
    root.querySelectorAll('[data-extra-add]').forEach(function (b) {
      b.addEventListener('click', function () {
        var formId = Number(b.getAttribute('data-extra-form'));
        extrasOf(b).push({ form: formId, key: firstKey(formId) });
        renderEditor();
      });
    });
    root.querySelectorAll('[data-extra-remove]').forEach(function (b) {
      b.addEventListener('click', function () { extrasOf(b).splice(Number(b.getAttribute('data-extra-remove')), 1); renderEditor(); });
    });
    root.querySelectorAll('[data-extra-prop]').forEach(function (el) {
      el.addEventListener(el.tagName === 'SELECT' ? 'change' : 'input', function () {
        var x = extrasOf(el)[Number(el.getAttribute('data-extra'))], prop = el.getAttribute('data-extra-prop');
        x[prop] = prop === 'form' ? Number(el.value) : el.value;
        if (prop === 'form') { x.key = firstKey(x.form); renderEditor(); return; }
        refreshLive();
      });
    });
    root.querySelectorAll('[data-normalize]').forEach(function (el) {
      el.addEventListener('change', function () {
        var c = d.checks[Number(el.getAttribute('data-i'))], key = el.getAttribute('data-normalize');
        c.normalize = c.normalize.filter(function (x) { return x !== key; });
        if (el.checked) c.normalize.push(key);
        refreshLive();
      });
    });
    root.querySelectorAll('[data-prop]').forEach(function (el) {
      el.addEventListener(el.tagName === 'SELECT' ? 'change' : 'input', function () {
        var c = d.checks[Number(el.getAttribute('data-i'))], prop = el.getAttribute('data-prop');
        c[prop] = el.type === 'number' ? (el.value === '' ? NaN : Number(el.value)) : el.value;
        if (prop === 'leftForm' || prop === 'rightForm') {
          c[prop] = Number(el.value);
          var side = prop === 'leftForm' ? 'left' : 'right';
          c[side + 'Key'] = firstKey(c[prop], c[side + 'Key']);
          // 조건은 집계면 왼쪽, 그 밖에는 오른쪽 서류의 항목이다.
          if (side === (c.type === 'aggregate' ? 'left' : 'right')) { c.conditionKey = firstKey(c[prop], c.conditionKey); }
          if (c[side + 'Sort']) c[side + 'Sort'].by = firstKey(c[prop], c[side + 'Sort'].by);
          if (c[side + 'Pick']) c[side + 'Pick'].key = firstKey(c[prop], c[side + 'Pick'].key);
          c[side + 'Role'] = null;
          renderEditor();
          return;
        }
        // 조건 예시에 쓰는 항목이나 찾은 서류 수를 보여 줄지가 바뀌면 다시 그린다.
        // 입력 칸 구성이 바뀌는 선택도 다시 그린다.
        if (['rightKey', 'conditionKey', 'op', 'patternMode', 'fn', 'aggregateAs', 'targetKind'].indexOf(prop) >= 0) {
          // 날짜 집계에는 숫자 대상이 없고, 숫자 집계에는 기준일 대상이 없다.
          if (c.type === 'aggregate') {
            var dateAggregate = (c.fn === 'MAX' || c.fn === 'MIN') && c.aggregateAs === 'DATE';
            if (dateAggregate && c.targetKind === 'number') c.targetKind = 'baseDate';
            if (!dateAggregate && c.targetKind === 'baseDate') c.targetKind = 'number';
            if (dateAggregate && c.rightFn !== 'MAX' && c.rightFn !== 'MIN') c.rightFn = 'MAX';
          }
          renderEditor();
          return;
        }
        refreshLive();
      });
    });
  }
  /* 검증 오류에 경우·방법·입력 위치를 함께 담아 숨겨진 비교도 바로 열 수 있게 한다. */
  function checkIssue(d, c, selector, message) {
    var found;
    d.branches.concat(d.otherwise.on ? [d.otherwise] : []).some(function (branch, bi) {
      return branch.methods.some(function (method, mi) {
        var index = method.indexOf(c);
        if (index < 0) return false;
        found = { branch: bi, method: mi, selector: selector.split(',').map(function (part) { return '[data-check-index="' + index + '"] ' + part.trim(); }).join(', '),
          message: (d.branches.length > 1 || d.otherwise.on ? (bi < d.branches.length ? '경우 ' + (bi + 1) : '그 외') + ' · ' : '')
            + (branch.methods.length > 1 ? '방법 ' + (mi + 1) + ' · ' : '') + '비교 ' + (index + 1) + ': ' + message };
        return true;
      });
    });
    return found;
  }
  function validateDraft(d, ignoreName) {
    if (!ignoreName && !d.name.trim()) return { message: '규칙 이름을 입력해 주세요.', selector: '#crb-name' };
    var multi = d.branches.length > 1 || d.otherwise.on;
    for (var n = 0; n < d.branches.length; n++) {
      var branch = d.branches[n], whens = [branch.when].concat(branch.andWhen);
      for (var wi = 0; wi < whens.length; wi++) {
        var w = whens[wi], target = null;
        if (multi && w.type === 'none') target = 'type';
        else if (w.type === 'value' && !w.key) target = 'key';
        else if (w.type === 'value' && ['EMPTY', 'NOT_EMPTY'].indexOf(w.op) < 0 && !String(w.value).trim()) target = 'value';
        if (target) return { branch: n, method: 0, selector: '[data-when-n="' + wi + '"][data-when="' + target + '"]', message: (multi ? '경우 ' + (n + 1) + ': ' : '') + (target === 'type' ? '적용 조건을 정해 주세요.' : '적용 조건의 항목과 값을 입력해 주세요.') };
      }
    }
    var list = allChecks(d);
    for (var i = 0; i < list.length; i++) {
      var c = list[i];
      function problem(selector, message) { return checkIssue(d, c, selector, message); }
      function prop(name, message) { return problem('[data-prop="' + name + '"]', message); }
      if (c.type !== 'count' && !c.leftKey) return prop('leftKey', '항목을 선택해 주세요.');
      if (RIGHT_ITEM_TYPES.indexOf(c.type) >= 0 && !c.rightKey) return prop('rightKey', '대조할 항목을 선택해 주세요.');
      if (c.type === 'fixed' && !String(c.fixed).trim()) return prop('fixed', '비교할 값을 입력해 주세요.');
      if (c.condition && !String(c.conditionValue).trim()) return prop('conditionValue', '행 선택 조건 값을 입력해 주세요.');
      if (c.type === 'count' && (!Number.isInteger(c.min) || !Number.isInteger(c.max) || c.min < 0 || c.max < c.min)) return prop('min', '건수는 0 이상의 정수이고 최소가 최대 이하여야 합니다.');
      if (c.type === 'number') {
        var badStep = c.calc.findIndex(function (x) { return x.kind === 'item' ? !x.key : !Number.isFinite(x.value) || (x.op === 'DIV' && x.value === 0); });
        if (badStep >= 0) return problem('[data-step="' + badStep + '"][data-step-prop="' + (c.calc[badStep].kind === 'item' ? 'key' : 'value') + '"]', '계산 값을 확인해 주세요. 0으로 나눌 수 없습니다.');
      }
      if ((c.type === 'number' || c.type === 'aggregate') && (![c.tolerance, c.tolerancePct].every(Number.isFinite) || c.tolerance < 0 || c.tolerancePct < 0)) return prop('tolerance', '허용 오차는 0 이상이어야 합니다.');
      if (c.useDocCount && RIGHT_ITEM_TYPES.indexOf(c.type) >= 0 && !negativeOp(c) && (!Number.isInteger(c.docMin) || !Number.isInteger(c.docMax) || c.docMin < 0 || c.docMax < c.docMin)) return prop('docMin', '일치한 서류 수는 0 이상의 정수이고 최소가 최대 이하여야 합니다.');
      if (PAIR_TYPES.indexOf(c.type) >= 0) {
        var badPair = c.pairs.findIndex(function (pair) { return !pair.left || !pair.right; });
        if (badPair >= 0) return problem('[data-pair="' + badPair + '"][data-pair-side="' + (!c.pairs[badPair].left ? 'left' : 'right') + '"]', '같은 행에 묶을 항목을 선택해 주세요.');
      }
      var extraLists = ['leftExtras', 'extras'];
      for (var ei = 0; ei < extraLists.length; ei++) {
        var badExtra = c[extraLists[ei]].findIndex(function (x) { return !x.key; });
        if (badExtra >= 0) return problem('[data-extra-list="' + extraLists[ei] + '"][data-extra="' + badExtra + '"][data-extra-prop="key"]', '목록에 추가할 항목을 선택해 주세요.');
      }
      var sides = ['left', 'right'];
      for (var pi = 0; pi < sides.length; pi++) {
        var pick = c[sides[pi] + 'Pick'];
        if (pick && (!pick.key || (pick.kind === 'item' ? !pick.valueKey : !String(pick.value).trim()))) return problem('[data-pick="' + sides[pi] + '"][data-pick-prop="' + (pick.kind === 'item' ? 'valueKey' : 'value') + '"]', '서류 선택 조건을 입력해 주세요.');
      }
      if (c.type === 'date' && (!Number.isInteger(c.months) || c.months < 0)) return prop('months', '기간은 0 이상의 정수여야 합니다.');
      if ((c.type === 'baseDate' || c.targetKind === 'baseDate') && !Number.isInteger(c.offset)) return problem('[data-offset-amount]', '접수일에서 이동할 기간은 정수여야 합니다.');
      if (c.type === 'pattern' && c.patternMode === 'regex' && !String(c.pattern).trim()) return prop('pattern', '정규식을 입력해 주세요.');
      if (c.type === 'pattern' && c.patternMode === 'list' && !splitList(c.valueList).length) return prop('valueList', '값 목록을 입력해 주세요.');
      if (c.type === 'pattern' && c.sliceOn && (!Number.isInteger(c.sliceStart) || !Number.isInteger(c.sliceLength) || c.sliceStart < 1 || c.sliceLength < 1)) return prop('sliceStart', '자리는 1 이상의 정수여야 합니다.');
      if (c.type === 'aggregate' && c.targetKind === 'number' && !Number.isFinite(c.targetValue)) return prop('targetValue', '비교 값을 입력해 주세요.');
      if (c.type === 'aggregate' && (c.targetKind === 'item' || c.targetKind === 'aggregate') && !c.rightKey) return prop('rightKey', '대조할 항목을 선택해 주세요.');
      for (var si = 0; si < sides.length; si++) {
        var sort = c[sides[si] + 'Sort'];
        if (sort && (!sort.by || !Number.isInteger(sort.limit) || sort.limit < 1)) return problem('[data-sort="' + sides[si] + '"][data-sort-prop="' + (!sort.by ? 'by' : 'limit') + '"]', '순위 기준 항목과 1 이상의 상위 행 수를 입력해 주세요.');
      }
      if (c.condition && c.conditionOp === 'IN' && !splitList(c.conditionValue).length) return prop('conditionValue', '조건 값 목록을 입력해 주세요.');
    }
    return null;
  }
  function clearEditorError() {
    editorError = null;
    root.querySelectorAll('.crb-field-error').forEach(function (el) { el.remove(); });
    root.querySelectorAll('[aria-invalid="true"]').forEach(function (el) { el.removeAttribute('aria-invalid'); el.removeAttribute('aria-describedby'); });
    var box = document.getElementById('crb-save-error');
    if (box) { box.hidden = true; box.textContent = ''; }
  }
  function applyEditorError(moveFocus) {
    if (!editorError) return;
    root.querySelectorAll('.crb-field-error').forEach(function (el) { el.remove(); });
    var box = document.getElementById('crb-save-error');
    box.hidden = false; box.textContent = editorError.message;
    if (editorError.branch != null && (editorError.branch !== state.draft.activeBranch || editorError.method !== activeCase(state.draft).activeMethod)) return;
    if (!editorError.selector) {
      if (moveFocus) { box.tabIndex = -1; box.focus(); box.scrollIntoView({ block: 'nearest' }); }
      return;
    }
    var field = root.querySelector(editorError.selector);
    if (!field) { if (moveFocus) { box.tabIndex = -1; box.focus(); } return; }
    var parent = field.parentElement;
    while (parent && parent !== root) { if (parent.tagName === 'DETAILS') parent.open = true; parent = parent.parentElement; }
    field.setAttribute('aria-invalid', 'true'); field.setAttribute('aria-describedby', 'crb-field-error');
    var error = document.createElement('span'); error.id = 'crb-field-error'; error.className = 'crb-field-error'; error.textContent = editorError.message;
    field.insertAdjacentElement('afterend', error);
    if (moveFocus) { field.focus(); field.scrollIntoView({ block: 'nearest' }); }
  }
  function showEditorError(issue) {
    editorError = issue;
    if (issue.branch != null) { selectBranch(state.draft, issue.branch); selectMethod(state.draft, issue.method || 0); }
    renderEditor(); applyEditorError(true);
  }
  /* 저장 요청이 끝날 때까지 입력·이동을 잠가 같은 내용을 중복 등록하지 않는다. */
  function setSaving(saving) {
    editorSaving = saving;
    var controls = Array.prototype.slice.call(root.querySelectorAll('input, select, button'));
    var workflow = document.getElementById('crb-wk'); if (workflow) controls.push(workflow);
    controls.forEach(function (el) {
      if (saving && !el.disabled) { el.setAttribute('data-save-disabled', 'true'); el.disabled = true; }
      else if (!saving && el.hasAttribute('data-save-disabled')) { el.removeAttribute('data-save-disabled'); el.disabled = false; }
    });
    var button = document.getElementById('crb-save'); if (button) button.textContent = saving ? '저장 중…' : '저장';
    var status = document.getElementById('crb-save-state'); if (status) status.textContent = saving ? '저장 중입니다.' : hasUnsavedChanges() ? '변경사항 있음' : '';
  }
  function save() {
    if (editorSaving) return;
    var d = state.draft, error = validateDraft(d);
    if (error) { showEditorError(error); return; }
    clearEditorError();
    var payload = { ruleId: d.ruleId, wkId: wkId, ruleNm: d.name.trim(), useYn: d.useYn, defJson: JSON.stringify(toDef(d)) };
    var savedOnServer = false, resultWarning = '';
    setSaving(true);
    request(base + '/rules', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload) })
      .then(function (saved) {
        savedOnServer = true;
        // 저장된 ID를 먼저 보존해 이후 목록 조회가 실패해도 같은 규칙을 다시 생성하지 않는다.
        if (saved && saved.ruleId != null) d.ruleId = saved.ruleId;
        editorBaseline = draftFingerprint();
        var previousIds = state.rules.map(function (r) { return r.ruleId; });
        // 목록 조회가 실패해도 저장 응답을 기준으로 완료 화면을 보여 재저장을 유도하지 않는다.
        var storedRule = Object.assign({}, payload, saved || {});
        state.rules = state.rules.filter(function (r) { return r.ruleId !== d.ruleId; }).concat(storedRule);
        return request(base + '/rules?wkId=' + encodeURIComponent(wkId)).catch(function (e) {
          console.warn('[cross-rule] 저장 후 규칙 목록 조회 실패', { wkId: wkId, ruleId: d.ruleId, error: e });
          resultWarning = '저장했습니다. 목록을 다시 불러오지 못해 저장한 규칙을 표시합니다.';
          return state.rules;
        }).then(function (rules) {
          state.rules = rules;
          if (!payload.ruleId) {
            state.search = '';
            var index = rules.findIndex(function (r) { return r.ruleId === d.ruleId || previousIds.indexOf(r.ruleId) < 0; });
            rulePaging.page = index < 0 ? 1 : Math.floor(index / rulePaging.pageSize) + 1;
          }
          // 저장 이후의 미리보기 실패는 저장 실패와 구분한다. 목록은 저장된 규칙으로 다시 그린다.
          return loadResults().catch(function (e) {
            console.warn('[cross-rule] 저장 후 판독 결과 조회 실패', { wkId: wkId, ruleId: d.ruleId, error: e });
            state.results = {};
            resultWarning = resultWarning ? '저장했습니다. 목록과 판독 결과는 다시 확인해 주세요.' : '저장했습니다. 판독 결과는 다시 확인해 주세요.';
          });
        }).then(function () { setSaving(false); roleReturn = false; state.view = 'rules'; render(); toast(resultWarning || '저장했습니다.'); });
      }).catch(function (e) {
        setSaving(false);
        console.warn('[cross-rule] 규칙 저장 또는 후속 목록 조회 실패', { wkId: wkId, ruleId: d.ruleId, saved: savedOnServer, error: e });
        showEditorError({ message: savedOnServer ? '규칙은 저장됐습니다. 목록을 불러오지 못했습니다. 규칙 목록으로 다시 이동해 주세요.' : e.message });
      });
  }

  /* ---------- 검증 결과 ---------- */
  function renderResults() {
    releaseViewers();
    var view = document.getElementById('crb-view');
    if (!currentTx()) { view.innerHTML = '<p class="crb-empty">이 업무에는 판독 건이 없습니다.</p>'; return; }
    var items = state.rules.filter(function (r) { return r.useYn === 'Y'; }).map(function (r) {
      return { rule: r, def: JSON.parse(r.defJson), result: resultOf(r) };
    }).filter(function (x) { return x.result; }).sort(function (a, b) { return ORDER.indexOf(a.result.status) - ORDER.indexOf(b.result.status); });
    var shown = items.filter(function (x) { return state.filter === 'ALL' || x.result.status === state.filter; });
    if (!shown.some(function (x) { return x.rule.ruleId === state.selected; })) state.selected = shown[0] && shown[0].rule.ruleId;
    var counts = ['ALL'].concat(ORDER).map(function (s) {
      var n = s === 'ALL' ? items.length : items.filter(function (x) { return x.result.status === s; }).length;
      return '<button type="button" class="crb-panel crb-count' + (state.filter === s ? ' is-active' : '') + '" aria-pressed="' + (state.filter === s) + '" data-filter="' + s + '">' + (s === 'ALL' ? '전체' : STATUS[s]) + '<strong>' + n + '</strong></button>';
    }).join('');
    view.innerHTML = '<div class="crb-bar"><div class="crb-bar__group"><span class="crb-label">판독 건</span>' + txSelect('crb-tx') + '</div></div><div class="crb-counts">' + counts + '</div>'
      + '<div class="crb-panel">' + (shown.length ? '<div class="crb-result"><div class="crb-result__list">' + shown.map(function (x) {
        return '<button type="button" class="crb-result__item' + (x.rule.ruleId === state.selected ? ' is-active' : '') + '" data-select="' + x.rule.ruleId + '">' + badge(x.result.status) + '<div>' + esc(x.rule.ruleNm) + '</div></button>';
      }).join('') + '</div><div class="crb-result__detail">' + resultDetail(shown.filter(function (x) { return x.rule.ruleId === state.selected; })[0]) + '</div></div>'
        : '<p class="crb-empty">' + (items.length ? STATUS[state.filter] + ' 결과가 없습니다.' : '사용 중인 규칙이 없습니다.') + '</p>') + '</div>';
    bindTx();
    view.querySelectorAll('[data-filter]').forEach(function (b) {
      b.addEventListener('click', function () { state.filter = b.getAttribute('data-filter'); renderResults(); });
    });
    view.querySelectorAll('[data-select]').forEach(function (b) {
      b.addEventListener('click', function () { state.selected = Number(b.getAttribute('data-select')); renderResults(); });
    });
    var selected = shown.filter(function (x) { return x.rule.ruleId === state.selected; })[0];
    if (selected) renderCards(selected);
    var edit = document.getElementById('crb-edit-rule');
    if (edit) edit.addEventListener('click', function () { openEditor(Number(edit.getAttribute('data-id'))); });
  }
  function resultDetail(x) {
    var draft = fromDef(x.rule);
    return '<div class="crb-result__head"><div class="crb-result__title"><h2>' + esc(x.rule.ruleNm) + '</h2>' + badge(x.result.status) + '</div>'
      + '<p class="crb-reason">' + esc(ruleSentence(x.rule)) + '</p>'
      + caseLine(x.result)
      + (x.result.reasons.length ? '<p class="crb-reason">' + esc(x.result.reasons.join(' ')) + '</p>' : '')
      + '<div class="crb-check-lines">' + x.result.checks.map(function (c, i) {
        if (c.children.length) return methodLines(draft, c);
        var e = checkById(draft, c.id), rule = !e ? '' : criterion(e) || (e.type === 'fixed' ? '정해진 값 "' + e.fixed + '"' : e.type === 'number' ? '계산값 ' + joinValues(c.right) : '');
        var reasons = checkReasons(e, c);
        /* 비교가 하나면 위 규칙 문장과 같으므로 다시 적지 않는다 */
        return '<div class="crb-check-line"><div class="crb-check-line__head">' + (x.result.checks.length > 1 ? '<span class="crb-check-line__no">비교 ' + (i + 1) + '</span>' : '') + badge(c.status)
          + (e && x.result.checks.length > 1 ? '<span>' + esc(sentence(e)) + '</span>' : '') + '</div>'
          + '<div class="crb-check-line__values">' + esc(joinValues(c.left)) + ' <span class="crb-pair__arrow">↔</span> ' + esc(rule || joinValues(c.right)) + '</div>'
          + (reasons ? '<div class="crb-check-line__reason">' + esc(reasons) + '</div>' : '') + '</div>';
      }).join('') + '</div>'
      + '<div class="crb-result__actions"><button type="button" class="crb-add" id="crb-edit-rule" data-id="' + x.rule.ruleId + '">이 규칙 수정</button>'
      + '<button type="button" class="btn-neutral" id="crb-compare" hidden>크게 보기</button></div></div>'
      + '<div class="cfv-live"><div class="cfv-viewers" id="crb-cards"></div></div>';
  }

  /* ---------- 결과 카드 ---------- */
  function releaseViewers() { window.CrossRuleCards.release(document.getElementById('crb-cards')); }
  function renderCards(x) {
    var container = document.getElementById('crb-cards');
    if (!container) return;
    var count = window.CrossRuleCards.render(container, {
      result: x.result, def: x.def, forms: state.forms, documents: currentTx().documents,
      wkId: wkId, transId: currentTx().transactionId, userId: page.getAttribute('data-user-id'),
      onSaved: function () {
        /* 고친 규칙의 결과가 바뀌어도 화면에서 사라지지 않게 전체로 돌린다 */
        state.filter = 'ALL';
        return reloadContext().then(function () { toast('저장하고 다시 판정했습니다.'); });
      }
    });
    var open = document.getElementById('crb-compare');
    open.hidden = count < 2;
    open.addEventListener('click', function () { window.CrossRuleCards.openCompare(x.rule.ruleNm); });
  }
  function reloadContext() {
    var current = currentTx().transactionId;
    return request(base + '/context?wkId=' + encodeURIComponent(wkId)).then(function (context) {
      applyContext(context);
      var index = state.transactions.findIndex(function (t) { return t.transactionId === current; });
      state.txIndex = index < 0 ? 0 : index;
      return loadResults();
    }).then(render);
  }

  /* ---------- 불러오기 ---------- */
  function loadRules() {
    return request(base + '/rules?wkId=' + encodeURIComponent(wkId)).then(function (rules) { state.rules = rules; return loadResults(); }).then(render);
  }
  bindHelp();
  root.addEventListener('input', function () { if (state.view === 'editor') clearEditorError(); }, true);
  root.addEventListener('change', function () { if (state.view === 'editor') clearEditorError(); }, true);
  window.addEventListener('beforeunload', function (event) {
    if (!hasUnsavedChanges()) return;
    event.preventDefault(); event.returnValue = '';
  });
  var workflowSelect = document.getElementById('crb-wk');
  if (workflowSelect) workflowSelect.addEventListener('change', function () {
    var selected = workflowSelect.value;
    workflowSelect.value = wkId;
    leaveEditor(function () {
      workflowSelect.value = selected;
      editorBaseline = state.draft ? draftFingerprint() : '';
      workflowSelect.form.submit();
    });
  });

  /* 직접 입력은 항목 칸의 처리보다 먼저 받는다. 취소하면 고르기 전 값으로 되돌리고 칸의 처리를 건너뛴다 */
  root.addEventListener('change', function (event) {
    var select = event.target;
    if (select.value !== CUSTOM_KEY || !select.hasAttribute('data-key-form')) return;
    var before = Array.prototype.filter.call(select.options, function (o) { return o.defaultSelected; })[0] || select.options[0];
    var key = (window.prompt('항목 이름을 판독 결과와 똑같이 입력해 주세요.\n표 항목은 표이름.열이름입니다. 예: 가족사항.관계구분', '') || '').trim();
    if (!key) {
      select.value = before.value;
      event.stopPropagation();
      return;
    }
    var formId = select.getAttribute('data-key-form');
    customKeys[formId] = (customKeys[formId] || []).concat(key);
    if (!Array.prototype.some.call(select.options, function (o) { return o.value === key; })) {
      select.add(new Option(key, key), select.options.length - 1);
    }
    select.value = key;
  }, true);
  if (!wkId) { root.innerHTML = '<p class="crb-empty">업무가 없습니다.</p>'; return; }
  Promise.all([request(base + '/context?wkId=' + encodeURIComponent(wkId)), request(base + '/rules?wkId=' + encodeURIComponent(wkId))])
    .then(function (r) {
      applyContext(r[0]);
      state.rules = r[1];
      return loadResults();
    }).then(render)
    .catch(function (e) { state.error = e.message; render(); });
})();
