/* 판독결과 검토 - "원본 JSON": 선택한 서식의 판독작업을 엔진에서 다시 조회해 응답을 그대로 보여준다.
   응답은 페이지를 닫기 전까지 판독작업별로 메모리에만 둔다(저장하지 않음). */
(function () {
  "use strict";

  var cache = {};
  var current = null; // {transId, docId, data, fetchedAt}
  var scope = "document";

  function modal() { return document.getElementById("raw-json-modal"); }
  function selected() { return document.querySelector(".result-file-group__file.is-active[data-trans-id]"); }

  function rawUrl(item) {
    // data-image-src 는 컨텍스트 경로를 포함한 판독결과 URL 이다.
    var base = item.getAttribute("data-image-src").split("/module/ocr/result/")[0];
    return base + "/module/ocr/result/trans/" + encodeURIComponent(item.getAttribute("data-trans-id")) + "/raw";
  }

  function escapeHtml(text) {
    return text.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
  }

  function highlight(text) {
    return escapeHtml(text).replace(/("(?:[^"\\]|\\.)*")(\s*:)?|\b(true|false|null)\b|(-?\d+(?:\.\d+)?(?:[eE][+-]?\d+)?)/g,
      function (match, str, colon, keyword, num) {
        if (str) return colon ? '<span class="raw-json__key">' + str + "</span>" + colon : '<span class="raw-json__str">' + str + "</span>";
        if (keyword) return '<span class="raw-json__lit">' + keyword + "</span>";
        return '<span class="raw-json__num">' + num + "</span>";
      });
  }

  function documentOf(data, docId) {
    var docs = Array.isArray(data.documents) ? data.documents : [];
    for (var i = 0; i < docs.length; i++) if (docs[i] && docs[i].document_id === docId) return docs[i];
    return null;
  }

  function shownValue() {
    if (scope === "document") return documentOf(current.data, current.docId) || current.data;
    return current.data;
  }

  function shownText() { return JSON.stringify(shownValue(), null, 2); }

  function render() {
    var root = modal();
    var docs = Array.isArray(current.data.documents) ? current.data.documents.length : 0;
    var fields = {
      transaction_id: current.data.transaction_id || current.transId,
      status: current.data.status || "-",
      document_id: current.docId || "-",
      fetched_at: current.fetchedAt
    };
    Object.keys(fields).forEach(function (key) {
      var node = root.querySelector('[data-raw-json-field="' + key + '"]');
      if (node) { node.textContent = fields[key]; node.title = fields[key]; }
    });
    root.querySelector("[data-raw-json-count]").textContent = "(문서 " + docs + "건)";
    root.querySelectorAll("[data-raw-json-scope]").forEach(function (tab) {
      tab.setAttribute("aria-selected", String(tab.getAttribute("data-raw-json-scope") === scope));
    });
    var code = root.querySelector("[data-raw-json-code]");
    code.innerHTML = highlight(shownText());
    code.scrollTop = 0;
  }

  function show() {
    var root = modal();
    root.hidden = false;
    root.style.display = "flex";
    var active = root.querySelector('[aria-selected="true"]');
    if (active) active.focus();
  }

  function timestamp() {
    var d = new Date(), pad = function (n) { return String(n).padStart(2, "0"); };
    return d.getFullYear() + "-" + pad(d.getMonth() + 1) + "-" + pad(d.getDate()) + " " +
      pad(d.getHours()) + ":" + pad(d.getMinutes()) + ":" + pad(d.getSeconds());
  }

  function open(button) {
    var item = selected();
    if (!item) {
      window.openInformationModal("서식목록에서 서식을 먼저 선택해 주세요.");
      return;
    }
    var transId = item.getAttribute("data-trans-id");
    var docId = item.getAttribute("data-doc-id");
    scope = "document";
    if (cache[transId]) {
      current = {transId: transId, docId: docId, data: cache[transId].data, fetchedAt: cache[transId].fetchedAt};
      render();
      show();
      return;
    }
    var label = button.textContent;
    button.disabled = true;
    button.textContent = "불러오는 중…";
    fetch(rawUrl(item), {cache: "no-store"})
      .then(function (res) {
        return res.json().catch(function () { return {}; }).then(function (body) {
          if (!res.ok) throw new Error(body.message || "판독 결과 원본을 가져오지 못했습니다.");
          return body;
        });
      })
      .then(function (data) {
        cache[transId] = {data: data, fetchedAt: timestamp()};
        current = {transId: transId, docId: docId, data: data, fetchedAt: cache[transId].fetchedAt};
        render();
        show();
      })
      .catch(function (err) {
        console.error(err);
        window.openErrorModal(err.message);
      })
      .finally(function () {
        button.disabled = false;
        button.textContent = label;
      });
  }

  function copy(button) {
    var text = shownText();
    var done = function () {
      var label = button.textContent;
      button.textContent = "복사됨";
      setTimeout(function () { button.textContent = label; }, 1200);
    };
    if (navigator.clipboard && window.isSecureContext) {
      navigator.clipboard.writeText(text).then(done, function (err) { console.error(err); window.openErrorModal("복사하지 못했습니다."); });
      return;
    }
    // http 개발서버(보안 컨텍스트 아님)에서는 clipboard API 가 없어 선택 복사로 대신한다.
    var area = document.createElement("textarea");
    area.value = text;
    area.setAttribute("readonly", "");
    area.style.position = "fixed";
    area.style.opacity = "0";
    document.body.appendChild(area);
    area.select();
    var ok = document.execCommand("copy");
    area.remove();
    if (ok) done(); else window.openErrorModal("복사하지 못했습니다.");
  }

  function download() {
    var name = current.transId + (scope === "document" && current.docId ? "-" + current.docId : "") + ".json";
    var url = URL.createObjectURL(new Blob([shownText()], {type: "application/json"}));
    var link = document.createElement("a");
    link.href = url;
    link.download = name;
    document.body.appendChild(link);
    link.click();
    link.remove();
    setTimeout(function () { URL.revokeObjectURL(url); }, 0);
  }

  function init() {
    var button = document.querySelector("[data-raw-json-open]");
    var root = modal();
    if (!button || !root) return;
    button.disabled = !document.querySelector(".result-file-group__file[data-trans-id]");
    button.addEventListener("click", function () { open(button); });
    root.querySelectorAll("[data-raw-json-scope]").forEach(function (tab) {
      tab.addEventListener("click", function () {
        scope = tab.getAttribute("data-raw-json-scope");
        render();
      });
    });
    root.querySelector("[data-raw-json-copy]").addEventListener("click", function (event) { copy(event.currentTarget); });
    root.querySelector("[data-raw-json-download]").addEventListener("click", download);
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", init);
  } else {
    init();
  }
})();
