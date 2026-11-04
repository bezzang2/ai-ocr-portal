/* 인식/확인 정보 패널의 임시 확대와 사용자가 선택한 개별 접힘을 관리한다. */
(function () {
  "use strict";

  var ICON_MAXIMIZE = "../../images/icons/result/ic-16-maximize.svg";
  var ICON_MINIMIZE = "../../images/icons/result/ic-16-minimize.svg";
  var STORAGE_KEY = "kjbank.resultPanelCollapse.v1";

  function setExpanded(button, expanded) {
    button.setAttribute("aria-expanded", String(expanded));
    var icon = button.querySelector("[data-panel-expand-icon]");
    var label = button.querySelector("[data-panel-expand-label]");
    if (icon) icon.src = expanded ? ICON_MINIMIZE : ICON_MAXIMIZE;
    if (label) label.textContent = expanded ? "축소" : "확대";
  }

  function readCollapsed() {
    try {
      var value = JSON.parse(window.localStorage.getItem(STORAGE_KEY) || "null");
      return {
        recognized: !!(value && value.recognized === true),
        confirm: !!(value && value.confirm === true)
      };
    } catch (error) {
      return { recognized: false, confirm: false };
    }
  }

  function writeCollapsed(state) {
    try {
      window.localStorage.setItem(STORAGE_KEY, JSON.stringify(state));
    } catch (error) {
      // Storage can be unavailable in private browsing; keep the current interaction working.
    }
  }

  function init() {
    document.querySelectorAll("[data-panel-expand]").forEach(function (group) {
      var panels = Array.from(group.querySelectorAll(".result-panel"));
      if (!panels.length) return;
      if (panels.length !== group.querySelectorAll("[data-panel-expand-btn]").length) return;

      var workspace = group.closest("[data-result-workspace]");
      var panelStates = panels.map(function (panel) {
        var body = panel.querySelector(":scope > .result-panel__body");
        var expandButton = panel.querySelector("[data-panel-expand-btn]");
        var collapseButton = panel.querySelector("[data-panel-collapse-btn]");
        var restoreButton = panel.querySelector("[data-panel-restore-btn]");
        var key = body && body.id === "result-recognized-body" ? "recognized"
          : body && body.id === "result-confirm-body" ? "confirm" : null;
        return { panel: panel, body: body, expand: expandButton, collapse: collapseButton, restore: restoreButton, key: key };
      });
      if (!panelStates.length || panelStates.some(function (item) { return !item.expand; })) return;

      var manualEnabled = panelStates.every(function (item) {
        return item.body && item.collapse && item.restore && item.key;
      });

      var preference = manualEnabled ? readCollapsed() : null;
      var collapsed = manualEnabled ? {
        recognized: preference.recognized,
        confirm: preference.confirm
      } : {};

      function resetTemporaryExpansion() {
        panelStates.forEach(function (item) {
          item.panel.classList.remove("is-expanded", "is-collapsed");
          item.panel.inert = false;
          item.panel.removeAttribute("aria-hidden");
          setExpanded(item.expand, false);
        });
      }

      function applyTemporaryExpansion(index) {
        panelStates.forEach(function (other, otherIndex) {
          if (otherIndex === index) {
            other.panel.classList.add("is-expanded");
            setExpanded(other.expand, true);
          } else if (!manualEnabled || !collapsed[other.key]) {
            other.panel.classList.add("is-collapsed");
            other.panel.inert = true;
            other.panel.setAttribute("aria-hidden", "true");
          }
        });
      }

      function wireExpandButtons() {
        panelStates.forEach(function (item, index) {
          setExpanded(item.expand, false);
          item.expand.addEventListener("click", function () {
            if (manualEnabled && collapsed[item.key]) return;
            var wasExpanded = item.panel.classList.contains("is-expanded");
            resetTemporaryExpansion();
            if (!wasExpanded) applyTemporaryExpansion(index);
            if (manualEnabled) {
              panelStates.forEach(syncManualState);
              syncWorkspaceState();
            }
          });
        });
      }

      wireExpandButtons();
      resetTemporaryExpansion();
      if (!manualEnabled) return;

      function syncManualState(item) {
        var folded = collapsed[item.key];
        item.panel.classList.toggle("is-user-collapsed", folded);
        item.body.inert = folded;
        item.body.hidden = folded;
        item.body.style.display = folded ? "none" : "";
        if (folded) item.body.setAttribute("aria-hidden", "true");
        else item.body.removeAttribute("aria-hidden");
        item.collapse.setAttribute("aria-expanded", String(!folded));
        var panelName = item.key === "recognized" ? "인식 정보" : "확인 정보";
        item.collapse.setAttribute("aria-label", panelName + (folded ? " 펼치기" : " 접기"));
        item.collapse.textContent = folded ? "펼치기" : "접기";
        item.restore.setAttribute("aria-expanded", String(!folded));
        item.restore.setAttribute("aria-label", panelName + " 펼치기");
        item.restore.inert = !folded;
      }

      function syncWorkspaceState() {
        var bothFolded = collapsed.recognized && collapsed.confirm;
        if (workspace) workspace.classList.toggle("are-detail-panels-collapsed", bothFolded);
        var splitter = workspace && workspace.querySelector('[data-workspace-resize="panels"]');
        if (splitter) {
          splitter.hidden = bothFolded;
          var enabled = window.matchMedia("(min-width: 1051px)").matches && !bothFolded
            && !workspace.classList.contains("is-panels-collapsed");
          splitter.setAttribute("aria-disabled", String(!enabled));
          splitter.tabIndex = enabled ? 0 : -1;
        }
      }

      function persistAndSync() {
        panelStates.forEach(syncManualState);
        syncWorkspaceState();
        writeCollapsed(collapsed);
      }

      persistAndSync();

      panelStates.forEach(function (item, index) {
        item.collapse.addEventListener("click", function () {
          resetTemporaryExpansion();
          collapsed[item.key] = true;
          persistAndSync();
          item.restore.focus();
        });

        item.restore.addEventListener("click", function () {
          resetTemporaryExpansion();
          collapsed[item.key] = false;
          persistAndSync();
          item.collapse.focus();
        });
      });
    });
  }

  if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", init);
  else init();
})();
