/* 메뉴 트리(components/menu-tree.css) 아코디언 제어 — js/lnb.js와 동일한 방식.
   - 1depth(.menu-tree__item) 클릭 시 부모 .menu-tree__group의 is-open을 토글한다.
   - 트리 하나(.menu-tree) 안에서는 한 번에 하나의 그룹만 펼쳐진다.
   - .menu-tree__sub이 없는 그룹(아직 하위메뉴 데이터가 없는 1depth)은 애초에 그룹으로
     감싸지 않으므로 이 스크립트가 관여하지 않는다 — .menu-tree__group만 대상.
   - 페이지 안에 트리가 여러 개(예: 페이지 본문 + 모달)여도 트리별로 독립 동작한다. */
(function () {
  function initMenuTree(tree) {
    var groups = Array.prototype.map.call(
      tree.querySelectorAll(":scope > .menu-tree__body > .menu-tree__group"),
      function (group) {
        return {
          group: group,
          toggle: group.querySelector(":scope > .menu-tree__item"),
        };
      }
    );

    function setOpen(entry, opening) {
      entry.group.classList.toggle("is-open", opening);
    }

    groups.forEach(function (entry) {
      if (!entry.toggle) return;

      entry.toggle.addEventListener("click", function () {
        var opening = !entry.group.classList.contains("is-open");

        if (opening) {
          groups.forEach(function (other) {
            if (other !== entry) setOpen(other, false);
          });
        }

        setOpen(entry, opening);
      });
    });
  }

  function init() {
    document.querySelectorAll(".menu-tree").forEach(initMenuTree);
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", init);
  } else {
    init();
  }
})();
