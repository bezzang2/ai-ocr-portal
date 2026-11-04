/* 서식 선택, 인식·확인 정보와 뷰어를 같은 판독작업 기준으로 갱신한다.
   이전 파일 응답은 폐기한다. 확인값은 임시저장·검증완료로 서버에 저장하고(담당자만, js/result-review.js),
   저장하지 않은 값은 페이지를 닫기 전까지 파일별로 보존한다. */
(function () {
  var activeLefrmId = null;
  var activeImageSrc = null;
  var requestVersion = 0;
  var fieldAbort = null;
  var fieldsReady = false;
  var verifying = false;
  var saving = false;
  var activeFailed = false;
  var drafts = new Map();
  // 파일별 서버에 저장된 확인값(불러온 값 또는 마지막 저장 값). 저장 안 된 변경을 판단한다.
  var savedSnapshots = new Map();
  var currentChange = null;
  var regionHighlighters = new WeakMap();

  function viewer() {
    var root = document.querySelector('[data-document-viewer]');
    return root && root.documentViewer;
  }

  function selectRegion(node, field) {
    node.setAttribute('data-result-region', String(field.fildId || field.cellId || ''));
    function highlight() {
      document.querySelectorAll('[data-result-region].is-highlighted').forEach(function (el) { el.classList.remove('is-highlighted'); });
      node.classList.add('is-highlighted');
      if (viewer()) viewer().highlight(field.tokenBbox);
    }
    regionHighlighters.set(node, highlight);
    // 표 셀은 생성 시 아직 td 밖에 있으므로, 렌더링이 끝난 뒤 셀 전체에 연결한다.
    queueMicrotask(function () {
      var hoverArea = node.closest('td') || node;
      hoverArea.addEventListener('pointerenter', function (event) {
        if (event.pointerType !== 'touch') highlight();
      });
      hoverArea.addEventListener('pointerleave', function (event) {
        if (event.pointerType === 'touch' || !node.classList.contains('is-highlighted')) return;
        var focused = document.activeElement && document.activeElement.closest('[data-result-region]');
        if (focused === node) return;
        if (focused && regionHighlighters.has(focused)) {
          regionHighlighters.get(focused)();
          return;
        }
        document.querySelectorAll('[data-result-region].is-highlighted').forEach(function (el) { el.classList.remove('is-highlighted'); });
        if (viewer()) viewer().clearFocus();
      });
    });
    node.addEventListener('focusin', highlight);
    node.addEventListener('click', highlight);
    if (node.tagName === 'SPAN') {
      node.tabIndex = 0;
      node.setAttribute('role', 'button');
      node.setAttribute('aria-label', (field.fildKey || '표 셀') + ' 인식 영역 보기');
      node.addEventListener('keydown', function (event) {
        if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); highlight(); }
      });
    }
    return node;
  }

  function activeItem() {
    return document.querySelector('.result-file-group__file.is-active');
  }

  /** 저장·검증완료를 할 수 없는 이유(실패 문서·담당자 아님·검증완료). 할 수 있으면 빈 문자열 */
  function lockReason() {
    // 실패·취소 문서는 확인할 추출값이 없어 검증완료 대상이 아니다.
    if (activeFailed) return '실패·취소된 문서는 검증완료할 수 없습니다.';
    return window.resultReview ? window.resultReview.lockMessage(activeItem()) : '';
  }

  function updateVerifyButton() {
    var reason = lockReason();
    var verified = !!(window.resultReview && activeItem() && window.resultReview.isVerified(activeItem()));
    var button = document.querySelector('[data-verify-btn]');
    if (button) {
      if (isViewerWebView()) {
        // BPR_SCAN 웹뷰에서는 담당자/검증완료 상태와 무관하게 버튼을 활성화한다.
        button.disabled = !activeImageSrc || verifying || saving;
      } else {
        button.disabled = !fieldsReady || verifying || saving || !!reason;
      }
      button.textContent = verified ? '검증완료됨' : '이 서식 검증완료 ✓';
      if (reason) button.title = reason; else button.removeAttribute('title');
    }
    var cancel = document.querySelector('[data-verify-cancel-btn]');
    if (cancel) {
      cancel.hidden = !(fieldsReady && !activeFailed && window.resultReview && window.resultReview.canCancelVerify(activeItem()));
      cancel.disabled = verifying || saving;
    }
    var save = document.querySelector('[data-result-save]');
    if (save) {
      save.disabled = !fieldsReady || verifying || saving || !!reason;
      if (reason) save.title = reason; else save.removeAttribute('title');
    }
    var reset = document.querySelector('[data-result-reset]');
    if (reset) reset.disabled = !fieldsReady || !!reason;
    // 담당자가 아니거나 검증완료한 서식은 확인값을 읽기 전용으로 둔다.
    document.querySelectorAll('[data-result-confirm-group] [data-fild-id], [data-result-confirm-table-group] [data-cell-id]').forEach(function (control) {
      if (control.tagName === 'SELECT') control.disabled = !!reason;
      else control.readOnly = !!reason;
    });
    var panel = document.querySelector('.result-panel--confirm');
    if (panel) panel.classList.toggle('is-locked', !!reason);
  }

  function snapshotOf(values) {
    return {
      fields: new Map(values.fields.map(function (f) { return [String(f.fildId), f.fildVal]; })),
      cells: new Map(values.tables.map(function (c) { return [String(c.cellId), c.fildVal]; }))
    };
  }

  function isDirty(src, values) {
    var saved = savedSnapshots.get(src);
    if (!saved || !values) return false;
    return values.fields.some(function (f) { return saved.fields.has(String(f.fildId)) && saved.fields.get(String(f.fildId)) !== f.fildVal; })
        || values.tables.some(function (c) { return saved.cells.has(String(c.cellId)) && saved.cells.get(String(c.cellId)) !== c.fildVal; });
  }

  function currentValues() {
    return { fields: collectConfirmFields(), tables: collectConfirmTables() };
  }

  function hasUnsavedChanges() {
    if (activeImageSrc && fieldsReady && isDirty(activeImageSrc, currentValues())) return true;
    for (var entry of drafts) {
      if (entry[0] !== activeImageSrc && isDirty(entry[0], entry[1])) return true;
    }
    return false;
  }

  // Counts describe current values only; they are not a saved/reviewed status.
  function updateReview() {
    var selected = document.querySelector('.result-file-group__file.is-active');
    var fileName = document.querySelector('[data-result-selected-file]');
    if (fileName) {
      fileName.textContent = selected ? (selected.dataset.file || selected.textContent.trim()) : '서식을 선택해 주세요';
      fileName.title = fileName.textContent;
    }
    var count = document.querySelector('[data-result-file-count]');
    // 결과·결과 없음·실패 행을 모두 센다(워크플로 건수와 같은 기준). "내 담당만"으로 숨긴 행은 뺀다.
    if (count) count.textContent = document.querySelectorAll('[data-result-row]:not(.is-filtered-out)').length;
    document.querySelectorAll('.result-file-group__file').forEach(function (item) {
      if (item === selected) item.setAttribute('aria-current', 'true');
      else item.removeAttribute('aria-current');
      item.title = item.dataset.reviewTitle || item.dataset.file || item.textContent.trim();
    });
    var controls = document.querySelectorAll('[data-result-confirm-group] [data-fild-id], [data-result-confirm-table-group] [data-cell-id]');
    var changedCount = 0;
    controls.forEach(function (control) {
      var changed = control.value !== control.dataset.originalValue;
      if (changed) changedCount++;
      var container = control.closest('.result-field, td');
      if (!container) return;
      container.classList.toggle('is-modified', changed);
      var marker = container.querySelector('[data-result-change-label]');
      if (!marker) {
        marker = document.createElement('span');
        marker.className = 'ocr-review__change-label';
        marker.setAttribute('data-result-change-label', '');
        marker.textContent = '수정됨';
        (container.querySelector('.result-field__label') || container).appendChild(marker);
      }
      marker.hidden = !changed;
    });
    updateChangeNavigation();
    var summary = document.querySelector('[data-result-review-summary]');
    if (summary) {
      var text = fieldsReady ? '확인 항목 ' + controls.length + '개 · 수정 ' + changedCount + '개' : (selected ? '확인 정보를 불러오는 중…' : '서식을 선택해 주세요.');
      var unsaved = fieldsReady && isDirty(activeImageSrc, currentValues());
      if (fieldsReady && selected) {
        if (selected.dataset.verified === 'true') text += ' · 검증완료 ' + [selected.dataset.vrfyUserNm, selected.dataset.vrfyDt].filter(Boolean).join(' ');
        else if (unsaved) text += ' · 저장 안 됨';
        else if (selected.dataset.saveDt) text += ' · 저장됨 ' + selected.dataset.saveDt;
      }
      summary.textContent = text;
      summary.classList.toggle('has-changes', changedCount > 0);
      summary.classList.toggle('is-unsaved', unsaved);
    }
  }

  function changedControls() {
    return Array.from(document.querySelectorAll('[data-result-confirm-group] .is-modified [data-fild-id], [data-result-confirm-table-group] .is-modified [data-cell-id]'));
  }

  function updateChangeNavigation() {
    var controls = changedControls();
    var index = controls.indexOf(currentChange);
    var position = document.querySelector('[data-result-change-position]');
    if (position) position.textContent = (index < 0 ? 0 : index + 1) + ' / ' + controls.length;
    document.querySelectorAll('[data-result-change-action]').forEach(function (button) {
      button.disabled = !fieldsReady || !controls.length;
    });
  }

  function moveChange(direction) {
    var controls = changedControls();
    if (!fieldsReady || !controls.length) return;
    var index = controls.indexOf(currentChange);
    index = index < 0 ? (direction > 0 ? 0 : controls.length - 1) : (index + direction + controls.length) % controls.length;
    currentChange = controls[index];
    var workspace = document.querySelector('[data-result-workspace]');
    if (workspace && workspace.classList.contains('is-panels-collapsed')) {
      var toggle = document.querySelector('[data-workspace-toggle="panels"]');
      if (toggle) toggle.click();
    }
    var foldedPanel = currentChange.closest('.result-panel.is-user-collapsed');
    if (foldedPanel) {
      var restore = foldedPanel.querySelector('[data-panel-restore-btn]');
      if (restore) restore.click();
    }
    var expanded = document.querySelector('[data-panel-expand] .is-expanded [data-panel-expand-btn]');
    if (currentChange.closest('.is-collapsed') && expanded) expanded.click();
    currentChange.focus({preventScroll: true});
    currentChange.scrollIntoView({block: 'nearest', inline: 'nearest'});
    updateChangeNavigation();
  }

  function saveDraft() {
    if (activeImageSrc && fieldsReady) drafts.set(activeImageSrc, currentValues());
  }

  /** 저장한 확인값(cnfmYn=Y)이 있으면 그 값, 없으면 인식값 */
  function confirmedValue(item) {
    return item.cnfmYn === 'Y' ? (item.cnfmVal || '') : (item.fildVal || '');
  }

  function restoreDraft(src) {
    var draft = drafts.get(src);
    if (!draft) return;
    var fields = new Map(draft.fields.map(function (f) { return [String(f.fildId), f.fildVal]; }));
    var cells = new Map(draft.tables.map(function (c) { return [String(c.cellId), c.fildVal]; }));
    document.querySelectorAll('[data-result-confirm-group] [data-fild-id]').forEach(function (input) {
      if (fields.has(input.dataset.fildId)) input.value = fields.get(input.dataset.fildId);
    });
    document.querySelectorAll('[data-result-confirm-table-group] [data-cell-id]').forEach(function (input) {
      if (cells.has(input.dataset.cellId)) input.value = cells.get(input.dataset.cellId);
    });
  }

  // 모든 하위 요청이 같은 판독작업(transId) 기준으로 조회
  function toActionUrl(viewSrc, action) {
    return viewSrc.replace(/\/view(?=\?|$)/, "/" + action);
  }

  function showImage(item) {
    if (viewer()) { viewer().open(item); return; }
    var img = document.querySelector(".doc-viewer .doc-viewer__image");
    var pdf = document.querySelector(".doc-viewer [data-pdf-target]");
    if (!img && !pdf) return;
    var src = item.getAttribute("data-image-src");
    if (!src) return;
    var isPdf = item.getAttribute("data-is-pdf") === "true";

    if (isPdf) {
      if (pdf) {
        pdf.src = src + "#toobar=0&navpanes=0";
        pdf.style.display = "";
      }
      if (img) img.style.display = "none";
    } else {
      if (img) {
        img.src = src;
        img.alt = item.getAttribute("data-file") || "";
        img.style.display = "";
      }
      if (pdf) pdf.style.display = "none";
    }

    if (typeof window.docViewerZoomReset == "function") window.docViewerZoomReset();
  }

  // 인식 정보 - grpId 유무와 무관하게 모든 필드를 readonly 입력창 한 줄로 표시한다.
  function buildRecogFieldNode(field) {
    var wrap  = document.createElement("div");
    wrap.className = "result-field";

    var label = document.createElement("span");
    label.className = "result-field__label";
    label.textContent = field.fildKey || "";
    wrap.appendChild(label);

    var row = document.createElement("div");
    row.className = "result-field__row";

    var input = document.createElement("input");
    input.className = "input";
    input.type = "text";
    input.readOnly = true;
    input.setAttribute("aria-label", (field.fildKey || "항목") + " 인식값");
    input.value = field.fildVal || "";
    row.appendChild(input);

    wrap.appendChild(row);
    return selectRegion(wrap, field);
  }

  // 확인 정보 - grpId 있는 필드만 <select>(현재 값 1개를 옵션으로 넣은 셀렉트박스)로,
  // 없는 필드는 수정 가능한 입력창(readonly 없음)으로 표시한다. 각 입력 엘리먼트에
  // data-fild-id/data-fild-key 를 심어, 검증완료 클릭 시 collectConfirmFields 가
  // 사용자가 최종 확인/수정한 값을 그대로 걷어갈 수 있도록 한다.
  function buildConfirmFieldNode(field) {
    var wrap = document.createElement("div");
    wrap.className = "result-field";

    var label = document.createElement("span");
    label.className = "result-field__label";
    label.textContent = field.fildKey || "";
    wrap.appendChild(label);

    var row = document.createElement("div");
    row.className = "result-field__row";

    var control;
    if (field.grpId != null && field.grpId != 0) {
      control = document.createElement("select");
      control.className = "input";
      var option = document.createElement("option");
      option.textContent = confirmedValue(field);
      control.appendChild(option);
      control.dataset.originalValue = control.value;
    } else {
      control = document.createElement("input");
      control.className = "input";
      control.type = "text";
      control.value = confirmedValue(field);
      // 수정 표시·수정 초기화의 기준은 인식값이다. 저장한 확인값이 다르면 수정됨으로 보인다.
      control.dataset.originalValue = field.fildVal || "";
    }
    control.setAttribute("aria-label", (field.fildKey || "항목") + " 확인값");
    control.setAttribute("data-fild-id", field.fildId);
    control.setAttribute("data-fild-key", field.fildKey || "");
    row.appendChild(control);

    wrap.appendChild(row);
    return selectRegion(wrap, field);
  }

  function renderFields(selector, buildNode, fields) {
    var group = document.querySelector(selector);
    if (!group) return;

    group.innerHTML = "";
    if (!fields || fields.length === 0) {
      group.textContent = '표시할 항목이 없습니다.';
      return;
    }
    fields.forEach(function (field) {
      group.appendChild(buildNode(field));
    });
  }

  // 표 1건 만든다
  function buildTableSectionNode(table, buildCellNode) {
    var section = document.createElement("div");
    section.className = "result-panel__section";

    var title = document.createElement("div");
    title.className = "section-title";
    title.textContent = table.tblKey || "";
    section.appendChild(title);

    var el = document.createElement("table");
    el.className = "table result-panel__table";

    var columns = table.columns || [];
    var thead = document.createElement("thead");
    var headRow = document.createElement("tr");
    columns.forEach(function (col) {
      var th = document.createElement("th");
      th.textContent = col.colNm || "";
      headRow.appendChild(th);
    });
    thead.appendChild(headRow);
    el.appendChild(thead);

    var tbody = document.createElement("tbody");
    var rows = table.rows || [];
    rows.forEach(function (row) {
      var tr = document.createElement("tr");
      var cellsByColId = {};
      (row.cells || []).forEach(function (cell) {
        cellsByColId[cell.colId] = cell;
      });
      columns.forEach(function (col) {
        var td = document.createElement("td");
        var cell = cellsByColId[col.colId];
        if (cell) {
          td.appendChild(buildCellNode(cell));
        } else {
          td.innerHTML = "&nbsp;";
        }
        tr.appendChild(td);
      });
      tbody.appendChild(tr);
    });
    el.appendChild(tbody);

    var scroll = document.createElement("div");
    scroll.className = "result-panel__table-scroll";
    scroll.appendChild(el);

    section.appendChild(scroll);
    return section;
  }

  // 인식 정보 - 셀 값을 읽기전용 텍스트로 표시한다.
  function buildRecogCellNode(cell) {
    var span = document.createElement("span");
    span.textContent = cell.fildVal || "";
    return selectRegion(span, cell);
  }

  // 확인 정보 - 셀 값을 수정 가능한 입력창으로 표시한다.
  function buildConfirmCellNode(cell) {
    var input = document.createElement("input");
    input.className = "input";
    input.type = "text";
    input.value = confirmedValue(cell);
    input.dataset.originalValue = cell.fildVal || "";
    input.setAttribute("aria-label", (cell.fildKey || "표 셀") + " 확인값");
    input.setAttribute("data-cell-id", cell.cellId);
    input.setAttribute("data-fild-key", cell.fildKey || "");
    return selectRegion(input, cell);
  }

  function renderTables(selector, buildCellNode, tables) {
    var group = document.querySelector(selector);
    if (!group) return;

    group.innerHTML = "";
    if (!tables || tables.length === 0) return;

    tables.forEach(function (table) {
      group.appendChild(buildTableSectionNode(table, buildCellNode));
    });
  }

  async function showFields(item) {
    var lefrmId = item.getAttribute('data-lefrm-id');
    var imageSrc = item.getAttribute('data-image-src');
    if (!lefrmId || !imageSrc) return;
    saveDraft();
    if (fieldAbort) fieldAbort.abort();
    fieldAbort = new AbortController();
    var version = ++requestVersion;
    activeLefrmId = lefrmId;
    activeImageSrc = imageSrc;
    activeFailed = item.hasAttribute('data-result-failed');
    fieldsReady = false;
    currentChange = null;
    updateVerifyButton();
    showCrossWarning();
    ['[data-result-field-group]', '[data-result-confirm-group]', '[data-result-table-group]', '[data-result-confirm-table-group]'].forEach(function (selector) {
      var node = document.querySelector(selector);
      if (node) node.replaceChildren();
    });
    ['[data-result-field-group]', '[data-result-confirm-group]'].forEach(function (selector) {
      var group = document.querySelector(selector);
      if (group) group.textContent = '항목을 불러오는 중…';
    });
    updateReview();
    try {
      var responses = await Promise.all(['recog-fields', 'recog-tables'].map(function (action) {
        return fetch(toActionUrl(imageSrc, action), { signal: fieldAbort.signal, cache: 'no-store' }).then(function (response) {
          if (!response.ok) throw new Error('인식 정보를 불러올 수 없습니다. 서식을 다시 선택해 주세요.');
          return response.json();
        });
      }));
      if (version !== requestVersion) return;
      renderFields('[data-result-field-group]', buildRecogFieldNode, responses[0]);
      renderFields('[data-result-confirm-group]', buildConfirmFieldNode, responses[0]);
      renderTables('[data-result-table-group]', buildRecogCellNode, responses[1]);
      renderTables('[data-result-confirm-table-group]', buildConfirmCellNode, responses[1]);
      savedSnapshots.set(imageSrc, snapshotOf(currentValues()));
      restoreDraft(imageSrc);
      fieldsReady = true;
    } catch (error) {
      if (version !== requestVersion || error.name === 'AbortError') return;
      var group = document.querySelector('[data-result-field-group]');
      if (group) { group.textContent = '인식 정보를 불러올 수 없습니다. 서식을 다시 선택해 주세요.'; group.setAttribute('role', 'status'); }
      var confirmGroup = document.querySelector('[data-result-confirm-group]');
      if (confirmGroup) confirmGroup.textContent = '확인 정보를 불러오지 못했습니다.';
    } finally {
      if (version === requestVersion) {
        updateVerifyButton();
        updateReview();
        var summary = document.querySelector("[data-result-review-summary]");
        if (summary && !fieldsReady) summary.textContent = "확인 정보를 불러오지 못했습니다. 서식을 다시 선택해 주세요.";
      }
    }
  }

  // "확인 정보"(data-result-confirm-group) 안s의 모든 입력 엘리먼트에서 현재 값을 걷어
  // { fildId, fildKey, fildVal } 배열로 만든다.
  function collectConfirmFields() {
    var group = document.querySelector("[data-result-confirm-group]");
    if (!group) return [];

    return Array.prototype.map.call(
        group.querySelectorAll("[data-fild-id]"),
        function (control) {
          return {
            fildId: Number(control.getAttribute("data-fild-id")),
            fildKey: control.getAttribute("data-fild-key") || "",
            fildVal: control.value
          };
        }
    );
  }

  function collectConfirmTables() {
    var group = document.querySelector("[data-result-confirm-table-group]");
    if (!group) return [];

    return Array.prototype.map.call(
        group.querySelectorAll("[data-cell-id]"),
        function (control) {
          return {
            cellId: Number(control.getAttribute("data-cell-id")),
            fildKey: control.getAttribute("data-fild-key") || "",
            fildVal: control.value
          };
        }
    );
  }

  function verifyResult() {
    // BPR_SCAN 웹뷰에서는 확인 모달 없이 바로 결과 JSON 다운로드 + 완료 신호를 보내 닫는다.
    if (isViewerWebView()) {
      notifyViewerVerifyCompleted();
      return;
    }
    if (!activeLefrmId || !activeImageSrc || !fieldsReady || verifying) {
      window.openInformationModal("검증할 서식을 서식목록에서 먼저 선택해 주세요.");
      return;
    }
    var reason = lockReason();
    if (reason) {
      window.openInformationModal(reason);
      return;
    }
    // 공통 확인 모달(common/commonModals.html). "검증완료"를 누르면 전송한다.
    var item = activeItem();
    crossWarningOf(item ? item.getAttribute("data-trans-id") : "").then(function (warning) {
      var text = crossWarningText(warning);
      window.openConfirmModal(text ? "서식간 규칙 " + text + "이 있습니다. 그래도 검증을 완료하시겠습니까?" : "검증을 완료하시겠습니까?", sendVerify);
    });
  }

  /** 판독작업의 서식간 경고 수. 값을 저장하면 다시 판정되므로 그때 버린다. 못 읽어도 검토·검증완료는 막지 않는다 */
  var crossWarnings = new Map();
  function crossWarningOf(transId) {
    if (!transId) return Promise.resolve(null);
    if (crossWarnings.has(transId)) return crossWarnings.get(transId);
    var loaded = fetch(contextPath + 'module/ocr/crossVerify/' + encodeURIComponent(transId) + '/detail', { cache: 'no-store' })
      .then(function (res) {
        if (!res.ok) throw new Error('status ' + res.status);
        return res.json();
      })
      .then(function (detail) {
        var count = function (code) { return detail.rules.filter(function (rule) { return rule.result === code; }).length; };
        return { mismatch: count('MISMATCH'), unverifiable: count('UNVERIFIABLE') };
      })
      .catch(function (err) {
        console.warn('서식간 경고 수를 읽지 못했습니다.', transId, err);
        crossWarnings.delete(transId);
        return null;
      });
    crossWarnings.set(transId, loaded);
    return loaded;
  }

  function crossWarningText(warning) {
    if (!warning || (!warning.mismatch && !warning.unverifiable)) return '';
    return [warning.mismatch ? '불일치 ' + warning.mismatch + '건' : '', warning.unverifiable ? '검증불가 ' + warning.unverifiable + '건' : '']
      .filter(Boolean).join(', ');
  }

  /** 서식 선택·저장 뒤 해당 판독작업의 상단 경고 건수만 갱신한다. 조회 실패 시 기존 요약을 유지한다. */
  function showCrossWarning() {
    var item = activeItem();
    var transId = item ? item.getAttribute('data-trans-id') : '';
    var summary = Array.from(document.querySelectorAll('[data-cross-summary]')).find(function (node) {
      return node.dataset.transId === transId;
    });
    if (!summary) return;
    crossWarningOf(transId).then(function (warning) {
      // BPR에서 다른 작업의 서식을 선택해도 응답이 원래 작업의 요약에만 반영되게 한다.
      if (!warning) return;
      summary.querySelector('[data-cross-mismatch]').textContent = '불일치 ' + warning.mismatch;
      summary.querySelector('[data-cross-unverifiable]').textContent = '검증불가 ' + warning.unverifiable;
      summary.hidden = !warning.mismatch && !warning.unverifiable;
    });
  }

  /** 확인값을 서버에 보낸다(action: save 임시저장, verify 검증완료). 응답은 서식의 검토 상태다. */
  function submitValues(action, failureMessage) {
    var item = activeItem();
    var src = activeImageSrc;
    var payload = currentValues();
    return fetch(toActionUrl(src, action), {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload)
    }).then(function (res) {
      return res.json().catch(function () { return {}; }).then(function (body) {
        if (!res.ok) throw new Error(body.message || failureMessage);
        return body;
      });
    }).then(function (state) {
      savedSnapshots.set(src, snapshotOf(payload));
      if (window.resultReview) window.resultReview.applyDocState(item, state);
      if (item) crossWarnings.delete(item.getAttribute('data-trans-id'));
      showCrossWarning();
      return state;
    });
  }

  function isViewerWebView() {
    return !!(window.chrome && window.chrome.webview);
  }

  /** 현재 화면번호(scrn_no)를 반환한다. 뷰어 URL 쿼리 또는 서식목록 data-scrn-no 에서 읽는다. */
  function viewerScreenNo() {
    var nav = document.getElementById("result-file-list");
    var v = nav ? (nav.getAttribute("data-scrn-no") || "") : "";
    if (!v && window.location.search) {
      var q = new URLSearchParams(window.location.search);
      v = q.get("scrn_no") || q.get("screenNo") || q.get("scrnNo") || "";
    }
    return (v || "").trim();
  }

  function notifyViewerVerifyCompleted() {
    var item = activeItem();
    var transId = item ? item.getAttribute("data-trans-id") : "";
    var docId = item ? item.getAttribute("data-doc-id") : "";
    var group = item ? item.closest(".result-file-group") : null;
    var wkId = group ? (group.getAttribute("data-wk-id") || "") : "";
    var screenNo = viewerScreenNo();
    // 결과(raw) + 매핑정보를 함께 가져와 완료 신호에 담아 보낸다.
    fetchCombinedPayload(transId, wkId, screenNo).then(function (payload) {
      var signal = {
        event: "VERIFY_COMPLETED",
        workflowId: wkId,
        resultKey: activeLefrmId || "",
        screenNo: screenNo || (payload.screenNo || ""),
        transactionId: transId,
        result: payload.result,
        mapping: payload.mapping
      };
      // 결과/매핑 JSON 파일도 함께 내려받는다(운영자 확인용).
      downloadJsonFile(transId, docId, payload.result);
      if (payload.mapping) downloadMappingFile(transId, payload.mapping);
      window.chrome.webview.postMessage(JSON.stringify(signal));
    }).catch(function (err) {
      console.error("결과/매핑 전송 실패", err);
    });
  }

  /** 결과(raw)와 매핑정보를 함께 조회한다. mapping 은 등록 안 된 경우 null. */
  function fetchCombinedPayload(transId, wkId, screenNo) {
    var item = activeItem();
    var base = item ? (item.getAttribute("data-image-src") || "").split("/module/ocr/result/")[0] : "";
    var resultUrl = base + "/module/ocr/result/trans/" + encodeURIComponent(transId) + "/raw";
    // 옵션1: 로컬 매핑 파일을 화면번호(scrn_no)로 조회한다. 화면번호가 없으면 매핑은 생략한다.
    var mappingUrl = screenNo && /^[A-Za-z0-9_-]{1,40}$/.test(screenNo)
      ? base + "/module/ocr/result/mapping?screenNo=" + encodeURIComponent(screenNo)
      : null;
    var mappingPromise = mappingUrl
      ? fetch(mappingUrl, { cache: "no-store" }).then(function (res) {
          if (res.status === 404) return null;
          return res.json().catch(function () { return {}; }).then(function (b) {
            if (!res.ok) throw new Error(b.message || "매핑 JSON을 가져오지 못했습니다.");
            return b;
          });
        })
      : Promise.resolve(null);
    return Promise.all([
      fetch(resultUrl, { cache: "no-store" }).then(function (res) {
        return res.json().catch(function () { return {}; }).then(function (b) {
          if (!res.ok) throw new Error(b.message || "결과 JSON을 가져오지 못했습니다.");
          return b;
        });
      }),
      mappingPromise
    ]).then(function (arr) {
      var result = arr[0];
      var mapping = arr[1];
      return {
        screenNo: (mapping && mapping.screenNo) || screenNo || "",
        transactionId: transId,
        workflowId: wkId,
        result: result,
        mapping: mapping
      };
    });
  }

  /** JSON 객체를 브라우저 다운로드로 내려보낸다(WebView2 는 별도 DownloadStarting 처리 필요). */
  function downloadJsonFile(transId, docId, data) {
    var text = JSON.stringify(data, null, 2);
    var name = transId + (docId ? "-" + docId : "") + ".json";
    triggerBlobDownload(text, name, "application/json");
  }

  function downloadMappingFile(transId, mapping) {
    var text = JSON.stringify(mapping, null, 2);
    var name = transId + "-mapping.json";
    triggerBlobDownload(text, name, "application/json");
  }

  function triggerBlobDownload(text, name, type) {
    var blobUrl = URL.createObjectURL(new Blob([text], { type: type || "application/json" }));
    var link = document.createElement("a");
    link.href = blobUrl;
    link.download = name;
    document.body.appendChild(link);
    link.click();
    link.remove();
    setTimeout(function () { URL.revokeObjectURL(blobUrl); }, 1000);
  }
  function sendVerify() {
    if (verifying) return;
    verifying = true;
    updateVerifyButton();
    var src = activeImageSrc;
    submitValues("verify", "검증완료 처리 중 오류가 발생했습니다.")
        .then(function () {
          drafts.delete(src);
          if (isViewerWebView()) {
            notifyViewerVerifyCompleted();
            return;
          }
          window.openSuccessModal("검증완료 되었습니다.");
        })
        .catch(function (err) {
          console.error(err);
          window.openErrorModal(err.message);
        })
        .finally(function () {
          verifying = false;
          updateVerifyButton();
          updateReview();
        });
  }

  function cancelVerify() {
    var item = activeItem();
    if (verifying || saving || !item || !window.resultReview || !window.resultReview.canCancelVerify(item)) return;
    window.openConfirmModal("검증완료를 취소하시겠습니까? 이미 단말로 보낸 값은 바뀌지 않으며, 다시 검증완료하면 새로 전송합니다.", sendCancelVerify);
  }

  /** 검증완료 취소. 확인값은 그대로 두고 상태만 돌린다. 담당자면 다시 고칠 수 있다. */
  function sendCancelVerify() {
    if (verifying) return;
    var item = activeItem();
    verifying = true;
    updateVerifyButton();
    fetch(toActionUrl(activeImageSrc, "verify/cancel"), { method: "POST" })
        .then(function (res) {
          return res.json().catch(function () { return {}; }).then(function (body) {
            if (!res.ok) throw new Error(body.message || "검증완료를 취소하지 못했습니다.");
            return body;
          });
        })
        .then(function (state) {
          window.resultReview.applyDocState(item, state);
          window.openSuccessModal("검증완료를 취소했습니다.");
        })
        .catch(function (err) {
          console.error(err);
          window.openErrorModal(err.message);
        })
        .finally(function () {
          verifying = false;
          updateVerifyButton();
          updateReview();
        });
  }

  function saveValues() {
    if (saving || verifying || !fieldsReady || lockReason()) return;
    saving = true;
    updateVerifyButton();
    submitValues("save", "확인값을 저장하지 못했습니다.")
        .catch(function (err) {
          console.error(err);
          window.openErrorModal(err.message);
        })
        .finally(function () {
          saving = false;
          updateVerifyButton();
          updateReview();
        });
  }

  function selectItem(item) {
    document.dispatchEvent(new CustomEvent('result-review:select', { detail: { item: item } }));
  }

  function init() {
    document.querySelectorAll(".file-list__items").forEach(function (list) {
      var items = list.querySelectorAll(".result-file-group__file");
      items.forEach(function (item) {
        item.addEventListener("click", function () {
          items.forEach(function (other) { other.classList.remove("is-active"); });
          item.classList.add("is-active");
          selectItem(item);
          showImage(item);
          showFields(item);
        });
      });

      var initial = list.querySelector(".result-file-group__file.is-active");
      if (!initial) {
        initial = list.querySelector('.result-file-group__file[data-lefrm-id]');
        if (initial) initial.classList.add('is-active');
      }
      if (initial) { selectItem(initial); if (viewer()) showImage(initial); showFields(initial); }
    });

    updateReview();
    document.querySelectorAll('[data-result-confirm-group], [data-result-confirm-table-group]').forEach(function (group) {
      group.addEventListener('input', updateReview);
      group.addEventListener('change', updateReview);
      group.addEventListener('focusin', function (event) {
        if (event.target.matches('[data-fild-id], [data-cell-id]')) {
          currentChange = event.target;
          updateChangeNavigation();
        }
      });
    });
    document.querySelectorAll('[data-result-change-action]').forEach(function (button) {
      button.addEventListener('click', function () { moveChange(button.dataset.resultChangeAction === 'previous' ? -1 : 1); });
    });
    function clearRegionSelection() {
      document.querySelectorAll('[data-result-region].is-highlighted').forEach(function (node) { node.classList.remove('is-highlighted'); });
    }
    var clearFocusButton = document.querySelector('[data-viewer-action="clear-focus"]');
    if (clearFocusButton) clearFocusButton.addEventListener('click', clearRegionSelection);
    document.addEventListener('keydown', function (event) {
      var editing = event.target.closest('input, textarea, select, [contenteditable="true"]');
      if (event.key === 'Escape' && viewer()) {
        viewer().clearFocus();
        clearRegionSelection();
      }
      if (editing || event.ctrlKey || event.metaKey || event.altKey || event.isComposing) return;
      if (event.key.toLowerCase() === 'm') { event.preventDefault(); moveChange(event.shiftKey ? -1 : 1); }
    });
    updateVerifyButton();
    // 페이지가 로딩될 때 함수를 연결한다.
    var verifyBtn = document.querySelector("[data-verify-btn]");
    if (verifyBtn) verifyBtn.addEventListener("click", verifyResult);
    var cancelBtn = document.querySelector("[data-verify-cancel-btn]");
    if (cancelBtn) cancelBtn.addEventListener("click", cancelVerify);
    var saveBtn = document.querySelector("[data-result-save]");
    if (saveBtn) saveBtn.addEventListener("click", saveValues);
    // 담당자 지정·검증완료가 바뀌면 선택한 서식의 편집 가능 여부를 다시 정한다.
    document.addEventListener('result-review:change', function (event) {
      if (event.detail && event.detail.item === activeItem()) {
        updateVerifyButton();
        updateReview();
      }
    });
    window.addEventListener('beforeunload', function (event) {
      if (!hasUnsavedChanges()) return;
      event.preventDefault();
      event.returnValue = '';
    });
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", init);
  } else {
    init();
  }
 })();
