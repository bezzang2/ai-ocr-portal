

document.addEventListener("click",function (e){
    const btn = e.target.closest("[data-audit='Y']");
    if(!btn){
        return;
    }
    const menuId = btn.dataset.menuId;
    const btnCd = btn.dataset.btnCd;
    const actionCd = btn.dataset.actionCd;
    const actionNm = btn.dataset.actionNm;

    writeAuditLog(menuId,btnCd,actionCd,actionNm);
});

/* ************************************
* 감사로그를 남기는 함수
* menuId : 메뉴ID
* btnId : 버튼ID
* actionCd : 엑션코드
* actionNm : 엑션명
* *************************************/
function writeAuditLog(menuId, btnCd, actionCd, actionNm) {
    fetch(contextPath+"module/admin/auditLog/log", {
        method: "POST",
        headers: {
            "Content-Type": "application/json"
        },
        body: JSON.stringify({
            menuId:menuId,
            btnCd:btnCd,
            actionCd:actionCd,
            actionNm:actionNm,
            reqUrl:window.location.pathname
        })
    });
}

