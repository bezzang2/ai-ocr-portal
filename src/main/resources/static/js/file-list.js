/* 서식목록(.file-list__item) 단일 선택 제어.
   클릭한 항목에 is-active를 주고 나머지는 해제한다 — 한 번에 하나만 선택된다. */
(function () {

  function init() {
    document.querySelectorAll(".file-list__items").forEach(function (list) {
      var items = list.querySelectorAll(".file-list__item");
      items.forEach(function (item) {
        item.addEventListener("click", function () {
          items.forEach(function (other) { other.classList.remove("is-active"); });
          item.classList.add("is-active");
        });
      });
    });
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", init);
  } else {
    init();
  }
})();
