/* =========================================================================
   OCR업무사용신청 (menu M140) 화면 스크립트
   화면: templates/module/apply/ocr-task-usage-apply.html
   - jQuery, modal.js(openInformationModal 등), contextPath 는 화면/공통에서 먼저 로드됨
   - callbackApprChain 은 공통 결재선 팝업(approval-line-modal)이 부르므로 전역 함수로 둠
   ========================================================================= */
const MAX_REASON_BYTES = 100;
const MSG_NEED_KEYWORD = "업무명을 입력하거나 돋보기로 업무를 선택한 뒤 조회하세요.";
let gSelectedTr = null;     // 상세 모달을 연 행
let gApprLines  = [];       // 결재선 [{seq, empno, empnm, ...}]
let gSaved      = false;    // 저장/조회로 나갈 때 경고 끄기용

$(() => {
    $("#btn_submit").on("click", function(){ fnSearchFirst(); });
    $("#ocr_work_nm").on("keydown", function(e){ if(e.key === "Enter"){ e.preventDefault(); fnSearchFirst(); } });
    $("#btn_reset").on("click", function(){ fnReset(); });
    //  업무명을 직접 고치면 팝업에서 고른 업무ID는 버림 (다시 업무명 검색)
    $("#ocr_work_nm").on("input", function(){ $("#wk_id").val(""); });
    //  돋보기 → 업무 검색 팝업
    $("#btn_task_popup").on("click", function(){ fnOpenTaskPopup(); });
    $("#btn_task_search").on("click", function(){ fnSearchTask(); });
    $("#task_keyword").on("keydown", function(e){ if(e.key === "Enter"){ e.preventDefault(); fnSearchTask(); } });
    //  팝업 목록 : 클릭 = 선택, 더블클릭 = 바로 적용
    $("#task_list").on("click", "tr[data-wk-id]", function(){ fnSelectTaskRow(this); });
    $("#task_list").on("dblclick", "tr[data-wk-id]", function(){ fnSelectTaskRow(this); fnApplyTask(); });
    $("#btn_task_apply").on("click", function(){ fnApplyTask(); });
    $("#btn_save").on("click", function(){ fnSave(); });
    $("#btn_use_apply").on("click", function(){ fnOpenUseApply(); });
    $("#btn_detail_apply").on("click", function(){ fnDetailApply(); });
    $("#btn_apply_save").on("click", function(){ fnApplySave(); });
    $("#btn_appr_line").on("click", function(){
        fnOpenModal("approval-line-modal");
        setTimeout(() => { $("#approval_keyword").val("").focus(); }, 50);
    });

    $("#page-size").on("change", function(){
        $("#page_size").val($(this).val());
        if(fnHasKeyword()) fnSearchFirst();     // 업무명이 없으면 크기만 바꾸고 조회 안 함
    });

    //  표 이벤트 (tbody 하나에만 등록)
    const $body = $("#id_usage_list");
    //  행 클릭 → 선택
    $body.on("click", "tr[data-ocr-code]", function(){ fnSelectRow(this); });
    //  행 더블클릭 → 상세 모달 (입력칸 더블클릭은 글자 선택이라 제외)
    $body.on("dblclick", "tr[data-ocr-code]", function(e){
        if($(e.target).is("input")) return;
        fnOpenDetail(this);
    });
    //  입력할 때마다 → 표시순서 숫자만 + 수정 행 표시
    $body.on("input", "input[data-field]", function(){
        if(this.dataset.field === "dispOrd"){
            const onlyNum = this.value.replace(/[^0-9]/g, "");
            if(onlyNum !== this.value) this.value = onlyNum;
        }
        fnMarkRow(this.closest("tr"));
    });

    //  상세 모달 표시순서 숫자만
    $("#dtl_disp_ord").on("input", function(){
        this.value = this.value.replace(/[^0-9]/g, "");
    });
    //  상세 모달 Enter = 적용
    $("#usage-detail-modal input").on("keydown", function(e){
        if(e.key === "Enter"){ e.preventDefault(); fnDetailApply(); }
    });

    //  신청사유 100byte 제한 (한글 조합 중에는 자르지 않음)
    let composing = false;
    $("#apply_reason")
        .on("compositionstart", function(){ composing = true; })
        .on("compositionend",   function(){ composing = false; fnCheckReason(); })
        .on("input",            function(){ if(!composing) fnCheckReason(); });

    //  저장 안 하고 나갈 때 경고
    window.addEventListener("beforeunload", function(e){
        if(fnHasChanges()){ e.preventDefault(); e.returnValue = ""; }
    });

    movePrev = function(){ movePage(Number($("#page_no").val()) - 1); };
    moveNext = function(){ movePage(Number($("#page_no").val()) + 1); };
    movePage = function(pageNo){
        fnConfirmIfChanged(function(){
            $("#page_no").val(pageNo);
            fnSearch();
        });
    };

    //  처음 열 때는 조회하지 않음 (업무명 입력 후 조회)
    fnClearList(MSG_NEED_KEYWORD);
});

/* ---------------- 공통 ---------------- */
function fnOpenModal(id){
    const overlay = document.getElementById(id);
    if(overlay){ overlay.hidden = false; overlay.style.display = "flex"; }
}
function fnCloseModal(id){
    const overlay = document.getElementById(id);
    if(overlay){ overlay.hidden = true; overlay.style.display = "none"; }
}
function fnEscape(v){
    return v == null ? "" : String(v)
        .replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;")
        .replace(/"/g, "&quot;").replace(/'/g, "&#39;");
}
function fnHasChanges(){
    return !gSaved && $("#id_usage_list tr.is-modified").length > 0;
}
//  수정 중인 내용이 있으면 확인 후 진행
function fnConfirmIfChanged(next){
    if(!fnHasChanges()){ next(); return; }
    openConfirmModal("저장하지 않은 수정 내용이 있습니다. 그래도 조회할까요?", next);
}

/* ---------------- 조회 / 초기화 ---------------- */
//  업무명(또는 팝업에서 고른 업무)이 있는지
function fnHasKeyword(){
    return $("#ocr_work_nm").val().trim() !== "" || $("#wk_id").val() !== "";
}

//  목록 비우고 안내 문구 표시
function fnClearList(message){
    $("#id_usage_list").empty().append(`<tr><td colspan="8">${message}</td></tr>`);
    $("#list_count").empty();
    $(".list-footer .pagination").empty();
    gSelectedTr = null;
    fnRefreshCount();
}

function fnSearchFirst(){
    if(!fnHasKeyword()){
        openInformationModal("업무명을 입력하세요.", () => $("#ocr_work_nm").focus());
        return;
    }
    fnConfirmIfChanged(function(){
        $("#page_no").val(1);
        fnSearch();
    });
}

function fnReset(){
    fnConfirmIfChanged(function(){
        $("#ocr_work_nm").val("");
        $("#wk_id").val("");
        $("#page_no").val(1);
        fnClearList(MSG_NEED_KEYWORD);          // 초기화 후에는 조회하지 않음
    });
}

function fnSearch(){
    if(!fnHasKeyword()){                        // 업무명 없으면 서버 호출 안 함
        fnClearList(MSG_NEED_KEYWORD);
        return;
    }
    let param = {
        ocr_work_nm: $("#ocr_work_nm").val(),
        wk_id: $("#wk_id").val(),
        page_no: $("#page_no").val() == "" ? 1 : Number($("#page_no").val()),
        page_size: $("#page_size").val() == "" ? 15 : Number($("#page_size").val())
    };

    fetch(contextPath + "api/v1/usage-apply/list", {
        method: "POST",
        headers: { "content-Type": "application/json" },
        body: JSON.stringify(param)
    })
    .then(response => response.json())
    .then(res => {
        if(res.status == 200){
            gSaved = false;
            gSelectedTr = null;
            fnDisplayList(res.data.data, res.data.page);
            fnSettingPage(res.data.page);
            fnRefreshCount();
        }
        else{
            openErrorModal(res.message + "[" + res.status + "]");
        }
    })
    .catch(error => {
        console.error("실패", error);
        openErrorModal("오류가 발생했습니다.");
    });
}

function fnDisplayList(list, page){
    const $body = $("#id_usage_list");
    $body.empty();

    if(list.length == 0){
        $body.append(`<tr><td colspan="8">조회된 데이터가 없습니다.</td></tr>`);
        return;
    }

    const startNo = (page.pageNum - 1) * page.pageSize;
    list.forEach(function(item, index){
        //  같은 OCR 서식은 첫 행에만 이름 표시
        const prev    = index === 0 ? null : list[index - 1];
        const showWk  = !prev || prev.wkId !== item.wkId;
        const showDoc = showWk || prev.lefrmId !== item.lefrmId;
        const html = `
            <tr data-wk-id="${fnEscape(item.wkId)}" data-wk-nm="${fnEscape(item.wkNm)}" data-lefrm-id="${fnEscape(item.lefrmId)}"
                data-ocr-doc="${fnEscape(item.ocrDoc)}" data-ocr-code="${fnEscape(item.ocrCode)}" data-read-item="${fnEscape(item.readItem)}">
                <td>${startNo + index + 1}</td>
                <td style="text-align:left">${showWk ? fnEscape(item.wkNm) : ""}</td>
                <td style="text-align:left">${showDoc ? fnEscape(item.ocrDoc) : ""}</td>
                <td>${fnEscape(item.lefrmId)}</td>
                <td style="text-align:left">${fnEscape(item.readItemNm)}</td>
                <td><input type="text" class="input input--sm" data-field="dispNumber" maxlength="20"
                           value="${fnEscape(item.dispNumber)}" data-orig="${fnEscape(item.dispNumber)}"></td>
                <td><input type="text" class="input input--sm" data-field="mappingInfo" maxlength="200"
                           value="${fnEscape(item.mappingInfo)}" data-orig="${fnEscape(item.mappingInfo)}"></td>
                <td style="display:none"><input type="text" class="input input--sm" data-field="dispOrd" maxlength="5" inputmode="numeric"
                           style="text-align:right"
                           value="${fnEscape(item.dispOrd)}" data-orig="${fnEscape(item.dispOrd)}"></td>
            </tr>
        `;
        $body.append(html);
    });
}

function fnSettingPage(page){
    $("#list_count").html(`조회 결과 : <strong>${page.startRowIndex}-${page.endRowIndex}</strong>/${page.totalRecord}`);

    const $nav = $(".list-footer .pagination");
    $nav.empty();

    let html = ``;
    if(page.hasFirstPageNav){
        html += `<button type="button" class="pagination__item" aria-label="처음" onclick="movePage(${page.firstPageNo});">
                    <svg width="9.5" height="8.16667" fill="none" aria-hidden="true"><use href="#svg_move_first" /></svg>
                 </button>`;
    }
    if(page.hasPrevPageNav){
        html += `<button type="button" class="pagination__item" aria-label="이전" onclick="movePrev();">
                    <svg width="5.5" height="9.5" fill="none" aria-hidden="true"><use href="#svg_move_prev" /></svg>
                 </button>`;
    }
    for(let i = page.firstPageNo; i <= page.lastPageNo; i++){
        const current = i === page.pageNum ? " is-current" : "";
        html += `<button type="button" class="pagination__item${current}" onclick="movePage(${i});">${i}</button>`;
    }
    if(page.hasNextPageNav){
        html += `<button type="button" class="pagination__item" aria-label="다음" onclick="moveNext();">
                    <svg width="5.5" height="9.5" fill="none" aria-hidden="true"><use href="#svg_move_next" /></svg>
                 </button>`;
    }
    if(page.hasLastPageNav){
        html += `<button type="button" class="pagination__item" aria-label="끝" onclick="movePage(${page.lastPageNo});">
                    <svg width="9.5" height="8.16667" fill="none" aria-hidden="true"><use href="#svg_move_last" /></svg>
                 </button>`;
    }
    $nav.append(html);
}

/* ---------------- 행 선택 / 수정 표시 ---------------- */
function fnSelectRow(tr){
    $("#id_usage_list tr.is-selected").removeClass("is-selected");
    $(tr).addClass("is-selected");
}

function fnRowValues(tr){
    const v = {};
    $(tr).find("input[data-field]").each(function(){ v[this.dataset.field] = this.value.trim(); });
    return v;
}

function fnMarkRow(tr){
    const changed = $(tr).find("input[data-field]").toArray()
        .some(input => input.value.trim() !== (input.dataset.orig || "").trim());
    $(tr).toggleClass("is-modified", changed);
    fnRefreshCount();
}

function fnRefreshCount(){
    $("#changed_count").text($("#id_usage_list tr.is-modified").length);
}

/* ---------------- 업무 검색 팝업 ---------------- */
function fnOpenTaskPopup(){
    $("#task_keyword").val($("#ocr_work_nm").val());
    fnOpenModal("task-search-modal");
    fnSearchTask();
    setTimeout(() => $("#task_keyword").focus(), 50);
}

function fnSearchTask(){
    const keyword = $("#task_keyword").val().trim();
    fetch(contextPath + "api/v1/usage-apply/task-list?keyword=" + encodeURIComponent(keyword))
    .then(response => response.json())
    .then(res => {
        if(res.status == 200){
            fnDisplayTaskList(res.data);
        }
        else{
            openErrorModal(res.message + "[" + res.status + "]");
        }
    })
    .catch(error => {
        console.error("실패", error);
        openErrorModal("업무 검색 중 오류가 발생했습니다.");
    });
}

function fnDisplayTaskList(list){
    const $body = $("#task_list");
    $body.empty();
    if(!list || list.length === 0){
        $body.append(`<tr><td colspan="4">조회된 업무가 없습니다.</td></tr>`);
        return;
    }
    list.forEach(function(item, index){
        $body.append(`
            <tr data-wk-id="${fnEscape(item.wkId)}" data-wk-nm="${fnEscape(item.wkNm)}">
                <td>${index + 1}</td>
                <td style="text-align:left">${fnEscape(item.wkNm)}</td>
                <td>${fnEscape(item.formCnt)}</td>
                <td>${fnEscape(item.wkStatCd)}</td>
            </tr>
        `);
    });
}

function fnSelectTaskRow(tr){
    $("#task_list tr.is-selected").removeClass("is-selected");
    $(tr).addClass("is-selected");
}

//  선택 → 업무명/업무ID 를 검색조건에 넣고 그 업무로 조회
function fnApplyTask(){
    const tr = $("#task_list tr.is-selected")[0];
    if(!tr){
        openInformationModal("업무를 선택하세요.");
        return;
    }
    fnConfirmIfChanged(function(){
        $("#ocr_work_nm").val(tr.dataset.wkNm);
        $("#wk_id").val(tr.dataset.wkId);
        fnCloseModal("task-search-modal");
        $("#page_no").val(1);
        fnSearch();
    });
}

/* ---------------- 상세 모달 ---------------- */
function fnOpenDetail(tr){
    fnSelectRow(tr);
    gSelectedTr = tr;
    const v = fnRowValues(tr);
    $("#dtl_wk_nm").text(tr.dataset.wkNm);
    $("#dtl_ocr_doc").text(tr.dataset.ocrDoc);
    $("#dtl_ocr_code").text(tr.dataset.lefrmId);
    $("#dtl_read_item").text(tr.dataset.readItem);
    $("#dtl_disp_number").val(v.dispNumber);
    $("#dtl_mapping_info").val(v.mappingInfo);
    $("#dtl_disp_ord").val(v.dispOrd);
    fnOpenModal("usage-detail-modal");
    setTimeout(() => $("#dtl_disp_number").focus(), 50);
}

//  적용 : 목록 행에 값만 넣음 (DB 저장은 상단 [저장] 버튼)
function fnDetailApply(){
    if(!gSelectedTr){ fnCloseModal("usage-detail-modal"); return; }
    const values = {
        dispNumber:  $("#dtl_disp_number").val().trim(),
        mappingInfo: $("#dtl_mapping_info").val().trim(),
        dispOrd:     $("#dtl_disp_ord").val().trim()
    };
    $(gSelectedTr).find("input[data-field]").each(function(){
        this.value = values[this.dataset.field] ?? "";
    });
    fnMarkRow(gSelectedTr);
    fnCloseModal("usage-detail-modal");
}

/* ---------------- 저장 ---------------- */
function fnSave(){
    const rows = $("#id_usage_list tr.is-modified").toArray().map(function(tr){
        return Object.assign({
            wkId:     tr.dataset.wkId,
            lefrmId:  tr.dataset.lefrmId,
            readItem: tr.dataset.readItem
        }, fnRowValues(tr));
    });

    if(rows.length === 0){
        openInformationModal("수정된 데이터가 없습니다.");
        return;
    }

    openConfirmModal(rows.length + "건을 저장하시겠습니까?", function(){
        $("#btn_save").prop("disabled", true);
        fetch(contextPath + "api/v1/usage-apply/save", {
            method: "POST",
            headers: { "content-Type": "application/json" },
            body: JSON.stringify({ rows: rows })
        })
        .then(response => response.json())
        .then(res => {
            if(res.status == 200){
                gSaved = true;
                openSuccessModal(res.message, function(){ fnSearch(); });
            }
            else{
                openErrorModal(res.message);
            }
        })
        .catch(error => {
            console.error("실패", error);
            openErrorModal("저장 중 오류가 발생했습니다.");
        })
        .finally(() => $("#btn_save").prop("disabled", false));
    });
}

/* ---------------- 사용신청 ---------------- */
function fnOpenUseApply(){
    const tr = $("#id_usage_list tr.is-selected")[0];
    if(!tr){
        openInformationModal("사용신청할 행을 먼저 선택하세요.");
        return;
    }
    $("#apply_ocr_doc_text").text(tr.dataset.ocrDoc + " (" + tr.dataset.lefrmId + ")");
    $("#apply_ocr_doc").val(tr.dataset.ocrDoc);
    $("#apply_ocr_code").val(tr.dataset.ocrCode);
    $("#apply_reason").val("");
    fnCheckReason();
    gApprLines = [];
    $("#approval_chain").val("");
    $("#approval_list .appr-tr").remove();      // 결재선 모달에 남은 이전 선택 지우기
    $("#approval_list_empty").show();
    fnOpenModal("usage-apply-modal");
    setTimeout(() => $("#apply_reason").focus(), 50);
}

//  글자별 byte (UTF-8 : 영문·숫자 1, 한글 3) — Oracle VARCHAR2(100 BYTE) 와 맞춤
function fnCharBytes(ch){
    const code = ch.codePointAt(0);
    if(code <= 0x7F)   return 1;
    if(code <= 0x7FF)  return 2;
    if(code <= 0xFFFF) return 3;
    return 4;
}
function fnByteLength(s){
    return [...s].reduce((sum, ch) => sum + fnCharBytes(ch), 0);
}
function fnCutBytes(s, max){
    let sum = 0, out = "";
    for(const ch of s){
        sum += fnCharBytes(ch);
        if(sum > max) break;
        out += ch;
    }
    return out;
}
function fnCheckReason(){
    const el = document.getElementById("apply_reason");
    if(fnByteLength(el.value) > MAX_REASON_BYTES) el.value = fnCutBytes(el.value, MAX_REASON_BYTES);
    $("#apply_reason_bytes").text(fnByteLength(el.value));
}

/**
 * 결재선 팝업(approval-line-modal)에서 확인 클릭 시 호출되는 함수 (이름 고정)
 */
function callbackApprChain(arr){
    gApprLines = arr || [];
    $("#approval_chain").val(gApprLines.map(item => item.empnm + "(결재)").join(" > "));
}

function fnApplySave(){
    const reason = $("#apply_reason").val().trim();
    if(!reason){
        openInformationModal("신청사유를 입력하세요.", () => $("#apply_reason").focus());
        return;
    }
    if(gApprLines.length === 0){
        openInformationModal("결재선을 지정하세요.");
        return;
    }

    openConfirmModal("사용 신청하시겠습니까?", function(){
        $("#btn_apply_save").prop("disabled", true);
        fetch(contextPath + "api/v1/usage-apply/apply", {
            method: "POST",
            headers: { "content-Type": "application/json" },
            body: JSON.stringify({
                ocrDoc:      $("#apply_ocr_doc").val(),
                ocrCode:     $("#apply_ocr_code").val(),
                applyReason: reason,
                //  신청자는 보내지 않음 → 서버가 로그인 세션으로 넣음
                lines: gApprLines.map(item => ({ empno: item.empno, empnm: item.empnm }))
            })
        })
        .then(response => response.json())
        .then(res => {
            if(res.status == 200){
                fnCloseModal("usage-apply-modal");
                const d = res.data;
                openSuccessModal("사용신청이 완료되었습니다. (신청번호 : " + d.applyId + ", 결재선 : " + d.lineText + ")");
            }
            else{
                openErrorModal(res.message);
            }
        })
        .catch(error => {
            console.error("실패", error);
            openErrorModal("사용신청 중 오류가 발생했습니다.");
        })
        .finally(() => $("#btn_apply_save").prop("disabled", false));
    });
}
