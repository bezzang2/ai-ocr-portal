/* 문서 뷰어(css/patterns/doc-viewer.css .doc-viewer) 확대/축소 — OCR서식신청 추출화면과
   서식판독결과 화면이 동일하게 쓰는 공용 스크립트다(2026-09-01, 화면별로 따로 있던
   result-viewer-zoom.js를 여기로 통합·일반화했다). .doc-viewer 안에서 이미지 자체가
   커지고 작아진다(오버레이 없음). 확대율은 50~200%를 25%p 단위로 조절하고, 상한/하한에서는
   해당 버튼을 비활성화한다. 버튼뿐 아니라 뷰어 위에서 마우스 휠을 굴려도 같은 단위(25%p)로
   확대/축소되고, 퍼센트 표시(.doc-viewer__zoom-value)도 항상 실제 스케일 값과 그대로 맞춰
   갱신된다. */
(function () {
  var MIN = 50;
  var MAX = 200;
  var STEP = 25;
  var resetFns = [];
  var resetFnByRoot = new (typeof WeakMap !== "undefined" ? WeakMap : Map)();

  function pickVisible(candidates) {
    for (var i = 0; i < candidates.length; i++) {
      if (candidates[i].style.display !== "none") {
        return candidates[i];
      }
    }
    return candidates[0] || null;
  }

  function bind(root) {
    //document.querySelectorAll("[data-zoom]").forEach(function (root) {
    var candidates = Array.prototype.slice.call(root.querySelectorAll("[data-zoom-target]"));
    var valueEl = root.querySelector("[data-zoom-value]");
    var outBtn = root.querySelector("[data-zoom-out]");
    var inBtn = root.querySelector("[data-zoom-in]");
    if (!valueEl) return;
    if (outBtn) {
      var freshOutBtn = outBtn.cloneNode(true);
      outBtn.parentNode.replaceChild(freshOutBtn, outBtn);
      outBtn = freshOutBtn;
    }
    if (inBtn) {
      var freshInBtn = inBtn.cloneNode(true);
      inBtn.parentNode.replaceChild(freshInBtn, inBtn);
      inBtn = freshInBtn;
    }
    var zoom = 100;

    function render() {
      var target = pickVisible(candidates);
      candidates.forEach(function (el) {
        el.style.transform = el === target ? "scale(" + zoom / 100 + ")" : "";
      });
      valueEl.textContent = zoom + "%";
      if (outBtn) outBtn.disabled = zoom <= MIN;
      if (inBtn) inBtn.disabled = zoom >= MAX;
    }

    function setZoom(next) {
      zoom = Math.min(MAX, Math.max(MIN, next));
      render();
    }

    if (outBtn) {
      outBtn.addEventListener("click", function () {
        setZoom(zoom - STEP);
      });
    }
    if (inBtn) {
      inBtn.addEventListener("click", function () {
        setZoom(zoom + STEP);
      });
    }
    render();

    var previousResetFn = resetFnByRoot.get(root);
    if (previousResetFn) {
      var idx = resetFns.indexOf(previousResetFn);
      if (idx !== -1) resetFns.splice(idx, 1);
    }
    var resetFn = function () {
      setZoom(100);
    };
    resetFnByRoot.set(root, resetFn);
    resetFns.push(resetFn);
  }

  function init() {
    document.querySelectorAll("[data-zoom]").forEach(bind);
  }

  window.docViewerZoomReset = function () {
    resetFns.forEach(function (fn) {
      fn();
    });
  };

  // 전체를 다시 스캔한다.
  window.docViewerZoomRebind = function (rootEl) {
    var roots = rootEl ? [rootEl] : Array.prototype.slice.call(document.querySelectorAll("[data-zoom]"));
    roots.forEach(bind);
  };

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", init);
  } else {
    init();
  }
})();
