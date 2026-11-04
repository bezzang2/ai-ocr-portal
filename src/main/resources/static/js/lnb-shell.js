/* 공통 업무 메뉴의 표시 상태. 문서 목록/정보 패널의 접기 상태와 별도로 유지한다. */
(function () {
  'use strict';
  const storageKey = 'kjbank.navigation.collapsed';
  function init() {
    document.querySelectorAll('[data-navigation-toggle]').forEach(function (button) {
      const shell = button.closest('.app-shell');
      const menu = document.getElementById(button.getAttribute('aria-controls'));
      if (!shell || !menu || button.dataset.navigationReady) return;
      button.dataset.navigationReady = 'true';
      function render(collapsed) {
        shell.classList.toggle('is-navigation-collapsed', collapsed);
        menu.hidden = collapsed;
        button.setAttribute('aria-expanded', String(!collapsed));
        const label = '업무 메뉴 ' + (collapsed ? '펼치기' : '접기');
        button.setAttribute('aria-label', label);
        button.title = label;
        button.querySelector('[data-navigation-toggle-icon]').textContent = collapsed ? '›' : '‹';
        button.querySelector('[data-navigation-toggle-label]').textContent = label;
      }
      let collapsed = false;
      try { collapsed = localStorage.getItem(storageKey) === 'true'; } catch (_) { /* 저장 불가 시에도 메뉴는 동작한다. */ }
      render(collapsed);
      button.addEventListener('click', function () {
        collapsed = !shell.classList.contains('is-navigation-collapsed');
        render(collapsed);
        try { localStorage.setItem(storageKey, String(collapsed)); } catch (_) { /* 표시 상태만 적용한다. */ }
      });
    });
  }
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', init);
  else init();
})();
