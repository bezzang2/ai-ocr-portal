/* 표 행 삭제 버튼 — [data-row-delete] 클릭 시 그 버튼이 속한 <tr>을 DOM에서 제거한다.
   일괄변경 상세 모달의 대상자 목록처럼 "이 행을 목록에서 빼기" 용도의 X 버튼에 범용으로
   쓸 수 있게 이벤트 위임으로 구현했다 — 표가 나중에 다시 그려져도 재바인딩이 필요 없다. */
(function () {
  document.addEventListener("click", function (e) {
    var btn = e.target.closest("[data-row-delete]");
    if (!btn) return;
    var row = btn.closest("tr");
    if (row) row.remove();
  });
})();
