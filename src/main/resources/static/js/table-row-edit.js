/* 표 안 행 단위 인터랙션(편집 전환 / 선택) 공통 스크립트.
   둘 다 "행을 클릭(또는 더블클릭)하면 그 행에만 상태 클래스가 붙고, 같은 표 안 다른
   행에서는 빠진다"는 동일한 뼈대라 파일을 나누지 않고 하나로 합쳤다(2026-08-20,
   원래 별도였던 table-row-select.js를 이 파일에 흡수).

   1) 편집 전환(.is-editing) — 인풋형 표(components/table.css의 .cell-text/.cell-input)에서
      행을 클릭(기본) 또는 더블클릭(<table data-edit-trigger="dblclick">)하면 그 행만 인풋을
      보여준다. 표 바깥을 클릭하면 편집 중이던 행을 닫는다.
   2) 행 선택(.is-selected) — <table data-row-select>가 붙은 표에서 행을 클릭하면 그 행만
      선택 상태가 된다(마스터-디테일 목록의 "선택된 행" 표시용, 관리자>코드관리 코드그룹
      표처럼 옆 패널과 연동하는 용도). 편집과 달리 표 바깥 클릭으로 닫히지 않는다(항상
      하나는 선택돼 있어야 하는 화면이라).

   같은 <table>에 두 속성을 동시에 줄 수 있다(관리자>코드관리 코드그룹 표 — 클릭=선택,
   더블클릭=편집). 서로 다른 이벤트만 듣기 때문에 두 동작이 충돌하지 않는다. */
(function () {
  function initEditableTables() {
    document.querySelectorAll(".table tbody").forEach(function (tbody) {
      var rows = Array.prototype.filter.call(
        tbody.querySelectorAll(":scope > tr"),
        function (row) { return row.querySelector(".cell-input"); }
      );
      if (rows.length === 0) return; // 인풋형 표가 아니면 손대지 않는다

      var table = tbody.closest("table");
      var trigger = (table && table.dataset.editTrigger) || "click";

      rows.forEach(function (row) {
        row.addEventListener(trigger, function () {
          if (row.classList.contains("is-editing")) return;
          rows.forEach(function (r) { r.classList.remove("is-editing"); });
          row.classList.add("is-editing");
        });
      });

      document.addEventListener("click", function (e) {
        if (tbody.contains(e.target)) return;
        rows.forEach(function (r) { r.classList.remove("is-editing"); });
      });
    });
  }

  function initSelectableTables() {
    document.querySelectorAll("table[data-row-select]").forEach(function (table) {
      var tbody = table.tBodies[0];
      if (!tbody) return;
      var rows = Array.prototype.slice.call(tbody.querySelectorAll(":scope > tr"));

      rows.forEach(function (row) {
        row.addEventListener("click", function () {
          if (row.classList.contains("is-selected")) return;
          rows.forEach(function (r) { r.classList.remove("is-selected"); });
          row.classList.add("is-selected");
        });
      });
    });
  }

  function init() {
    initEditableTables();
    initSelectableTables();
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", init);
  } else {
    init();
  }
})();
