/* 서식간 규칙 결과 카드: 규칙이 참조한 서식·항목마다 값 입력칸과 판독 이미지를 놓는다. 장수 제한은 없다.
   규칙 관리 화면과 서식간 검증 결과 상세가 함께 쓴다. 값 저장은 판독결과 검토의 확인값 저장을 그대로 쓴다. */
(function () {
  'use strict';
  /* 판정은 Agent 가 한다. 화면은 PASS·FAIL·UNKNOWN·NOT_APPLICABLE 로 표시한다 */
  var SCREEN_STATUS = { MATCH: 'PASS', MISMATCH: 'FAIL', UNVERIFIABLE: 'UNKNOWN', NOT_APPLICABLE: 'NOT_APPLICABLE' };

  /* 통과 방법 묶음은 children 에 방법별·비교별 결과가 있다 */
  function screenCheck(c) {
    return Object.assign({}, c, { status: SCREEN_STATUS[c.status], children: (c.children || []).map(screenCheck) });
  }

  function screenResult(result) {
    return {
      status: SCREEN_STATUS[result.status],
      reasons: result.reasons,
      caseLabel: result.caseLabel,
      checks: result.checks.map(screenCheck)
    };
  }

  function request(url, options) {
    return fetch(url, Object.assign({ cache: 'no-store' }, options)).then(function (response) {
      return response.json().catch(function () { return {}; }).then(function (body) {
        if (!response.ok) {
          var error = new Error(body.message || '처리하지 못했습니다. 잠시 후 다시 시도해 주세요.');
          error.status = response.status;
          throw error;
        }
        return body;
      });
    });
  }

  /* 엔진 문서 코드와 서식 코드가 같은 서식인가. Portal·Agent 의 sameFormCode 와 같다 */
  function sameFormCode(documentCode, formCode) {
    return !!formCode && (documentCode === formCode || documentCode === 'KJB_' + formCode);
  }

  function cardsOf(result, def) {
    var cards = [], byKey = {};
    function cardOf(formId, key, role) {
      var id = formId + '\u0000' + key + '\u0000' + (role || '');
      if (!byKey[id]) { byKey[id] = { formId: formId, key: key, role: role || '', values: [], failed: false }; cards.push(byKey[id]); }
      return byKey[id];
    }
    result.checks.forEach(function (c) {
      c.raws.forEach(function (raw) {
        var source = def.sources.filter(function (s) { return s.alias === raw.source; })[0];
        var card = cardOf(source.formId, raw.key, source.role);
        raw.values.forEach(function (v) {
          if (!card.values.some(function (o) { return (o.fieldId != null && o.fieldId === v.fieldId) || (o.cellId != null && o.cellId === v.cellId); })) card.values.push(v);
        });
        card.failed = card.failed || (raw.side === 'right' && c.status === 'FAIL');
      });
    });
    return cards;
  }

  function release(container) {
    if (!container) return;
    container.querySelectorAll('[data-cfv-viewer]').forEach(function (v) {
      if (v.documentViewer) v.documentViewer.release();
    });
  }

  function mergedBoxes(values) {
    var boxes = [];
    values.forEach(function (v) {
      try { boxes = boxes.concat(JSON.parse(v.bbox || '[]')); } catch (e) { /* 좌표가 깨진 값은 강조하지 않는다 */ }
    });
    return boxes;
  }

  /* 같은 서식이 여러 건일 때 카드별로 보던 문서. 값을 저장해 다시 그려도 유지한다 */
  var activeDocs = {};

  /* options: result(screenResult), def(정의 JSON), forms, documents, wkId, transId, userId, onSaved(저장 뒤 다시 그리기) */
  function render(container, options) {
    if (!compare || container !== compare.grid) lastOptions = options;
    var cards = cardsOf(options.result, options.def), template = document.getElementById('crb-card-template');
    function form(formId) { return options.forms.filter(function (f) { return f.formId === formId; })[0]; }
    function formName(formId) { var f = form(formId); return f ? f.formName : '(연결 해제된 서식)'; }
    function url(formId, documentId, action) {
      return contextPath + 'module/ocr/result/lefrm/' + encodeURIComponent(options.wkId) + '/' + formId + '/' + action
        + '?transId=' + encodeURIComponent(options.transId) + '&docId=' + encodeURIComponent(documentId);
    }
    container.style.setProperty('--cfv-columns', Math.max(1, Math.min(cards.length, 3)));
    container.classList.toggle('is-peek', cards.length > 3);
    cards.forEach(function (card, index) {
      var el = template.content.firstElementChild.cloneNode(true);
      var f = form(card.formId), docs = options.documents.filter(function (d) { return f && sameFormCode(d.formCode, f.formCode); });
      var cardKey = card.formId + '\u0000' + card.key;
      function docOrder(documentId) { return docs.findIndex(function (d) { return d.documentId === documentId; }); }
      function hasValues(d) { return card.values.some(function (v) { return v.documentId === d.documentId; }); }
      // 역할이 있는 카드는 그 역할의 문서만 넘겨 본다.
      if (card.role && card.values.length) docs = docs.filter(hasValues);
      el.querySelector('[data-card-letter]').textContent = index < 26 ? String.fromCharCode(65 + index) : 'A' + (index - 25);
      el.querySelector('[data-card-form]').textContent = formName(card.formId) + (card.role ? ' [' + card.role + ']' : '');
      el.querySelector('[data-card-key]').textContent = card.key;
      var viewerRoot = el.querySelector('[data-cfv-viewer]'), holder = el.querySelector('[data-card-values]'), ready = null;
      if (!card.values.length) holder.innerHTML = '<span class="crb-muted">값 없음</span>';
      card.values.forEach(function (v) {
        var row = document.createElement('div');
        row.className = 'cfv-edit crb-value' + (card.failed ? ' is-mismatch' : '') + (v.confirmed ? ' is-edited' : '');
        var input = document.createElement('input');
        input.className = 'input';
        input.type = 'text';
        input.maxLength = 1000;
        input.value = v.value == null ? '' : v.value;
        input.setAttribute('aria-label', formName(card.formId) + ' ' + card.key);
        // 마스킹 값은 원문을 덮어쓰지 않게 막는다. 문서를 못 찾는 값(연결 해제된 서식)은 저장 경로가 없다.
        input.disabled = docOrder(v.documentId) < 0 || String(v.value || '').indexOf('*') >= 0;
        var save = document.createElement('button');
        save.type = 'button';
        save.className = 'btn-neutral';
        save.textContent = '저장';
        save.disabled = true;
        function submit() { saveValue(url, options, card, v, input, save); }
        if (docs.length > 1 && docOrder(v.documentId) >= 0) {
          var tag = document.createElement('span');
          tag.className = 'crb-doc-tag';
          tag.textContent = (docOrder(v.documentId) + 1) + '건째';
          row.append(tag);
        }
        input.addEventListener('input', function () { save.disabled = input.value === (v.value == null ? '' : v.value); });
        input.addEventListener('keydown', function (event) {
          if (event.key === 'Enter' && !save.disabled) { event.preventDefault(); submit(); }
        });
        input.addEventListener('focus', function () {
          if (docOrder(v.documentId) < 0) return;
          if (activeDocs[cardKey] !== v.documentId) showDoc(v.documentId, v.bbox);
          else if (ready && v.bbox) ready.then(function () { viewerRoot.documentViewer.highlight(v.bbox); });
        });
        save.addEventListener('click', submit);
        row.append(input, save);
        holder.append(row);
        if (v.confirmed) {
          var orig = document.createElement('span');
          orig.className = 'cfv-edit__orig';
          orig.textContent = '인식값: ' + (v.recognizedValue || '(빈 값)');
          holder.append(orig);
        }
      });
      container.append(el);
      if (!docs.length) {
        viewerRoot.hidden = true;
        var empty = el.querySelector('[data-card-empty]');
        empty.hidden = false;
        empty.textContent = '이 판독 건에 해당 서식 문서가 없습니다.';
        return;
      }
      var viewer = window.attachDocumentViewer(viewerRoot), tabs = el.querySelector('[data-card-docs]');
      /* 고른 문서의 이미지를 열고 그 문서의 값을 강조한다. bbox 가 있으면 그 값만 강조한다 */
      function showDoc(documentId, bbox) {
        activeDocs[cardKey] = documentId;
        var order = docOrder(documentId);
        tabs.querySelector('[data-doc-position]').textContent = (order + 1) + ' / ' + docs.length + '건';
        tabs.querySelector('[data-doc-prev]').disabled = order === 0;
        tabs.querySelector('[data-doc-next]').disabled = order === docs.length - 1;
        var source = document.createElement('span');
        source.setAttribute('data-image-src', url(card.formId, documentId, 'view'));
        var boxes = mergedBoxes(card.values.filter(function (v) { return v.documentId === documentId; }));
        ready = viewer.open(source).then(function () {
          if (bbox) return viewer.highlight(bbox);
          if (boxes.length) return viewer.highlight(JSON.stringify(boxes));
        }).catch(function () { /* 뷰어가 상태 문구로 안내한다 */ });
      }
      if (docs.length > 1) {
        tabs.hidden = false;
        tabs.querySelector('[data-doc-prev]').addEventListener('click', function () { showDoc(docs[docOrder(activeDocs[cardKey]) - 1].documentId); });
        tabs.querySelector('[data-doc-next]').addEventListener('click', function () { showDoc(docs[docOrder(activeDocs[cardKey]) + 1].documentId); });
      }
      var remembered = docs.filter(function (d) { return d.documentId === activeDocs[cardKey]; })[0];
      showDoc((remembered || docs.filter(hasValues)[0] || docs[0]).documentId);
    });
    /* 크게 보기에서 값을 저장해 화면이 다시 그려지면 창도 새 판정으로 다시 그린다 */
    if (compare && container !== compare.grid) drawCompare();
    return cards.length;
  }

  /* 크게 보기: 카드를 두 줄씩 세로로 늘어놓는 창. 저장 확인 창(z-index 1000)이 위에 뜨도록 그 아래에 둔다 */
  var compare = null, lastOptions = null, compareFull = false;
  function drawCompare() {
    var top = compare.grid.scrollTop;
    release(compare.grid);
    compare.grid.replaceChildren();
    render(compare.grid, lastOptions);
    compare.grid.scrollTop = top;
  }
  function closeCompare() {
    if (!compare) return;
    release(compare.grid);
    compare.root.remove();
    if (compare.opener) compare.opener.focus();
    compare = null;
  }
  function openCompare(title) {
    var root = document.createElement('div');
    root.className = 'crb-compare';
    root.setAttribute('role', 'dialog');
    root.setAttribute('aria-modal', 'true');
    root.setAttribute('aria-label', '서류 크게 보기');
    var panel = document.createElement('div');
    panel.className = 'crb-compare__panel cfv-live cfv-clean';
    var head = document.createElement('div');
    head.className = 'crb-compare__head';
    var name = document.createElement('h2');
    name.textContent = title;
    var hint = document.createElement('span');
    hint.className = 'crb-compare__hint';
    hint.textContent = '휠로 위아래 이동, Ctrl + 휠로 이미지 확대·축소';
    var close = document.createElement('button');
    close.type = 'button';
    close.className = 'btn-neutral';
    close.textContent = '닫기';
    close.addEventListener('click', closeCompare);
    /* 브라우저 전체 화면에서는 body 에 뜨는 저장 확인 창이 보이지 않으므로 브라우저 창을 가득 채운다 */
    var full = document.createElement('button');
    full.type = 'button';
    full.className = 'btn-neutral';
    function showFull() {
      root.classList.toggle('is-full', compareFull);
      full.textContent = compareFull ? '창 크기로' : '전체 화면';
      full.setAttribute('aria-pressed', String(compareFull));
    }
    full.addEventListener('click', function () { compareFull = !compareFull; showFull(); });
    showFull();
    head.append(name, hint, full, close);
    var grid = document.createElement('div');
    grid.className = 'cfv-viewers';
    grid.setAttribute('data-viewer-ctrl-zoom', '');
    panel.append(head, grid);
    root.append(panel);
    root.addEventListener('mousedown', function (event) { if (event.target === root) closeCompare(); });
    root.addEventListener('keydown', function (event) { if (event.key === 'Escape') closeCompare(); });
    document.body.append(root);
    compare = { root: root, grid: grid, opener: document.activeElement };
    render(grid, lastOptions);
    close.focus();
  }

  /* 담당자가 아니면 맡을지 물어본다. 저장 실패와 저장 뒤 다시 읽기 실패를 구분해 알린다 */
  function saveValue(url, options, card, v, input, button) {
    button.disabled = true;
    var json = { 'Content-Type': 'application/json' };
    function post() {
      var body = v.cellId != null
        ? { fields: [], tables: [{ cellId: v.cellId, fildKey: v.key, fildVal: input.value }] }
        : { fields: [{ fildId: v.fieldId, fildKey: v.key, fildVal: input.value }], tables: [] };
      return request(url(card.formId, v.documentId, 'save'), { method: 'POST', headers: json, body: JSON.stringify(body) });
    }
    function saved() {
      return Promise.resolve().then(options.onSaved).catch(function () {
        openErrorModal('값은 저장했지만 판정 결과를 다시 불러오지 못했습니다. 다시 저장하지 말고 화면을 새로 고쳐 주세요.');
      });
    }
    function failed(error) {
      button.disabled = false;
      openErrorModal(error.message);
    }
    post().then(saved, function (error) {
      if (error.status !== 403) { failed(error); return; }
      openConfirmModal('이 판독작업의 담당자가 아닙니다. 내가 맡고 저장할까요?', function () {
        request(contextPath + 'module/ocr/result/wk/' + encodeURIComponent(options.wkId)
          + '/trans/' + encodeURIComponent(options.transId) + '/assignee', { method: 'POST', headers: json, body: JSON.stringify({ userId: options.userId }) })
          .then(post)
          .then(saved, failed);
      }, function () { button.disabled = false; });
    });
  }

  window.CrossRuleCards = { screenResult: screenResult, request: request, sameFormCode: sameFormCode, render: render, release: release, openCompare: openCompare };
})();
