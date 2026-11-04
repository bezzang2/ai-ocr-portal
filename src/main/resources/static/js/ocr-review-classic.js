/* 검토 도움말과 수정 초기화. 두 열의 스크롤은 CSS 공통 컨테이너가 담당한다. */
(function () {
  'use strict';
  document.querySelectorAll('[data-review-help], [data-comparison-help]').forEach(function (help) {
    var tooltip = document.getElementById(help.getAttribute('aria-controls'));
    var helpArea = help.parentElement;
    var pinned = false;
    function showHelp(visible) {
      tooltip.hidden = !visible;
      help.setAttribute('aria-expanded', String(visible));
    }
    help.addEventListener('click', function () { pinned = !pinned; showHelp(pinned); });
    helpArea.addEventListener('mouseenter', function () { showHelp(true); });
    helpArea.addEventListener('mouseleave', function () { if (!pinned && !helpArea.contains(document.activeElement)) showHelp(false); });
    help.addEventListener('focus', function () { showHelp(true); });
    helpArea.addEventListener('focusout', function (event) { if (!helpArea.contains(event.relatedTarget)) { pinned = false; showHelp(false); } });
    document.addEventListener('keydown', function (event) { if (event.key === 'Escape') { pinned = false; showHelp(false); } });
    document.addEventListener('click', function (event) { if (!helpArea.contains(event.target)) { pinned = false; showHelp(false); } });
  });
  var reset = document.querySelector('[data-result-reset]');
  if (reset) reset.addEventListener('click', function () {
    document.querySelectorAll('[data-result-confirm-group] [data-original-value], [data-result-confirm-table-group] [data-original-value]').forEach(function (control) {
      control.value = control.dataset.originalValue;
      control.dispatchEvent(new Event('input', {bubbles: true}));
    });
  });
}());
