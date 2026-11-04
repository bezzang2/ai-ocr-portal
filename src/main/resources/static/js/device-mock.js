/* 단말 mock 화면(templates/module/devicemock/device-mock.html).
   ① 업무 목록 → ② 이미지 → ③ 요청 정보 → [전송] 을 DeviceMockController 로 보내고, ④ 수신함을 보여 준다. */
(function () {
  'use strict';
  var body = document.body;
  var urls = {
    workflows: body.dataset.workflowsUrl,
    send: body.dataset.sendUrl,
    inbox: body.dataset.inboxUrl,
    result: body.dataset.resultUrl
  };
  var form = document.querySelector('[data-send-form]');
  var workflowList = document.querySelector('[data-workflow-list]');
  var fileInput = document.querySelector('[data-file-input]');
  var fileList = document.querySelector('[data-file-list]');
  var sendButton = document.querySelector('[data-send-button]');
  var sendStatus = document.querySelector('[data-send-status]');
  var sendResult = document.querySelector('[data-send-result]');
  var inboxList = document.querySelector('[data-inbox-list]');

  function el(tag, text, className) {
    var node = document.createElement(tag);
    if (text != null) node.textContent = text;
    if (className) node.className = className;
    return node;
  }

  function readJson(response) {
    return response.json().catch(function () { return {}; }).then(function (data) {
      return { ok: response.ok, data: data };
    });
  }

  function setStatus(text, isError) {
    sendStatus.textContent = text;
    sendStatus.classList.toggle('device-mock__status--error', !!isError);
  }

  function loadWorkflows() {
    workflowList.replaceChildren(el('span', '불러오는 중…', 'device-mock__muted'));
    fetch(urls.workflows).then(readJson).then(function (res) {
      if (!res.ok) throw new Error(res.data.message || '업무 목록을 가져오지 못했습니다.');
      if (!res.data.length) {
        workflowList.replaceChildren(el('span', '활성 업무가 없습니다.', 'device-mock__muted'));
        return;
      }
      workflowList.replaceChildren.apply(workflowList, res.data.map(function (wf) {
        var label = el('label');
        var box = el('input');
        box.type = 'checkbox';
        box.name = 'wkflow_id';
        box.value = wf.workflowId;
        label.append(box, wf.name || wf.workflowId);
        label.title = wf.workflowId;
        return label;
      }));
    }).catch(function (e) {
      workflowList.replaceChildren(el('span', e.message, 'device-mock__status--error'));
    });
  }

  function renderSendResult(data) {
    var trans = (data.data && data.data.TRANS_LIST) || [];
    var nodes = [el('div', '요청GUID ' + data.reqGuid + ' · 판독작업 ' + trans.length + '건')];
    trans.forEach(function (id) {
      var link = el('a', id);
      link.href = urls.result + '?transId=' + encodeURIComponent(id);
      link.target = '_blank';
      var row = el('div', '판독결과 보기: ');
      row.append(link);
      nodes.push(row);
    });
    nodes.push(el('pre', JSON.stringify(data, null, 2)));
    sendResult.replaceChildren.apply(sendResult, nodes);
    sendResult.hidden = false;
  }

  function send(event) {
    event.preventDefault();
    if (!form.querySelector('input[name="wkflow_id"]:checked')) { setStatus('업무를 하나 이상 선택해 주세요.', true); return; }
    if (!fileInput.files.length) { setStatus('이미지를 하나 이상 선택해 주세요.', true); return; }
    sendButton.disabled = true;
    sendResult.hidden = true;
    setStatus('전송 중…');
    fetch(urls.send, { method: 'POST', body: new FormData(form) }).then(readJson).then(function (res) {
      if (!res.ok) {
        setStatus(res.data.message || '판독요청을 보내지 못했습니다.', true);
        if (res.data.reqGuid) renderSendResult(res.data);
        return;
      }
      setStatus('접수되었습니다. 판독이 끝나면 판독결과 화면에서 확인하세요.');
      renderSendResult(res.data);
    }).catch(function () {
      setStatus('판독요청을 보내지 못했습니다.', true);
    }).finally(function () {
      sendButton.disabled = false;
    });
  }

  function loadInbox() {
    fetch(urls.inbox).then(readJson).then(function (res) {
      if (!res.ok || !res.data.length) {
        inboxList.replaceChildren(el('span', res.ok ? '받은 결과가 없습니다.' : '수신함을 불러오지 못했습니다.', 'device-mock__muted'));
        return;
      }
      inboxList.replaceChildren.apply(inboxList, res.data.map(function (item) {
        var b = item.body || {};
        var row = el('div', null, 'device-mock__inbox-item');
        row.append(el('div', item.receivedAt + ' · 업무 ' + b.wkId + ' · 판독작업 ' + b.transId
          + ' · 서식 ' + b.lefrmId + ' · 검증자 ' + b.verifierId));
        row.append(el('pre', JSON.stringify(b, null, 2)));
        return row;
      }));
    }).catch(function () {
      inboxList.replaceChildren(el('span', '수신함을 불러오지 못했습니다.', 'device-mock__status--error'));
    });
  }

  fileInput.addEventListener('change', function () {
    fileList.replaceChildren.apply(fileList, Array.prototype.map.call(fileInput.files, function (f) {
      return el('li', f.name + ' (' + Math.ceil(f.size / 1024) + ' KB)');
    }));
  });
  form.addEventListener('submit', send);
  document.querySelector('[data-workflow-reload]').addEventListener('click', loadWorkflows);
  document.querySelector('[data-inbox-reload]').addEventListener('click', loadInbox);

  loadWorkflows();
  loadInbox();
  // 다른 탭에서 검증완료하면 수신함에 들어오므로 주기적으로 다시 읽는다.
  setInterval(loadInbox, 10000);
})();
