/* 판독결과 화면의 문서 역할. 서식에 고를 역할이 있거나 문서에 역할이 있으면 선택칸을 보이고, 저장은 담당자만 된다(서버가 확인). */
(function () {
  var optionsByForm = {};
  var UNDECIDED_HELP = '비교 규칙이 이 역할을 쓰면 검증불가로 표시됩니다.';
  var LOAD_FAILED_HELP = '화면을 새로 열어 다시 시도해 주세요.';

  function activeItem() { return document.querySelector('.result-file-group__file.is-active'); }
  function actionUrl(item, action) { return item.getAttribute('data-image-src').replace(/\/view(?=\?|$)/, '/' + action); }

  function showNote(box, text, help) {
    var note = box.querySelector('[data-result-role-note]');
    note.textContent = text;
    if (help) note.title = help; else note.removeAttribute('title');
  }

  function roleNote(item) {
    if (!item.dataset.docRole) return '역할 미정';
    return item.dataset.docRoleSrc === 'MANUAL' ? '담당자 지정' : '자동 구분';
  }

  /* 실패한 조회는 담아 두지 않아 다음 문서를 고를 때 다시 부른다 */
  function loadOptions(item) {
    var url = actionUrl(item, 'role-options').replace(/\?.*$/, '');
    if (!optionsByForm[url]) {
      optionsByForm[url] = fetch(url, { cache: 'no-store' }).then(function (response) {
        if (!response.ok) throw new Error('role-options status ' + response.status);
        return response.json();
      }).catch(function (error) {
        console.warn('[문서 역할] 목록 조회 실패', url, error);
        delete optionsByForm[url];
        return null;
      });
    }
    return optionsByForm[url];
  }

  /* 지정 뒤 설정에서 지운 역할도 사라져 보이지 않게 지금 값을 보기에 넣는다 */
  function fillSelect(select, roles, current) {
    var names = current && roles.indexOf(current) < 0 ? [current].concat(roles) : roles;
    select.replaceChildren(new Option('자동 구분', ''));
    names.forEach(function (name) { select.appendChild(new Option(name, name)); });
    select.value = current;
  }

  function render() {
    var box = document.querySelector('[data-result-role]');
    var item = activeItem();
    if (!box) return;
    if (!item || !item.getAttribute('data-image-src') || item.hasAttribute('data-result-failed')) { box.hidden = true; return; }
    loadOptions(item).then(function (roles) {
      if (activeItem() !== item) return;
      var select = box.querySelector('[data-result-role-select]');
      var button = box.querySelector('[data-result-role-save]');
      var current = item.dataset.docRole || '';
      var failed = roles === null;
      fillSelect(select, roles || [], current);
      select.disabled = failed;
      button.disabled = failed;
      if (failed) showNote(box, '역할 목록 조회 실패', LOAD_FAILED_HELP);
      else showNote(box, roleNote(item), current ? '' : UNDECIDED_HELP);
      box.hidden = failed ? !(Number(item.dataset.docTotal) > 1 || current) : !(roles.length || current);
    });
  }

  function save() {
    var item = activeItem();
    var box = document.querySelector('[data-result-role]');
    var button = box.querySelector('[data-result-role-save]');
    var role = box.querySelector('[data-result-role-select]').value;
    button.disabled = true;
    fetch(actionUrl(item, 'role'), {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ role: role })
    }).then(function (response) {
      return response.json().catch(function () { return {}; }).then(function (body) {
        if (!response.ok) throw new Error(body.message || '역할을 저장하지 못했습니다.');
      });
    }).then(function () {
      item.dataset.docRole = role;
      item.dataset.docRoleSrc = 'MANUAL';
      if (!role) {
        delete item.dataset.docRole;
        delete item.dataset.docRoleSrc;
      }
      var badge = item.querySelector('[data-doc-role-badge]');
      if (badge) { badge.textContent = role; badge.hidden = !role; }
      render();
      window.openSuccessModal(role ? '역할을 저장하고 다시 판정합니다.' : '역할 지정을 지웠습니다. 다음 판정에서 자동으로 다시 정합니다.');
    }).catch(function (error) {
      render();
      window.openErrorModal(error.message);
    }).finally(function () {
      button.disabled = false;
    });
  }

  document.addEventListener('DOMContentLoaded', function () {
    var list = document.getElementById('result-file-list');
    var button = document.querySelector('[data-result-role-save]');
    if (!list || !button) return;
    list.addEventListener('click', function () { setTimeout(render, 0); });
    button.addEventListener('click', save);
    render();
  });
})();
