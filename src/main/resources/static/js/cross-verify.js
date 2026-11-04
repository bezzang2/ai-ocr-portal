/* 통합검색 > 서식간 검증 결과 목록(templates/module/ocr/cross-verify.html).
   규칙을 바꾼 뒤 조회한 업무·스캔기간의 판독작업을 한꺼번에 다시 판정하고 목록을 다시 읽는다. */
(function () {
  'use strict';
  var container = document.querySelector('[data-cfv-pagination]');
  if (container) {
    var rows = Array.from(document.querySelectorAll('[data-cfv-row]'));
    var body = document.querySelector('.cfv-list tbody');
    var form = document.querySelector('[data-cfv-search]');
    var query = new URLSearchParams(window.location.search);
    window.ListPagination.create({
      container: container, totalCount: rows.length, page: query.get('page'), pageSize: query.get('pageSize'),
      onChange: function (pager) {
        var address = new URL(window.location.href);
        address.searchParams.set('page', String(pager.page));
        address.searchParams.set('pageSize', String(pager.pageSize));
        window.history.replaceState(window.history.state, '', address);
        form.querySelector('[name="pageSize"]').value = pager.pageSize;
        // 행 번호는 전체 조회 순서를 유지한다. 빈 결과 안내 행도 그대로 둔다.
        if (rows.length) body.replaceChildren.apply(body, rows.slice(pager.start, pager.end));
        rows.forEach(function (row) {
          var link = row.querySelector('a');
          var detail = new URL(link.href);
          detail.searchParams.set('list', address.searchParams.toString());
          link.href = detail;
        });
      }
    });
  }
  var button = document.querySelector('[data-cfv-evaluate-all]');
  if (!button) return;
  var root = typeof contextPath === 'string' ? contextPath : '/';

  function fail(message) {
    button.disabled = false;
    if (typeof openErrorModal === 'function') openErrorModal(message); else alert(message);
  }

  function run() {
    button.disabled = true;
    var query = new URLSearchParams();
    ['wkId', 'from', 'to'].forEach(function (key) { if (button.dataset[key]) query.set(key, button.dataset[key]); });
    fetch(root + 'module/ocr/crossVerify/evaluate?' + query, { method: 'POST', cache: 'no-store' })
      .then(function (response) {
        return response.json().catch(function () { return {}; }).then(function (body) {
          if (!response.ok) throw new Error(body.message || '처리하지 못했습니다. 잠시 후 다시 시도해 주세요.');
          return body;
        });
      })
      .then(function (result) {
        var message = '판독작업 ' + result.targets + '건 중 ' + result.evaluated + '건을 다시 판정했습니다.';
        if (result.failed) message += ' ' + result.failed + '건은 판정하지 못했습니다.';
        if (result.truncated) message += ' 대상이 많아 최근 스캔 500건만 판정했습니다.';
        var reload = function () { window.location.reload(); };
        if (typeof openSuccessModal === 'function') openSuccessModal(message, reload); else reload();
      })
      .catch(function (error) { fail(error.message); });
  }

  button.addEventListener('click', function () {
    var message = '조회한 업무·스캔기간(' + button.dataset.from + ' ~ ' + button.dataset.to
      + ')의 판독작업을 지금 규칙으로 다시 판정합니다. 진행할까요?';
    if (typeof openConfirmModal === 'function') openConfirmModal(message, run); else if (confirm(message)) run();
  });
})();
