/* 서식 판독 화면의 목록/미리보기/정보 패널 크기와 접힘 상태를 관리한다.
   localStorage에는 UI 선호만 저장하며 문서 데이터는 저장하지 않는다. */
(function () {
  "use strict";

  var STORAGE_KEY = "kjbank.resultWorkspace.v1";
  var MIN_RAIL = 140;
  var MAX_RAIL = 280;
  var RAIL_TAB = 36;
  var MIN_VIEWER = 280;
  var MIN_PANELS = 400;
  var DEFAULT_RAIL = 160;
  var DEFAULT_RATIO = 0.58;

  function clamp(value, min, max) {
    return Math.min(Math.max(value, min), max);
  }

  function readPreferences() {
    try {
      var stored = JSON.parse(window.localStorage.getItem(STORAGE_KEY) || "null");
      if (!stored || typeof stored !== "object") return {};
      return {
        railWidth: Number.isFinite(stored.railWidth) ? clamp(stored.railWidth, MIN_RAIL, MAX_RAIL) : DEFAULT_RAIL,
        mediaRatio: Number.isFinite(stored.mediaRatio) ? clamp(stored.mediaRatio, 0.2, 0.8) : DEFAULT_RATIO,
        railCollapsed: stored.railCollapsed === true,
        panelsCollapsed: stored.panelsCollapsed === true
      };
    } catch (error) {
      return {};
    }
  }

  function writePreferences(state) {
    try {
      window.localStorage.setItem(STORAGE_KEY, JSON.stringify({
        railWidth: Math.round(state.railWidth),
        mediaRatio: Number(state.mediaRatio.toFixed(4)),
        railCollapsed: state.railCollapsed,
        panelsCollapsed: state.panelsCollapsed
      }));
    } catch (error) {
      // Private browsing or a full storage quota must not disable the workspace.
    }
  }

  function isEditingTarget(target) {
    return !!(target && (target.isContentEditable || target.closest("input, textarea, select, [contenteditable='true']")));
  }

  function init() {
    document.querySelectorAll("[data-result-workspace]").forEach(function (workspace) {
      if (workspace.dataset.workspaceReady === "true") return;

      var media = workspace.querySelector("[data-workspace-media]");
      var rail = workspace.querySelector("[data-workspace-rail]");
      var panels = workspace.querySelector("[data-workspace-panels]");
      var railSplitter = workspace.querySelector('[data-workspace-resize="rail"]');
      var panelsSplitter = workspace.querySelector('[data-workspace-resize="panels"]');
      var railTab = workspace.querySelector("[data-workspace-rail-tab]");
      var panelsTab = workspace.querySelector("[data-workspace-panels-tab]");
      if (!media || !rail || !panels) return;

      workspace.dataset.workspaceReady = "true";
      var saved = readPreferences();
      var state = {
        railWidth: saved.railWidth || DEFAULT_RAIL,
        mediaRatio: saved.mediaRatio || DEFAULT_RATIO,
        railCollapsed: saved.railCollapsed === true,
        panelsCollapsed: saved.panelsCollapsed === true
      };

      function desktopEnabled() {
        return window.matchMedia("(min-width: 1051px)").matches;
      }

      function panelRatioBounds() {
        var width = workspace.clientWidth;
        if (!width) return { min: 0.2, max: 0.8, available: 1 };
        var railWidth = state.railCollapsed ? RAIL_TAB : state.railWidth;
        var railSplitterWidth = railSplitter && !state.railCollapsed ? railSplitter.offsetWidth : 0;
        var available = Math.max(1, width - (panelsSplitter && !state.panelsCollapsed ? panelsSplitter.offsetWidth : 0));
        var minMedia = railWidth + railSplitterWidth + MIN_VIEWER;
        return {
          min: clamp(minMedia / available, 0.2, 0.8),
          max: clamp((available - MIN_PANELS) / available, 0.2, 0.8),
          available: available,
          minMedia: minMedia
        };
      }

      function syncSplitter(splitter, now, min, max) {
        if (!splitter) return;
        var collapsed = splitter === railSplitter ? state.railCollapsed : state.panelsCollapsed;
        var detailPanelsCollapsed = splitter === panelsSplitter && workspace.classList.contains("are-detail-panels-collapsed");
        splitter.setAttribute("aria-valuemin", String(Math.round(min)));
        splitter.setAttribute("aria-valuemax", String(Math.round(max)));
        splitter.setAttribute("aria-valuenow", String(Math.round(now)));
        var disabled = !desktopEnabled() || collapsed || detailPanelsCollapsed;
        splitter.setAttribute("aria-disabled", String(disabled));
        splitter.tabIndex = disabled ? -1 : 0;
      }

      function syncControls() {
        var railToggle = document.querySelectorAll('[data-workspace-toggle="rail"]');
        var panelsToggle = document.querySelectorAll('[data-workspace-toggle="panels"]');
        railToggle.forEach(function (button) {
          var label = state.railCollapsed ? "서식목록 펼치기" : "서식목록 접기";
          button.setAttribute("aria-expanded", String(!state.railCollapsed));
          button.setAttribute("aria-label", label);
          button.title = label;
          var text = button.querySelector("[data-workspace-toggle-label]");
          if (text) text.textContent = label;
        });
        panelsToggle.forEach(function (button) {
          var label = state.panelsCollapsed ? "정보 펼치기" : "정보 접기";
          button.setAttribute("aria-expanded", String(!state.panelsCollapsed));
          button.setAttribute("aria-label", label);
          button.title = label;
          var text = button.querySelector("[data-workspace-toggle-label]");
          if (text) text.textContent = label;
          else button.textContent = label;
        });
        if (railTab) {
          railTab.setAttribute("aria-expanded", String(!state.railCollapsed));
          railTab.setAttribute("aria-label", "서식목록 펼치기");
        }
        if (panelsTab) {
          panelsTab.setAttribute("aria-expanded", String(!state.panelsCollapsed));
          panelsTab.setAttribute("aria-label", "정보 펼치기");
        }
      }

      function applyState(persist) {
        workspace.classList.toggle("is-rail-collapsed", state.railCollapsed);
        workspace.classList.toggle("is-panels-collapsed", state.panelsCollapsed);
        rail.querySelectorAll(".file-list__header, .file-list__items, .ocr-review__list-note").forEach(function (part) {
          part.inert = state.railCollapsed;
          if (state.railCollapsed) part.setAttribute("aria-hidden", "true");
          else part.removeAttribute("aria-hidden");
        });
        panels.inert = state.panelsCollapsed;
        panels.setAttribute("aria-hidden", String(state.panelsCollapsed));
        if (railTab) railTab.inert = !state.railCollapsed;
        if (panelsTab) panelsTab.inert = !state.panelsCollapsed;

        var railWidth = state.railCollapsed ? RAIL_TAB : state.railWidth;
        workspace.style.setProperty("--review-rail-width", railWidth + "px");
        var desktop = desktopEnabled();
        var bounds = panelRatioBounds();
        if (desktop && !state.panelsCollapsed && bounds.min <= bounds.max) state.mediaRatio = clamp(state.mediaRatio, bounds.min, bounds.max);
        workspace.style.setProperty("--review-media-ratio", String(state.mediaRatio));
        var mediaBasis = bounds.available * state.mediaRatio;
        workspace.style.setProperty("--review-media-min-width", bounds.minMedia + "px");
        workspace.style.setProperty("--review-media-width", mediaBasis + "px");
        var mediaWidth = media.getBoundingClientRect().width;
        syncSplitter(railSplitter, railWidth, RAIL_TAB, MAX_RAIL);
        syncSplitter(panelsSplitter, mediaWidth, bounds.minMedia, Math.max(bounds.minMedia, bounds.available - MIN_PANELS));
        syncControls();
        if (persist) writePreferences(state);
      }

      function setCollapsed(which, collapsed) {
        if (which === "rail") state.railCollapsed = collapsed;
        else state.panelsCollapsed = collapsed;
        applyState(true);
      }

      document.querySelectorAll('[data-workspace-toggle="rail"]').forEach(function (button) {
        button.addEventListener("click", function () { setCollapsed("rail", !state.railCollapsed); });
      });
      document.querySelectorAll('[data-workspace-toggle="panels"]').forEach(function (button) {
        button.addEventListener("click", function () { setCollapsed("panels", !state.panelsCollapsed); });
      });
      if (railTab) railTab.addEventListener("click", function () { setCollapsed("rail", false); });
      if (panelsTab) panelsTab.addEventListener("click", function () { setCollapsed("panels", false); });

      function configureSplitter(splitter, kind) {
        if (!splitter) return;
        splitter.setAttribute("role", "separator");
        splitter.setAttribute("aria-orientation", "vertical");
        splitter.tabIndex = 0;
        var pointerStart = null;

        function setFromPointer(event) {
          if (!pointerStart || !desktopEnabled()) return;
          var delta = event.clientX - pointerStart.x;
          if (kind === "rail") {
            state.railWidth = clamp(pointerStart.value + delta, MIN_RAIL, MAX_RAIL);
          } else {
            var ratioBounds = panelRatioBounds();
            state.mediaRatio = clamp(pointerStart.value + delta / Math.max(1, ratioBounds.available), ratioBounds.min, ratioBounds.max);
          }
          applyState(false);
        }

        splitter.addEventListener("pointerdown", function (event) {
          if (!desktopEnabled() || event.button !== 0) return;
          pointerStart = { x: event.clientX, value: kind === "rail" ? state.railWidth : state.mediaRatio };
          splitter.setPointerCapture(event.pointerId);
          event.preventDefault();
        });
        splitter.addEventListener("pointermove", setFromPointer);
        splitter.addEventListener("pointerup", function () {
          if (!pointerStart) return;
          pointerStart = null;
          writePreferences(state);
        });
        splitter.addEventListener("pointercancel", function () {
          if (!pointerStart) return;
          pointerStart = null;
          writePreferences(state);
        });
        splitter.addEventListener("keydown", function (event) {
          if (!desktopEnabled() || !["ArrowLeft", "ArrowRight", "Home"].includes(event.key)) return;
          event.preventDefault();
          if (kind === "rail") {
            state.railWidth = event.key === "Home" ? DEFAULT_RAIL
              : clamp(state.railWidth + (event.key === "ArrowLeft" ? -1 : 1) * (event.shiftKey ? 64 : 24), MIN_RAIL, MAX_RAIL);
          } else {
            var bounds = panelRatioBounds();
            state.mediaRatio = event.key === "Home" ? DEFAULT_RATIO
              : clamp(state.mediaRatio + (event.key === "ArrowLeft" ? -1 : 1) * (event.shiftKey ? 0.06 : 0.025), bounds.min, bounds.max);
          }
          applyState(true);
        });
      }

      configureSplitter(railSplitter, "rail");
      configureSplitter(panelsSplitter, "panels");

      document.addEventListener("keydown", function (event) {
        if (event.altKey || event.ctrlKey || event.metaKey || isEditingTarget(event.target)) return;
        if (event.key === "[") { event.preventDefault(); setCollapsed("rail", !state.railCollapsed); }
        if (event.key === "]") { event.preventDefault(); setCollapsed("panels", !state.panelsCollapsed); }
      });

      window.addEventListener("resize", function () { applyState(false); });
      if (window.ResizeObserver) {
        var observer = new ResizeObserver(function () { applyState(false); });
        observer.observe(workspace);
      }
      applyState(false);
    });
  }

  if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", init);
  else init();
})();
