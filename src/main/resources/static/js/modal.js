let commonUserCallback = null;
let excelDownloadCallback = null;
let commonDeptCallback = null;
/* 모달 열기/닫기 제어.
   - 트리거: [data-modal-open="모달id"] 클릭 → #모달id(.modal-overlay)의 hidden을 해제.
   - 닫기: 모달 안의 [data-modal-close], 오버레이 배경(모달 박스 바깥) 클릭, Esc 키.
   - 카탈로그(modals.html)의 .modal은 .modal-overlay로 감싸지 않으므로 이 스크립트의
     영향을 받지 않고 항상 정적으로 열린 상태로 보인다. */
(function () {
  var reviewReturnFocus = null;
  function isReviewAlert(overlay) {
    return document.body.classList.contains("ocr-review-page") && !!overlay.querySelector(".alert");
  }

  function openModal(id) {
    var overlay = document.getElementById(id);
    if (overlay){
      overlay.hidden = false;
      overlay.style.display = "flex";
      if (isReviewAlert(overlay)) {
        if (!reviewReturnFocus || !reviewReturnFocus.isConnected) reviewReturnFocus = document.activeElement;
        (overlay.querySelector("#alertNo") || overlay.querySelector(".alert__actions button")).focus();
      }
    }
  }

  function closeModal(overlay) {
    if (!overlay) return;
    overlay.hidden = true;
    overlay.style.display = "none";
    if (isReviewAlert(overlay) && reviewReturnFocus) {
      var trigger = reviewReturnFocus;
      reviewReturnFocus = null;
      if (trigger.isConnected) trigger.focus({preventScroll: true});
    }
  }

  function init() {
    if (document.body.classList.contains("ocr-review-page")) {
      document.querySelectorAll(".modal-overlay .alert").forEach(function (dialog) {
        var overlay = dialog.closest(".modal-overlay");
        var message = dialog.querySelector(".alert__message p");
        message.id = overlay.id + "-message";
        dialog.setAttribute("role", "dialog");
        dialog.setAttribute("aria-modal", "true");
        dialog.setAttribute("aria-labelledby", message.id);
      });
      document.getElementById("alertNo").textContent = "계속 검토";
      document.getElementById("alertYes").textContent = "검증완료";
    }
    document.querySelectorAll("[data-modal-open]").forEach(function (trigger) {
      trigger.addEventListener("click", function () {
        openModal(trigger.getAttribute("data-modal-open"));
      });
    });

    document.querySelectorAll(".modal-overlay").forEach(function (overlay) {
      overlay.addEventListener("click", function (e) {
        //console.log("모달 ID = ",overlay.id);
        //console.log("클릭 대상 = ",e.target);
        // 주변 클릭 시 닫히지 않을 모달
        const noCloseModals = [
             "user-modal",
             "dept-modal",
             "excel-download-modal"
           ];
        // 모달 내부 클릭은 무시
        if (e.target !== overlay) return;
        // 특정 모달은 주변 클릭 무시
        if(noCloseModals.includes(overlay.id)){return;}
        // 나머지 모달 닫기
        closeModal(overlay);
      });
      overlay.querySelectorAll("[data-modal-close]").forEach(function (btn) {
        btn.addEventListener("click", function () { closeModal(overlay); });
      });
    });

    document.addEventListener("keydown", function (e) {
      // 겹쳐 열린 모달은 나중(DOM 뒤쪽)에 있는 것이 위에 보인다. 맨 위 하나만 닫는다.
      var opened = document.querySelectorAll(".modal-overlay:not([hidden])");
      var open = opened[opened.length - 1];
      if (!open) return;
      if (e.key === "Escape") {
        e.preventDefault();
        closeModal(open);
      } else if (e.key === "Tab" && isReviewAlert(open)) {
        var controls = Array.from(open.querySelectorAll("button:not([disabled])"));
        var first = controls[0], last = controls[controls.length - 1];
        if (e.shiftKey && (document.activeElement === first || !open.contains(document.activeElement))) {
          e.preventDefault(); last.focus();
        } else if (!e.shiftKey && (document.activeElement === last || !open.contains(document.activeElement))) {
          e.preventDefault(); first.focus();
        }
      }
    });
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", init);
  } else {
    init();
  }
  function openSuccessModal(message,callback){
    const modal = document.getElementById("success-alert-modal");
    if(!modal){
      return;
    }
    // 메시지 변경
    const messageEl = modal.querySelector(".alert__message p");
    if(messageEl){
      messageEl.textContent = message;
    }
    // 확인 버튼
    const confirmBtn = modal.querySelector("#successConfirmBtn");
    if(confirmBtn){
      confirmBtn.onclick = function () {
        closeModal(modal);
        if(callback){
          callback();
        }
      }
    }
    // 기존 공통 사용
    openModal("success-alert-modal");
  }

  function openInformationModal(message,callback){
    const modal = document.getElementById("information-alert-modal");
    if(!modal){
      return;
    }
    // 메시지 변경
    const messageEl = modal.querySelector(".alert__message p");
    if(messageEl){
      messageEl.textContent = message;
    }
    // 확인 버튼
    const confirmBtn = modal.querySelector("#informationBtn");
    if(confirmBtn){
      confirmBtn.onclick = function () {
        closeModal(modal);
        if(callback){
          callback();
        }
      }
    }
    // 기존 공통 사용
    openModal("information-alert-modal");
  }

  function openConfirmModal(message, yesCallback, noCallback) {
    const modal = document.getElementById("warning-alert-modal");
    if (!modal) return;
    modal.querySelector(".alert__message p").textContent = message;
    // 열 때마다 핸들러를 교체해 이전 확인 동작이 누적되지 않게 한다.
    function settle(callback) {
      modal.querySelector("#alertYes").onclick = null;
      modal.querySelector("#alertNo").onclick = null;
      closeModal(modal);
      if (typeof callback === "function") callback();
    }
    modal.querySelector("#alertYes").onclick = function () { settle(yesCallback); };
    modal.querySelector("#alertNo").onclick = function () { settle(noCallback); };
    openModal("warning-alert-modal");
  }

  function openErrorModal(message,callback){
    const modal = document.getElementById("error-alert-modal");
    if(!modal){
      return;
    }
    // 메시지 변경
    const messageEl = modal.querySelector(".alert__message p");
    if(messageEl){
      messageEl.textContent = message;
    }
    // 확인 버튼
    const confirmBtn = modal.querySelector("#errorBtn");
    if(confirmBtn){
      confirmBtn.onclick = function () {
        closeModal(modal);
        if(callback){
          callback();
        }
      }
    }
    // 기존 공통 사용
    openModal("error-alert-modal");
  }

  /* ************************************
  *  사용자정보 모달 오픈
  * *************************************/
  function openUserSearchModal(callback) {
    console.log("사용자정보 오픈 : ");

    reseUserSearchModal();

    commonUserCallback = callback;

    const modal = document.getElementById("user-modal");
    if(!modal){
      console.error("user-modal 없음");
      return;
    }
    openModal("user-modal");
    //modal.removeAttribute("hidden");
    // 모달 열릴 떄 조회
    //searchCommonUser();

    const empNo = modal.querySelector("#empNo");
    const empKorFlnm = modal.querySelector("#empKorFlnm");
    const enterSearch = function(e){
      if(e.key === "Enter"){
        e.preventDefault();
        searchCommonUser();
      }
    };
    if(empNo){
      empNo.onkeydown = enterSearch;
    }
    if(empKorFlnm){
      empKorFlnm.onkeydown = enterSearch;
    }
  }

  /* ************************************
*  사용자정보 조회
* *************************************/
  function searchCommonUser() {
    //console.log("사용자정보 조회 : ");
    const modal = document.getElementById("user-modal");
    if(!modal){return;}
    const empNo = document.getElementById("empNo")?.value.trim() || "";
    const empKorFlnm = document.getElementById("empKorFlnm")?.value.trim() || "";

    const param = {
      empNo:empNo, empKorFlnm:empKorFlnm
    };
    //console.log("조회 값 : ",param);
    fetch(contextPath+"module/admin/searchUserList", {
      method: "POST",
      headers: {
        "Content-Type": "application/json"
      },
      body: JSON.stringify(param)
    })
        .then(response => {
          if (!response.ok) {
            throw new Error("조회 오류");
          }
          return response.json();
        })
        .then(data => {

          drawCommonUserList(data.userList);
        })
        .catch(error => {
          console.error("사용자 조회 오류 : ", error);
        })
  }

  /* ************************************
*  사용자정보 조회 후 데이터 매핑
* *************************************/
  function drawCommonUserList(list) {
    const tbody = document.getElementById("commonUserBody");
    tbody.innerHTML = "";
    if(!list || list.length === 0){
      tbody.innerHTML = '<tr><td colspan="5">조회된 데이터가 없습니다.</td></tr>';
      return;
    }
    list.forEach(function (user) {
      const tr = document.createElement("tr");
      tr.innerHTML = '<td>'+(user.empKorFlnm || "")+'</td>'+
          '<td>'+(user.empNo || "")+'</td>'+
          '<td>'+(user.brNm || "")+'</td>'+
          '<td>'+(user.hraPstnNm || "")+'</td>'+
          '<td>'+(user.empJobclsNm || "")+'</td>';
      // 모달에서 사용자 더블클릭
      tr.addEventListener("dblclick",function () {
        // 부모 화면으로 전달
        if(typeof commonUserCallback === "function"){
          commonUserCallback(user);
        }
        // 모달 닫기
        const modal = document.getElementById("user-modal");
        closeModal(modal);
      });

      tbody.appendChild(tr);
    })
  }

  /* ************************************
   *  사용자정보 초기화 이벤트
   * *************************************/
  function reseUserSearchModal() {
    const modal = document.getElementById("user-modal");
    if(!modal){return;}
    // 검색조건 초기화
    modal.querySelectorAll('input[type="text"]').forEach(input => {input.value = ""});
    // 조회결과 초기화
    const tbody = document.getElementById("commonUserBody");
    if(tbody){
      tbody.innerHTML = "";
    }
    modal.querySelectorAll(".modal-table-body, .table-scroll").forEach(el => {el.scrollTop = 0;});
  }

  /* ************************************
  *  엑셀다운로드 사유 팝업호출 함수
  * *************************************/
  function openExcelDownloadModal(callback) {
    // 실제 다운로드할 함수를 보관
    //console.log("OPEN 호출");
    excelDownloadCallback = callback;
    const modal = document.getElementById("excel-download-modal");
    const reason = document.getElementById("excelDownloadReason");

    if(!modal){
      console.error("excel-download-modal 없음");
      return;
    }
    // 이전 사유 초기화
    if(reason){
      reason.value = "";
    }

    openModal("excel-download-modal");
  };

  /* ************************************
  *  엑셀다운로드 사유 저장 함수
  * *************************************/
  function confirmExcelDownload() {
    const reasonElement = document.getElementById("excelDownloadReason");
    const reason = String(reasonElement?.value ?? "").trim();

    if(!reason){
      openInformationModal("다운로드 사유를 입력해주세요.", function(){});
      return;
    }
    // 모달 닫기
    const modal = document.getElementById("excel-download-modal");
    closeModal(modal);

    //각 화면의 실제 다운로드 함수 실행
    if(typeof excelDownloadCallback === "function"){
      excelDownloadCallback(reason);
    }
    excelDownloadCallback = null;
  }





  /* ************************************
  *  부서정보 모달 오픈
  * *************************************/
  function openDeptSearchModal(callback) {
    console.log("부서정보 오픈 : ");

    reseDeptSearchModal();

    commonDeptCallback = callback;

    const modal = document.getElementById("dept-modal");
    if(!modal){
      console.error("dept-modal 없음");
      return;
    }
    openModal("dept-modal");
    //modal.removeAttribute("hidden");
    // 모달 열릴 떄 조회
    //searchCommonUser();

    const brCd = modal.querySelector("#brCd");
    const brNm = modal.querySelector("#brNm");
    const enterSearch = function(e){
      if(e.key === "Enter"){
        e.preventDefault();
        searchCommonDept();
      }
    };
    if(brCd){
      brCd.onkeydown = enterSearch;
    }
    if(brNm){
      brNm.onkeydown = enterSearch;
    }
  }

  /* ************************************
  *  부서정보 조회
  * *************************************/
  function searchCommonDept() {
    const modal = document.getElementById("dept-modal");
    if(!modal){return;}
    const brCd = document.getElementById("brCd")?.value.trim() || "";
    const brNm = document.getElementById("brNm")?.value.trim() || "";

    const param = {
      brCd:brCd, brNm:brNm
    };
    //console.log("조회 값 : ",param);
    fetch(contextPath+"module/admin/searchDeptList", {
      method: "POST",
      headers: {
        "Content-Type": "application/json"
      },
      body: JSON.stringify(param)
    })
        .then(response => {
          if (!response.ok) {
            throw new Error("조회 오류");
          }
          return response.json();
        })
        .then(data => {
          drawCommonDeptList(data.deptList);
        })
        .catch(error => {
          console.error("부서정보 조회 오류 : ", error);
        })
  }

  /* ************************************
  *  부서정보 조회 후 데이터 매핑
  * *************************************/
  function drawCommonDeptList(list) {
    const tbody = document.getElementById("commonDeptBody");
    tbody.innerHTML = "";
    if(!list || list.length === 0){
      tbody.innerHTML = '<tr><td colspan="5">조회된 데이터가 없습니다.</td></tr>';
      return;
    }
    list.forEach(function (dept) {
      const tr = document.createElement("tr");
      tr.innerHTML = '<td>'+(dept.brCd || "")+'</td>'+
          '<td>'+(dept.brNm || "")+'</td>'+
          '<td>'+(dept.telNo || "")+'</td>'+
          '<td>'+(dept.faxNo || "")+'</td>'+
          '<td>'+(dept.brAd || "")+'</td>';
      // 모달에서 사용자 더블클릭
      tr.addEventListener("dblclick",function () {
        // 부모 화면으로 전달
        if(typeof commonDeptCallback === "function"){
          commonDeptCallback(dept);
        }
        // 모달 닫기
        const modal = document.getElementById("dept-modal");
        closeModal(modal);
      });

      tbody.appendChild(tr);
    })
  }

  /* ************************************
   *  부서정보 초기화 이벤트
   * *************************************/
  function reseDeptSearchModal() {
    const modal = document.getElementById("dept-modal");
    if(!modal){return;}
    // 검색조건 초기화
    modal.querySelectorAll('input[type="text"]').forEach(input => {input.value = ""});
    // 조회결과 초기화
    const tbody = document.getElementById("commonDeptBody");
    if(tbody){
      tbody.innerHTML = "";
    }
    modal.querySelectorAll(".modal-table-body, .table-scroll").forEach(el => {el.scrollTop = 0;});
  }


// 공통 함수 외부 공개
window.openSuccessModal = openSuccessModal;
window.openConfirmModal = openConfirmModal;
window.openInformationModal = openInformationModal;
window.openErrorModal = openErrorModal;
window.openUserSearchModal = openUserSearchModal;
window.searchCommonUser = searchCommonUser;
window.drawCommonUserList = drawCommonUserList;
window.openExcelDownloadModal = openExcelDownloadModal;
window.confirmExcelDownload = confirmExcelDownload;
window.openDeptSearchModal = openDeptSearchModal;
window.searchCommonDept = searchCommonDept;
window.drawCommonDeptList = drawCommonDeptList;
})();
