/* 판독작업 목록: 업무별 묶기, 행 열기, 판독작업 담당자 맡기.
   조회 조건과 보기 방식은 GET 주소에 남겨 목록 복귀·브라우저 뒤로 가기에서 유지한다. */
(function () {
  'use strict';
  var root = document.querySelector('[data-review-list]');
  if (!root) return;
  var form = root.querySelector('form');
  var feedback = root.querySelector('[data-review-list-feedback]');
  var from = form.querySelector('[name="from"]');
  var to = form.querySelector('[name="to"]');

  /** 날짜가 뒤집힌 조회를 막는다. 브라우저의 기본 입력 오류 안내를 사용한다. */
  function validatePeriod() {
    to.setCustomValidity(from.value && to.value && from.value > to.value ? '종료일을 시작일 이후로 선택해 주세요.' : '');
  }
  from.addEventListener('change', validatePeriod);
  to.addEventListener('change', validatePeriod);
  form.addEventListener('submit', function (event) {
    validatePeriod();
    if (!form.reportValidity()) { event.preventDefault(); return; }
    var button = form.querySelector('[type="submit"]');
    button.disabled = true;
    button.setAttribute('aria-busy', 'true');
    button.textContent = '조회 중…';
  });
  window.addEventListener('pageshow', function () {
    var button = form.querySelector('[type="submit"]');
    button.disabled = false;
    button.removeAttribute('aria-busy');
    button.textContent = '조회';
  });
  var retry = root.querySelector('[data-review-retry]');
  if (retry) retry.addEventListener('click', function () { form.requestSubmit(); });

  /** 같은 업무의 행을 tbody로 옮겨 의미 있는 표 구조와 접기 상태를 함께 유지한다. */
  function groupByWorkflow() {
    var table = root.querySelector('.review-table');
    if (!table) return;
    var original = table.tBodies[0];
    var groups = new Map();
    original.querySelectorAll('[data-review-row]').forEach(function (row) {
      var key = row.dataset.wkId;
      if (!groups.has(key)) groups.set(key, { name: row.dataset.wkName, rows: [], verified: 0, forms: 0 });
      var group = groups.get(key);
      group.rows.push(row);
      group.verified += Number(row.dataset.verifiedCount) || 0;
      group.forms += Number(row.dataset.formCount) || 0;
    });
    var fragment = document.createDocumentFragment();
    groups.forEach(function (group, key) {
      var heading = document.createElement('tbody');
      var headingRow = document.createElement('tr');
      headingRow.className = 'review-table__group';
      var cell = document.createElement('td');
      cell.colSpan = table.tHead.rows[0].cells.length;
      var button = document.createElement('button');
      button.type = 'button';
      button.setAttribute('data-review-group-toggle', '');
      button.setAttribute('aria-expanded', 'true');
      var body = document.createElement('tbody');
      body.id = 'review-workflow-' + fragment.childNodes.length;
      button.setAttribute('aria-controls', body.id);
      var chevron = document.createElement('span');
      chevron.className = 'review-table__chevron';
      chevron.setAttribute('aria-hidden', 'true');
      var name = document.createElement('span');
      name.textContent = group.name || key;
      var meta = document.createElement('span');
      meta.className = 'review-table__group-meta';
      meta.textContent = '판독작업 ' + group.rows.length + '건 · 검증완료 ' + group.verified + ' / ' + group.forms;
      button.append(chevron, name, meta);
      cell.appendChild(button);
      headingRow.appendChild(cell);
      heading.appendChild(headingRow);
      group.rows.forEach(function (row) { body.appendChild(row); });
      button.addEventListener('click', function () {
        body.hidden = !body.hidden;
        button.setAttribute('aria-expanded', String(!body.hidden));
      });
      fragment.append(heading, body);
    });
    original.replaceWith(fragment);
  }
  var table = root.querySelector('.review-table');
  var rows = Array.from(root.querySelectorAll('[data-review-row]'));
  var pagination = root.querySelector('[data-review-pagination]');
  var initialQuery = new URLSearchParams(window.location.search);

  /** 페이지 이동은 조회 결과의 일부만 표시한다. 업무별 묶기는 해당 페이지 안에서 적용한다. */
  function renderPage(pager) {
    var address = new URL(window.location.href);
    address.searchParams.set('page', String(pager.page));
    address.searchParams.set('pageSize', String(pager.pageSize));
    window.history.replaceState(window.history.state, '', address);
    form.querySelector('[name="pageSize"]').value = pager.pageSize;
    root.querySelectorAll('[data-review-view]').forEach(function (link) {
      var viewAddress = new URL(link.href);
      viewAddress.searchParams.set('page', '1');
      viewAddress.searchParams.set('pageSize', String(pager.pageSize));
      link.href = viewAddress;
    });
    rows.forEach(function (row) {
      var link = row.querySelector('[data-review-detail]');
      var detailAddress = new URL(link.href);
      var list = new URLSearchParams(detailAddress.searchParams.get('list'));
      list.set('page', String(pager.page));
      list.set('pageSize', String(pager.pageSize));
      detailAddress.searchParams.set('list', list.toString());
      link.href = detailAddress;
    });
    if (!table) return;
    // DOM 행을 재사용해 담당자 버튼의 상태와 이벤트를 보존한다.
    Array.from(table.tBodies).forEach(function (body) { body.remove(); });
    var body = document.createElement('tbody');
    rows.slice(pager.start, pager.end).forEach(function (row) { body.appendChild(row); });
    table.appendChild(body);
    if (root.dataset.view === 'wk') groupByWorkflow();
  }
  if (pagination) {
    window.ListPagination.create({
      container: pagination, totalCount: rows.length,
      page: initialQuery.get('page'), pageSize: initialQuery.get('pageSize'),
      onChange: renderPage
    });
  }

  /** 링크의 새 탭·키보드 동작은 유지하고, 행의 빈 곳을 누르면 상세로 이동한다. */
  root.addEventListener('click', function (event) {
    var row = event.target.closest('[data-review-row]');
    if (!row || event.target.closest('a, button, input, select') || event.ctrlKey || event.metaKey || event.shiftKey || event.altKey) return;
    if (window.getSelection().toString()) return;
    var link = row.querySelector('[data-review-detail]');
    if (link) link.click();
  });

  /** 서버가 담당자를 확정한 뒤 같은 주소를 다시 읽어 상태와 내 담당 필터를 갱신한다. */
  rows.forEach(function (row) {
    var button = row.querySelector('[data-review-self-assign]');
    if (!button) return;
    button.addEventListener('click', function () {
      if (button.disabled || !root.dataset.reviewerId) return;
      button.disabled = true;
      button.setAttribute('aria-busy', 'true');
      button.textContent = '지정 중…';
      feedback.hidden = true;
      fetch(button.dataset.assignUrl, {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ userId: root.dataset.reviewerId })
      }).then(function (response) {
        if (!response.ok) throw new Error('담당자 지정 요청 실패: ' + response.status);
        window.location.reload();
      }).catch(function (error) {
        console.error('[검토 목록] 담당자 지정 실패 transId=' + button.closest('[data-review-row]').dataset.transId, error);
        feedback.textContent = '담당자를 지정하지 못했습니다. 다시 조회한 뒤 시도해 주세요.';
        feedback.classList.add('is-error');
        feedback.hidden = false;
        button.disabled = false;
        button.removeAttribute('aria-busy');
        button.textContent = '내가 맡기';
      });
    });
  });
}());
