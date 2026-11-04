/* 표 헤더의 정렬 방향 토글 버튼(components/table.css의 .table-sort-btn) — 클릭할 때마다
   내림차순/오름차순 아이콘이 전환된다. 정적 프로토타입이라 실제 행 재정렬은 하지 않고
   방향 표시만 토글한다. */
(function () {
  function initSortToggles() {
    document.querySelectorAll("[data-sort-toggle]").forEach(function (btn) {
      btn.addEventListener("click", function () {
        var isAsc = btn.classList.toggle("table-sort-btn--asc");
        btn.setAttribute("aria-label", isAsc ? "오름차순 — 클릭 시 내림차순으로 정렬" : "내림차순 — 클릭 시 오름차순으로 정렬");
      });
    });
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", initSortToggles);
  } else {
    initSortToggles();
  }
})();
