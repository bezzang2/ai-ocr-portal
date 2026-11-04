/* LNB 아코디언 제어.
   - 1depth(.nav-item) 클릭 시 부모 .nav-group의 열림/닫힘(is-open)을 토글한다.
   - 한 번에 하나의 그룹만 펼쳐진다 — 다른 그룹을 열면 나머지는 전부 닫힌다.
   - 실제 화면이동은 2depth(.nav-subitem)의 <a href>가 담당한다(JS 없이도 동작).
   - 열림/닫힘에 따른 포커싱(1depth is-active, 2depth is-selected)은 모든 그룹에
     동일하게 적용된다 — 현재 페이지가 속한 그룹도 예외 없이, 접히면 포커스가 풀린다.
   - 페이지 로드 시: 현재 파일과 일치하는 그룹은 그 항목을 포커싱한 채로 기본 펼침
     처리한다. 클릭으로 펼치는 다른 그룹은 첫 번째 2depth 항목이 포커싱된다.
   - href만으로 안 잡히는 하위 흐름 페이지(예: 신청 화면에서 넘어가는 상세/추출 화면)는
     해당 <a>에 data-lnb-alias="파일명.html 파일명2.html"(공백 구분)를 추가하면 그 파일에서도
     같은 2depth 항목이 포커싱된다. */
(function () {
  function initLnbAccordion() {
    var currentFile = location.pathname.split("/").pop() || "index.html";

    var entries = Array.prototype.map.call(
      document.querySelectorAll(".lnb .nav-group"),
      function (group) {
        var toggle = group.querySelector(":scope > .nav-item");
        var subitems = group.querySelectorAll(":scope > .nav-submenu > .nav-subitem");
        var currentSubitem = null;

        subitems.forEach(function (subitem) {
          var href = subitem.getAttribute("href") || "";
          var aliases = (subitem.getAttribute("data-lnb-alias") || "").split(/\s+/).filter(Boolean);
          var files = [href.split("/").pop()].concat(aliases);
          if (files.indexOf(currentFile) !== -1) currentSubitem = subitem;
        });

        return { group: group, toggle: toggle, subitems: subitems, currentSubitem: currentSubitem };
      }
    );

    function setOpen(entry, opening) {
      entry.group.classList.toggle("is-open", opening);
      if (entry.toggle) entry.toggle.classList.toggle("is-active", opening);
      entry.subitems.forEach(function (s) { s.classList.remove("is-selected"); });

      if (opening) {
        var focusTarget = entry.currentSubitem || entry.subitems[0];
        if (focusTarget) focusTarget.classList.add("is-selected");
      }
    }

    entries.forEach(function (entry) {
      if (entry.currentSubitem) setOpen(entry, true);

      if (!entry.toggle) return;

      entry.toggle.addEventListener("click", function () {
        var opening = !entry.group.classList.contains("is-open");

        if (opening) {
          entries.forEach(function (other) {
            if (other !== entry && other.group.classList.contains("is-open")) {
              setOpen(other, false);
            }
          });
        }

        setOpen(entry, opening);
      });
    });
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", initLnbAccordion);
  } else {
    initLnbAccordion();
  }
})();
