/* 조회구분/기간선택처럼 "버튼 두 개 중 하나를 고르면 그 아래 입력 필드 구성이 통째로 바뀌는"
   토글 그룹 제어 — js/result-tabs.js와 같은 방식(data-toggle-panel에 hidden 토글)이다.
   패널은 토글 그룹과 같은 부모(.search-area__item) 밑의 형제로 둬야 한다 — 한 화면에
   토글 그룹이 여러 개(조회구분/기간선택) 있어도 서로의 패널을 건드리지 않도록 부모 범위로
   한정한다. */
(function () {
  function init() {
    document.querySelectorAll('[data-toggle-group]').forEach(function (group) {
      var buttons = group.querySelectorAll('[data-toggle]');
      if (!buttons.length) return;

      var scope = group.parentElement || document;
      var panels = scope.querySelectorAll(':scope > [data-toggle-panel]');

      buttons.forEach(function (btn) {
        btn.addEventListener('click', function () {
          var target = btn.getAttribute('data-toggle');

          buttons.forEach(function (b) { b.classList.toggle('is-active', b === btn); });
          panels.forEach(function (p) {
            p.hidden = p.getAttribute('data-toggle-panel') !== target;
          });
        });
      });
    });
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
