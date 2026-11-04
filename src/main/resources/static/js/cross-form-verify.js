/* 통합검색 > 서식간 검증 결과 상세 시안.
   - 좌측 Rule을 누르면 비교표, 서식 A/B 뷰어 제목·하이라이트 박스를 그 Rule 기준으로 바꾼다.
   - 비교표의 판독값을 고치면 비교유형(EQUALS/CONTAINS)대로 바로 다시 판정하고, Rule 카드·요약 건수도 갱신한다.
   정적 시안이라 데이터는 각 .cfv-rule의 data-* 속성에서 읽고, 저장은 화면 상태만 확정한다. */
(function () {
  var PILL = {
    ok: '<span class="status-pill status-pill--success">일치</span>',
    ng: '<span class="status-pill status-pill--error-fill">불일치</span>',
    na: '<span class="status-pill status-pill--neutral-fill">검증불가</span>'
  };
  var rules = Array.prototype.slice.call(document.querySelectorAll(".cfv-rule"));
  var current = null;

  function $(sel) { return document.querySelector(sel); }
  function text(el, value) { if (el) el.textContent = value; }
  function norm(v) { return (v || "").replace(/\s+/g, ""); }

  rules.forEach(function (r) {
    r.dataset.aOrig = r.dataset.aValue;
    r.dataset.bOrig = r.dataset.bValue;
    r.dataset.initialReason = r.dataset.reason;
  });

  function judge(d) {
    var a = norm(d.aValue), b = norm(d.bValue);
    if (!a || !b) return "na";
    if (d.type.indexOf("CONTAINS") !== -1) return b.indexOf(a) !== -1 ? "ok" : "ng";
    return a === b ? "ok" : "ng";
  }

  function edited(d) { return d.aValue !== d.aOrig || d.bValue !== d.bOrig; }

  function reason(d) {
    if (!edited(d)) return d.initialReason;
    if (d.result === "ok") return d.type.indexOf("CONTAINS") !== -1 ? "서식 B 값에 서식 A 값이 포함됩니다." : "두 서식의 값이 같습니다.";
    if (d.result === "ng") return "두 서식의 값이 다릅니다. 원본 이미지를 다시 확인하세요.";
    return "값이 비어 있어 비교할 수 없습니다. 원본을 보고 판독값을 입력하세요.";
  }

  function paintRule(rule) {
    var d = rule.dataset;
    rule.querySelector(".cfv-rule__head .status-pill").outerHTML = PILL[d.result];
    var name = rule.querySelector(".cfv-rule__name");
    var mark = name.querySelector(".cfv-rule__edited");
    if (edited(d) && !mark) name.insertAdjacentHTML("beforeend", '<span class="cfv-rule__edited">수정</span>');
    if (!edited(d) && mark) mark.remove();
  }

  function paintStats() {
    var count = { ok: 0, ng: 0, na: 0 }, label = { ok: "일치", ng: "불일치", na: "검증불가" };
    rules.forEach(function (r) { count[r.dataset.result]++; });
    Object.keys(count).forEach(function (k) { text($('[data-cfv-stat="' + k + '"]'), label[k] + " " + count[k]); });
    var dirty = rules.filter(function (r) { return edited(r.dataset); }).length;
    var dirtyEl = $("[data-cfv-dirty]");
    dirtyEl.hidden = dirty === 0;
    text(dirtyEl, "수정 " + dirty + "건 미저장");
    $("[data-cfv-save]").disabled = dirty === 0;
    $("[data-cfv-reset]").disabled = !current || !edited(current.dataset);
  }

  function placeBox(side, d) {
    var box = $('[data-cfv-box="' + side + '"]');
    var xywh = (d[side + "Box"] || "0,0,0,0").split(",");
    box.style.left = xywh[0] + "%";
    box.style.top = xywh[1] + "%";
    box.style.width = xywh[2] + "%";
    box.style.height = xywh[3] + "%";
    box.dataset.result = d.result;
    box.innerHTML = '<span class="cfv-box__label">' + d[side + "Item"] + "</span>";
  }

  function paintDetail() {
    var d = current.dataset;
    text($("[data-cfv-title]"), current.querySelector(".cfv-rule__name").childNodes[0].textContent);
    ["a", "b"].forEach(function (side) {
      var input = $('[data-cfv-edit="' + side + '"]');
      if (document.activeElement !== input) input.value = d[side + "Value"];
      input.placeholder = d[side + "Orig"] ? "" : "인식 실패 — 원본을 보고 입력";
      var cell = input.parentNode;
      var changed = d[side + "Value"] !== d[side + "Orig"];
      cell.classList.toggle("is-mismatch", d.result === "ng");
      cell.classList.toggle("is-edited", changed);
      text($('[data-cfv-orig="' + side + '"]'), changed ? "원판독값: " + (d[side + "Orig"] || "(인식 실패)") : "");
      placeBox(side, d);
    });
    text($("[data-cfv-a-item]"), d.aItem);
    text($("[data-cfv-b-item]"), d.bItem);
    $("[data-cfv-result]").innerHTML = PILL[d.result];
    text($("[data-cfv-type]"), d.type);
    text($("[data-cfv-reason]"), reason(d));
    text($("[data-cfv-a-form]"), d.aForm);
    text($("[data-cfv-b-form]"), d.bForm);
  }

  function select(rule) {
    current = rule;
    rules.forEach(function (r) { r.classList.toggle("is-active", r === rule); r.setAttribute("aria-pressed", String(r === rule)); });
    paintDetail();
    paintStats();
  }

  rules.forEach(function (rule) {
    rule.addEventListener("click", function () { select(rule); });
  });

  ["a", "b"].forEach(function (side) {
    var input = $('[data-cfv-edit="' + side + '"]');
    var box = $('[data-cfv-box="' + side + '"]');
    input.addEventListener("input", function () {
      current.dataset[side + "Value"] = input.value;
      current.dataset.result = judge(current.dataset);
      paintRule(current);
      paintDetail();
      paintStats();
    });
    input.addEventListener("focus", function () { box.classList.add("is-focused"); });
    input.addEventListener("blur", function () { box.classList.remove("is-focused"); });
  });

  $("[data-cfv-reset]").addEventListener("click", function () {
    var d = current.dataset;
    d.aValue = d.aOrig;
    d.bValue = d.bOrig;
    d.result = judge(d);
    paintRule(current);
    paintDetail();
    paintStats();
  });

  $("[data-cfv-save]").addEventListener("click", function () {
    rules.forEach(function (r) {
      if (!edited(r.dataset)) return;
      r.dataset.initialReason = reason(r.dataset);
      r.dataset.aOrig = r.dataset.aValue;
      r.dataset.bOrig = r.dataset.bValue;
      paintRule(r);
    });
    paintDetail();
    paintStats();
    text($("[data-cfv-reason]"), "저장했습니다(시안: 화면 상태만 바뀝니다). " + reason(current.dataset));
  });

  var first = $(".cfv-rule.is-active") || rules[0];
  if (first) select(first);
})();
