/* 행 전체가 링크처럼 동작하는 표 — <table data-row-link>에서 data-href가 붙은 <tr>를 클릭하면
   해당 페이지로 이동한다(커뮤니티>공지사항 목록에서 행을 눌러 상세로 진입하는 용도).
   제목 셀 텍스트만 파란 링크로 스타일하는 대신(hyperlink처럼 보여 오해를 줌) 행 전체가
   :hover로만 강조되고(components/table.css의 기존 tbody tr:hover) 클릭 시 이동하는 방식이다.
   체크박스처럼 행 안의 다른 인터랙션 요소를 누른 클릭은 이동시키지 않는다. */
(function () {
  function initRowLinkTables() {
    document.querySelectorAll("table[data-row-link]").forEach(function (table) {
      var tbody = table.tBodies[0];
      if (!tbody) return;

      Array.prototype.forEach.call(tbody.querySelectorAll(":scope > tr[data-href]"), function (row) {
        row.addEventListener("click", function (e) {
          if (e.target.closest("a, button, input, label, select, textarea")) return;
          location.href = row.dataset.href;
        });
      });
    });
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", initRowLinkTables);
  } else {
    initRowLinkTables();
  }
})();
