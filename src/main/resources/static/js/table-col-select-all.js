/* 헤더 체크박스로 칼럼 전체 선택/해제 — 역할권한 상세 모달의 역할권한 매트릭스(조회/등록/
   수정/삭제, 체크박스 칼럼이 뒤쪽)와 관리자>권한관리 부서사원 표(체크박스 칼럼이 맨 앞)처럼,
   헤더 th 안의 체크박스를 클릭하면 그 칼럼에 해당하는 모든 행의 체크박스가 같은 상태로
   맞춰지는 표를 위한 스크립트다. <table data-col-select-all>가 붙은 표에서만 동작한다
   (opt-in). 체크박스 칼럼이 표의 앞/뒤/중간 어디에 있어도 맞도록, th/td의 colSpan을 누적한
   "실제 칼럼 인덱스"로 헤더 체크박스와 그 아래 각 행의 셀을 매칭한다(단순히 앞/뒤 N개로
   가정하면 "구분"처럼 colspan으로 여러 칼럼을 차지하는 라벨 칼럼이 앞에 있는 표에서만
   맞고, 부서사원처럼 체크박스가 맨 앞인 표에서는 어긋난다). */
(function () {
  function indexByColumn(cells) {
    var map = {};
    var col = 0;
    Array.prototype.forEach.call(cells, function (cell) {
      map[col] = cell;
      col += cell.colSpan || 1;
    });
    return map;
  }

  function initColSelectAllTables() {
    document.querySelectorAll("table[data-col-select-all]").forEach(function (table) {
      var thead = table.tHead;
      var tbody = table.tBodies[0];
      if (!thead || !tbody) return;

      var headerRow = thead.querySelector("tr");
      if (!headerRow) return;
      var headerMap = indexByColumn(headerRow.querySelectorAll(":scope > th"));

      var headerCheckboxCols = Object.keys(headerMap).filter(function (col) {
        return headerMap[col].querySelector(".checkbox__input");
      });
      if (headerCheckboxCols.length === 0) return;

      var rows = Array.prototype.slice.call(tbody.querySelectorAll(":scope > tr"));

      headerCheckboxCols.forEach(function (col) {
        var headerBox = headerMap[col].querySelector(".checkbox__input");
        headerBox.addEventListener("change", function () {
          rows.forEach(function (row) {
            var rowMap = indexByColumn(row.querySelectorAll(":scope > td"));
            var td = rowMap[col];
            if (!td) return;
            var box = td.querySelector(".checkbox__input");
            if (box) box.checked = headerBox.checked;
          });
        });
      });
    });
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", initColSelectAllTables);
  } else {
    initColSelectAllTables();
  }
})();
