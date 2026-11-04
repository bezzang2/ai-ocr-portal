(function () {
  document.addEventListener('DOMContentLoaded', function () {
    document.querySelectorAll('.tabs[data-tab-group], .tabs').forEach(function (tabs) {
      var buttons = tabs.querySelectorAll('[data-tab]');
      if (!buttons.length) return;

      var panels = null;
      buttons.forEach(function (btn) {
        btn.addEventListener('click', function () {
          if (!panels) {
            var scope = tabs.parentElement || document;
            panels = scope.querySelectorAll('[data-tab-panel]');
          }
          var target = btn.getAttribute('data-tab');

          buttons.forEach(function (b) { b.classList.toggle('is-active', b === btn); });
          panels.forEach(function (p) {
            p.hidden = p.getAttribute('data-tab-panel') !== target;
          });
        });
      });
    });
  });
})();
