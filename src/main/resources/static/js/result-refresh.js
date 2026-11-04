/* 판독결과 검토 - 결과를 받지 못한 판독작업의 "결과 다시 가져오기" */
(function () {
  "use strict";

  var STATUS_LABEL = {
    pending: "처리 대기", running: "처리 중", awaiting_hitl: "검토 대기",
    failed: "처리 실패", canceled: "취소"
  };

  function refresh(button) {
    if (button.disabled) return;
    button.disabled = true;
    var label = button.textContent;
    button.textContent = "가져오는 중…";

    fetch(button.getAttribute("data-refresh-url"), { method: "POST", cache: "no-store" })
      .then(function (res) {
        return res.json().catch(function () { return {}; }).then(function (body) {
          if (!res.ok) throw new Error(body.message || "판독 결과를 가져오지 못했습니다.");
          return body;
        });
      })
      .then(function (body) {
        if (body.resultReady) {
          window.openSuccessModal("판독 결과를 가져왔습니다. 확인을 누르면 화면을 새로 고칩니다.", function () { window.location.reload(); });
          return;
        }
        window.openInformationModal("아직 판독 결과가 없습니다. (상태: " + (STATUS_LABEL[body.status] || body.status || "확인 불가") + ")");
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

  document.addEventListener("click", function (event) {
    var button = event.target.closest("[data-result-refresh]");
    if (button) refresh(button);
  });
})();
