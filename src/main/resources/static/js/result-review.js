/* 판독결과 검토: 판독작업 담당자 표시·지정과 서식 검증완료 표시.
   - 모든 서식은 해당 판독작업 담당자 1명이 맡는다.
   - 관리자·팀장은 누구든 지정·해제하고, 그 밖의 사용자는 비어 있는 판독작업을 "내가 맡기"로만 가져온다.
   - 확인값 저장·검증완료는 담당자만 한다(result-file-list.js 가 editable 로 판단). 권한은 서버가 다시 확인한다.
   - 검증완료 취소는 검증한 사람이나 관리자·팀장에게만 버튼을 보인다(canCancelVerify). 권한은 서버가 다시 확인한다.
   담당자·검증 상태가 바뀌면 'result-review:change' 이벤트를 보낸다. */
(function () {
  'use strict';
  var workspace = document.querySelector('[data-result-workspace]');
  if (!workspace) return;
  var me = workspace.dataset.reviewerId || '';
  var myName = workspace.dataset.reviewerNm || '';
  var canAssign = workspace.dataset.canAssign === 'true';
  var pendingAssignments = new Set();
  var popover = document.querySelector('[data-review-assign]');
  var popoverTarget = null;
  var popoverTrigger = null;
  var picker = null;

  function effective(item) {
    var d = item.dataset;
    if (d.transAsgnUserId) return { id: d.transAsgnUserId, name: d.transAsgnUserNm || d.transAsgnUserId };
    return null;
  }

  function isVerified(item) { return item.dataset.verified === 'true'; }

  function editable(item) {
    var assignee = item && effective(item);
    return !!(item && me && !isVerified(item) && assignee && assignee.id === me);
  }

  /** 검증완료 취소 버튼을 보일지: 검증완료한 서식이고 검증한 사람이거나 관리자·팀장 */
  function canCancelVerify(item) {
    return !!(item && me && isVerified(item) && (canAssign || item.dataset.vrfyUserId === me));
  }

  /** 저장·검증완료를 할 수 없는 이유. 할 수 있으면 빈 문자열 */
  function lockMessage(item) {
    if (!item) return '';
    if (isVerified(item)) return '검증완료한 서식입니다.';
    var assignee = effective(item);
    if (!assignee) return '판독작업 담당자를 먼저 지정해 주세요.';
    if (assignee.id !== me) return assignee.name + ' 님이 담당하는 판독작업입니다.';
    return '';
  }

  function setText(node, text) { if (node) node.textContent = text; }

  function renderItem(item) {
    var badge = item.querySelector('[data-review-badge]');
    var verified = isVerified(item);
    if (badge) {
      badge.textContent = verified ? '검증완료' : '';
      badge.hidden = !verified;
      badge.classList.toggle('is-verified', verified);
      badge.classList.remove('is-mine');
    }
    var base = item.dataset.file || '';
    var detail = verified ? '검증완료 ' + (item.dataset.vrfyUserNm || '') + ' ' + (item.dataset.vrfyDt || '')
        : '';
    item.dataset.reviewTitle = base + (detail ? ' - ' + detail.trim() : '');
    item.title = item.dataset.reviewTitle;
  }

  /** 버튼 이름과 표시: 관리자·팀장은 지정/변경, 그 밖에는 비어 있을 때만 "내가 맡기" */
  function setAssignButton(button, assignee) {
    if (!button) return;
    if (!me) { button.hidden = true; return; }
    if (canAssign) {
      button.hidden = false;
      button.textContent = assignee ? '변경' : '지정';
    } else {
      button.hidden = !!assignee;
      button.textContent = '내가 맡기';
    }
  }

  /** 작업 식별자로 서식을 모은다. BPR 진입에서 다른 판독작업의 상태를 섞지 않는다. */
  function transactionItems(transId) {
    var items = [];
    document.querySelectorAll('.result-file-group').forEach(function (group) {
      if (group.dataset.transId !== transId) return;
      group.querySelectorAll('.result-file-group__file[data-doc-id]').forEach(function (item) {
        items.push(item);
      });
    });
    return items;
  }

  /** 최초 서버 집계에서 화면 밖 문서의 검증 수를 보존하고, 이후 화면에 있는 서식의 변경만 합산한다. */
  function renderProgress(box, refreshProgress) {
    var summary = box.closest('.review-detail-summary');
    if (!summary) return;
    var progress = summary.querySelector('[data-review-progress]');
    if (!progress) return;
    var items = transactionItems(box.dataset.transId).filter(function (item) {
      return !item.hasAttribute('data-result-failed');
    });
    var visibleVerified = items.filter(isVerified).length;
    if (progress.dataset.hiddenVerifiedCount == null) {
      // 서버는 미분류 문서도 집계한다. 최초 렌더에서 차이를 저장해야 검증 응답 뒤에도 그 수가 유지된다.
      var initialVerified = Number(progress.dataset.verifiedCount);
      progress.dataset.hiddenVerifiedCount = Number.isFinite(initialVerified)
          ? Math.max(0, initialVerified - visibleVerified) : 0;
    }
    var badge = summary.querySelector('[data-review-status]');
    if (badge && !badge.dataset.initialStatus) badge.dataset.initialStatus = badge.dataset.status;
    if (!refreshProgress) return;
    var total = Number(progress.dataset.formCount);
    if (!Number.isFinite(total)) total = items.length;
    var verified = visibleVerified + Number(progress.dataset.hiddenVerifiedCount);
    progress.dataset.verifiedCount = verified;
    progress.textContent = verified + ' / ' + total + ' 검증완료';
    if (!badge) return;
    var status = badge.dataset.initialStatus;
    // 실패·처리 중·결과 없음은 엔진 결과이므로 담당자·검증 응답으로 덮어쓰지 않는다.
    if (['FAILED', 'PROCESSING', 'NO_RESULT'].indexOf(status) === -1) {
      status = total > 0 && verified === total ? 'VERIFIED'
          : (box.dataset.transAsgnUserId ? 'IN_REVIEW' : 'UNASSIGNED');
    }
    var labels = {
      FAILED: '실패', PROCESSING: '판독중', NO_RESULT: '결과없음',
      VERIFIED: '검증완료', UNASSIGNED: '미지정', IN_REVIEW: '검토중'
    };
    badge.dataset.status = status;
    badge.textContent = labels[status];
  }

  /** 워크스페이스 밖 요약 영역의 판독작업 담당자와 지정 버튼을 갱신한다. */
  function renderTransaction(box, refreshProgress) {
    var id = box.dataset.transAsgnUserId;
    var name = box.dataset.transAsgnUserNm || id;
    setText(box.querySelector('[data-review-trans-name]'), id ? (id === me ? name + ' (나)' : name) : '없음');
    var button = box.querySelector('[data-review-trans-assign]');
    setAssignButton(button, id ? { id: id } : null);
    if (button) {
      var pending = pendingAssignments.has(box.dataset.transId);
      button.disabled = pending;
      button.setAttribute('aria-busy', String(pending));
    }
    renderProgress(box, refreshProgress);
  }

  function renderAll(changedTransId) {
    document.querySelectorAll('[data-review-trans]').forEach(function (box) {
      renderTransaction(box, !!changedTransId && box.dataset.transId === changedTransId);
    });
    document.querySelectorAll('.result-file-group__file[data-doc-id]').forEach(renderItem);
  }

  function notifyChange(item) {
    document.dispatchEvent(new CustomEvent('result-review:change', { detail: { item: item } }));
  }

  // ---- 서버 응답 반영 ----

  /** 서식 1건의 상태(OcrReviewStateVO)를 화면에 반영한다. */
  function applyDocState(item, state) {
    if (!item || !state) return;
    var d = item.dataset;
    function put(key, value) { if (value == null || value === '') delete d[key]; else d[key] = value; }
    put('verified', state.verified ? 'true' : '');
    put('vrfyUserId', state.vrfyUserId);
    put('vrfyUserNm', state.vrfyUserNm);
    put('vrfyDt', state.vrfyDt);
    put('saveUserNm', state.saveUserNm);
    put('saveDt', state.saveDt);
    var group = item.closest('.result-file-group');
    var transId = group ? group.dataset.transId : d.transId;
    if (transId) {
      // 검증·취소 응답에도 최신 작업 담당자가 담기므로 요약과 다른 서식의 권한을 함께 맞춘다.
      applyTransState(transId, state);
    } else {
      renderItem(item);
      notifyChange(item);
    }
  }

  /** 같은 판독작업의 요약과 모든 서식에 담당자를 반영한다. BPR 진입의 여러 작업은 분리한다. */
  function applyTransState(transId, state) {
    var nodes = [];
    var items = transactionItems(transId);
    document.querySelectorAll('[data-review-trans]').forEach(function (summary) {
      if (summary.dataset.transId === transId) nodes.push(summary);
    });
    nodes = nodes.concat(items);
    nodes.forEach(function (node) {
      if (state.transAsgnUserId) {
        node.dataset.transAsgnUserId = state.transAsgnUserId;
        node.dataset.transAsgnUserNm = state.transAsgnUserNm || state.transAsgnUserId;
      } else {
        delete node.dataset.transAsgnUserId;
        delete node.dataset.transAsgnUserNm;
      }
    });
    // 최초 서버 집계는 유지하고, 실제 변경 응답을 받은 뒤부터 화면의 검증 수를 다시 계산한다.
    renderAll(transId);
    items.forEach(notifyChange);
  }

  /** 중복 지정을 막고 서버가 승인한 담당자를 적용한다. 실패 시 기존 상태를 유지한다. */
  function postAssign(box, userId) {
    var transId = box.dataset.transId;
    if (!me || !transId || !box.dataset.assignUrl || pendingAssignments.has(transId)) return;
    if (!canAssign && (box.dataset.transAsgnUserId || userId !== me)) return;
    pendingAssignments.add(transId);
    renderAll();
    return fetch(box.dataset.assignUrl, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ userId: userId || null })
    }).then(function (res) {
      return res.json().catch(function () { return {}; }).then(function (body) {
        if (!res.ok) throw new Error(body.message || '담당자를 지정하지 못했습니다.');
        return body;
      });
    }).then(function (state) {
      applyTransState(transId, state);
    }).catch(function (error) {
      console.error('[검토] 판독작업 담당자 지정 실패 transId=' + transId, error);
      if (window.openErrorModal) window.openErrorModal(error.message);
      else window.alert(error.message || '담당자를 지정하지 못했습니다.');
    }).finally(function () {
      pendingAssignments.delete(transId);
      renderAll();
    });
  }

  // ---- 지정 팝오버(관리자·팀장) ----

  function closePopover(restoreFocus) {
    if (!popover || popover.hidden) return;
    popover.hidden = true;
    if (picker) picker.reset();
    if (restoreFocus && popoverTrigger) popoverTrigger.focus();
    popoverTarget = null;
    popoverTrigger = null;
  }

  function openPopover(trigger, box) {
    if (!popover) return;
    popoverTarget = box;
    popoverTrigger = trigger;
    var current = effective(box);
    setText(popover.querySelector('[data-review-assign-title]'), '판독작업 담당자 지정');
    setText(popover.querySelector('[data-review-assign-current]'), current
        ? '현재 ' + current.name
        : '현재 담당자가 없습니다.');
    popover.querySelector('[data-review-assign-self]').hidden = !!(current && current.id === me);
    var clear = popover.querySelector('[data-review-assign-clear]');
    clear.hidden = !current;
    clear.textContent = '지정 해제';
    if (!picker && window.StaffPicker) {
      picker = window.StaffPicker.create({
        searchUrl: workspace.dataset.staffSearchUrl,
        label: '담당자 이름 검색',
        onSelect: function (staff) {
          var target = popoverTarget;
          closePopover(true);
          if (target) postAssign(target, staff.id);
        }
      });
      popover.querySelector('[data-review-assign-picker]').appendChild(picker.element);
    }
    popover.hidden = false;
    var rect = trigger.getBoundingClientRect();
    var width = popover.offsetWidth;
    var height = popover.offsetHeight;
    var left = Math.min(Math.max(8, rect.left), window.innerWidth - width - 8);
    var top = rect.bottom + 6 + height > window.innerHeight ? Math.max(8, rect.top - height - 6) : rect.bottom + 6;
    popover.style.left = left + 'px';
    popover.style.top = top + 'px';
    if (picker) picker.focus();
  }

  function assignTarget(trigger, box) {
    if (!me || trigger.disabled) return;
    if (canAssign) { openPopover(trigger, box); return; }
    postAssign(box, me);   // 내가 맡기
  }

  if (popover) {
    popover.querySelector('[data-review-assign-close]').addEventListener('click', function () { closePopover(true); });
    popover.querySelector('[data-review-assign-self]').addEventListener('click', function () {
      var target = popoverTarget; closePopover(true); if (target) postAssign(target, me);
    });
    popover.querySelector('[data-review-assign-clear]').addEventListener('click', function () {
      var target = popoverTarget; closePopover(true); if (target) postAssign(target, null);
    });
    document.addEventListener('keydown', function (event) { if (event.key === 'Escape') closePopover(true); });
    document.addEventListener('mousedown', function (event) {
      if (!popover.hidden && !popover.contains(event.target) && event.target !== popoverTrigger) closePopover(false);
    });
  }

  document.querySelectorAll('[data-review-trans]').forEach(function (box) {
    var button = box.querySelector('[data-review-trans-assign]');
    if (button) button.addEventListener('click', function () {
      assignTarget(button, box);
    });
  });

  window.resultReview = {
    editable: editable,
    lockMessage: lockMessage,
    isVerified: isVerified,
    canCancelVerify: canCancelVerify,
    applyDocState: applyDocState,
    myName: myName
  };
  renderAll();
}());
