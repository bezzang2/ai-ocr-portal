package com.agilesoda.module.admin.controller;


import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.agilesoda.module.admin.service.AuditLogService;
import com.agilesoda.module.admin.vo.*;
import com.agilesoda.module.admin.vo.ExcelDownloadVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/****************************************************
 *   감사로그 화면 호출 서비스를 관리하는 Controller
 *****************************************************/
@Slf4j
@Controller
@RequestMapping("/module/admin")
@RequiredArgsConstructor
@Tag(name = "감사로그", description = "접근 이력 조회·기록 및 통계 다운로드")
public class AuditLogController {


    private final AuditLogService auditLogService;

    /****************************************************
     *   auditLog 감사로그 화면 연결
     *****************************************************/
    @GetMapping("/auditLog")
    public String auditLog(@RequestParam(value = "menuId", defaultValue = "") String menuId,
                           Model model) throws Exception {
        log.debug("[auditLog] 시작");

        return "module/admin/auditLog";

    }

    /****************************************************
     *   auditLog 감사로그 로그리스트 조회
     *****************************************************/
    @Operation(summary = "감사로그 목록 조회")
    @PostMapping("/auditLog/selectLogLsit")
    @ResponseBody
    public Map<String, Object> selectLogLsit(@RequestBody AuditLogInqVO vo) throws Exception{
        log.debug("[selectLogLsit 조회] 시작");

        return auditLogService.selectLogLsit(vo);
    }

    /****************************************************
     *   auditLog 감사로그 메뉴화면 통계 최상위메뉴 조회
     *****************************************************/
    @Operation(summary = "상위 메뉴 목록 조회")
    @PostMapping("/auditLog/selectTopMenuList")
    @ResponseBody
    public List<Map<String, Object>> selectTopMenuList(){
        log.debug("[selectTopMenuList 조회] 시작");

        return auditLogService.selectTopMenuList();
    }

    /****************************************************
     *   auditLog 감사로그 메뉴화면 통계 조회
     *****************************************************/
    @Operation(summary = "메뉴 접근 통계 조회")
    @PostMapping("/auditLog/searchMenuStats")
    @ResponseBody
    public Map<String, Object> searchMenuStats(@RequestBody Map<String, Object> param) throws Exception{
        log.debug("[searchMenuStats 조회] 시작");
        log.info("param = {}",param);
        Map<String, Object> result = new HashMap<>();

        Map<String, Object> summary = auditLogService.selectAuditSummary(param);
        result.put("summary",summary);

        List<Map<String, Object>> menuTop5 = auditLogService.selectMenuTop5(param);
        result.put("menuTop5",menuTop5);

        List<Map<String, Object>> buttonTop5 = auditLogService.selectButtonTop5(param);
        result.put("buttonTop5",buttonTop5);

        Map<String, Object> detail = auditLogService.selectAuditDetail(param);

        result.put("detailList",detail.get("list"));
        result.put("totalPage",detail.get("totalPage"));
        result.put("totalCount",detail.get("totalCount"));
        result.put("pageSize",detail.get("pageSize"));
        result.put("page",detail.get("page"));

        return result;
    }

//    /****************************************************
//     *   auditLog 감사로그 부서사용자 통계 조회
//     *****************************************************/
//    @PostMapping("/auditLog/searchDeptStats")
//    @ResponseBody
//    public Map<String, Object> searchDeptStats(@RequestBody Map<String, Object> param) throws Exception{
//        log.debug("[searchDeptStats 조회] 시작");
//        log.info("param = {}",param);
//        Map<String, Object> result = new HashMap<>();
//        // 총 조회 건수
//        Map<String, Object> deptSummary = auditLogService.selectDeptSummary(param);
//        result.put("deptSummary",deptSummary);
//
//        List<Map<String, Object>> menuTop5 = auditLogService.selectMenuTop5(param);
//        result.put("menuTop5",menuTop5);
//
//        List<Map<String, Object>> buttonTop5 = auditLogService.selectButtonTop5(param);
//        result.put("buttonTop5",buttonTop5);
//
//        Map<String, Object> detail = auditLogService.selectAuditDetail(param);
//
//        result.put("detailList",detail.get("list"));
//        result.put("totalPage",detail.get("totalPage"));
//        result.put("totalCount",detail.get("totalCount"));
//        result.put("pageSize",detail.get("pageSize"));
//        result.put("page",detail.get("page"));
//
//        return result;
//    }

    /****************************************************
     *   auditLog 감사로그 메뉴화면 통계 엑셀다운로드
     *****************************************************/
    @ApiResponse(responseCode = "200", description = "감사로그 엑셀 파일",
            content = @Content(mediaType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", schema = @Schema(type = "string", format = "binary")))
    @Operation(summary = "감사로그 엑셀 다운로드")
    @PostMapping("/auditLog/execelDownLoad")
    @ResponseBody
    public void execelDownLoad(@RequestBody Map<String, Object> param,
                                                 HttpSession session,
                                                 HttpServletResponse response) throws Exception{
        log.info("[관리자] execelDownLoad 엑셀다운로드 시작 userId={}", SsoLoginUserVO.userIdOf(session));
        /* ================================================================
         * 		1. 로그인 사용자
         *  ================================================================ */
        SsoLoginUserVO ssoLoginUser = (SsoLoginUserVO) session.getAttribute("ssoLoginUser");
        String userId = "";
        if(ssoLoginUser != null){
            userId = ssoLoginUser.getUserId();
        }

        /* ================================================================
         * 		2. 요청값
         *  ================================================================ */
        String menuId = (String) param.get("menuIdSub");
        String downloadReason = (String) param.get("downloadReason");
        String excelType = (String) param.get("excelType");

        /* ================================================================
         * 		3. 다운로드 이력저장
         *  ================================================================ */
        ExcelDownloadVO vo = new ExcelDownloadVO();
        vo.setUserId(userId);
        vo.setMenuId(menuId);
        vo.setDownloadReason(downloadReason);
        vo.setFrstRegEmpno(userId);

        auditLogService.insertExcelDownloadHistory(vo);

        /* ================================================================
         * 		4. 액셀 생성
         *  ================================================================ */
        List<Map<String, Object>> list = null;
        // 페이지 없이 전체 조회
        if("dept".equals(excelType)){
            // 부서 사용자 통계
            list = auditLogService.selectAuditDeptExcel(param);
        }else if("menu".equals(excelType)){
            list = auditLogService.selectAuditMenuExcel(param);
        }


        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("감사로그");

        // 헤더
        Row header  = sheet.createRow(0);
        header.createCell(0).setCellValue("No");
        header.createCell(1).setCellValue("메뉴");
        header.createCell(2).setCellValue("화면명");
        header.createCell(3).setCellValue("접근타입");
        header.createCell(4).setCellValue("버튼ID");
        header.createCell(5).setCellValue("최근 접속 일시");

        // 데이터
        int rowNum = 1;
        for(Map<String, Object> item : list){
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(String.valueOf(item.getOrDefault("no","")));
            row.createCell(1).setCellValue(String.valueOf(item.getOrDefault("menuNm","")));
            row.createCell(2).setCellValue(String.valueOf(item.getOrDefault("screenNm","")));
            row.createCell(3).setCellValue(String.valueOf(item.getOrDefault("accessType","")));
            row.createCell(4).setCellValue(String.valueOf(item.getOrDefault("btnId","")));
            row.createCell(5).setCellValue(String.valueOf(item.getOrDefault("lastAccessDt","")));
        }

        // 컬럼 너비
        for(int i=0; i<6; i++){
            sheet.autoSizeColumn(i);
        }
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

        String fileName = URLEncoder.encode("감사로그.xlsx", StandardCharsets.UTF_8).replace("+","%20");
        response.setHeader("Content-Disposition","attachment;filename*UTF-8''"+fileName);
        workbook.write(response.getOutputStream());
        workbook.close();

    }

    /****************************************************
     *   감사로그 저장
     *****************************************************/
    @Operation(summary = "감사로그 기록")
    @PostMapping("/auditLog/log")
    @ResponseBody
    public Map<String, Object> insertAuditLog(@RequestBody Map<String, Object> param,
                                              HttpSession session,
                                              HttpServletRequest request) throws Exception {
        log.info("[관리자] insertAuditLog 시작 userId={}", SsoLoginUserVO.userIdOf(session));
        Map<String, Object> result = new HashMap<>();
        try {
            // 로그인 사용자 정보
            SsoLoginUserVO ssoLoginUser = (SsoLoginUserVO)session.getAttribute("ssoLoginUser");
            if(ssoLoginUser != null){
                param.put("userId",ssoLoginUser.getUserId());
            }
            // 사용자 IP
            param.put("reqIp",getClientIp(request));
            // 요청 url
            //param.put("reqUrl",request.getRequestURI());
            // 로그 저장
            auditLogService.insertAuditLog(param);
            result.put("result","SUCCESS");
        }catch (Exception e){
            log.error("[관리자] insertAuditLog 실패 userId={}", SsoLoginUserVO.userIdOf(session), e);
            result.put("result","FAIL");
        }
        return result;
    }

    // IP가져오는 함수
    private String getClientIp(HttpServletRequest request){
        String ip = request.getHeader("X-Forwarded-For");
        if(ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)){
            ip = request.getHeader("Proxy-Client-IP");
        }
        if(ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)){
            ip = request.getRemoteAddr();
        }
        if(ip != null && ip.contains(",")){
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    /****************************************************
     *   부서정보  조회
     *****************************************************/
    @Operation(summary = "감사로그 부서 검색")
    @PostMapping("/searchDeptList")
    @ResponseBody

    public Map<String, Object> searchDeptList(@RequestBody Map<String, String> param) {
        log.debug("[부서정보 조회] 시작");
        Map<String, Object> result = new HashMap<>();

        String brCd = param.getOrDefault("brCd","");
        String brNm = param.getOrDefault("brNm","");

        try {
            List<CommonDeptListVO> deptList = auditLogService.searchDeptList(brCd,brNm);
            result.put("result","SUCCESS");
            result.put("deptList",deptList);

        }catch(Exception e){
            log.error("[관리자] searchDeptList 실패", e);
            result.put("result","FAIL");
            result.put("message","처리 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.");
        }
        return result;
    }


}

