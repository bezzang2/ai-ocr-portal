package com.agilesoda.module.admin.controller;


import java.util.HashMap;
import java.util.Map;

import com.agilesoda.common.response.BaseResponse;
import com.agilesoda.common.util.StringUtil;
import com.agilesoda.module.admin.entity.ComCodeEntity;
import com.agilesoda.module.admin.entity.GroupCodeEntity;
import com.agilesoda.module.common.CommonService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import com.agilesoda.common.ComCodeManager;
import com.agilesoda.module.admin.service.CodeManagementService;
import com.agilesoda.module.admin.vo.SsoLoginUserVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/****************************************************
 *   코드관리 화면 호출 서비스를 관리하는 Controller
 *****************************************************/
@Slf4j
@Controller
@RequestMapping("/module/admin")
@RequiredArgsConstructor
@Tag(name = "공통코드 관리", description = "그룹코드와 상세코드 조회·저장·삭제")
public class CodeManagementController {

    private final CodeManagementService codeManagementService;

    private final CommonService commonService;

    /****************************************************
     *   codeManagement 공통코드 화면 연결
     *****************************************************/
    @GetMapping("/codeManagement")
    public String codeManagement(@RequestParam(value = "menuId", defaultValue = "") String menuId,
                                 Model model) throws Exception {
        log.debug("[codeManagement] 시작");
        
       model.addAttribute("groups", ComCodeManager.getInstance().getGroups());

        return "module/admin/codeManagement";

    }

    /****************************************************
     *  그룹코드 등록
     *****************************************************/
    @Operation(summary = "그룹코드와 상세코드 저장")
    @PostMapping("/save-group-code")
    @ResponseBody
    public ResponseEntity<?> saveGroupCode(@RequestBody Map<String, Object> params,
                                        HttpSession session) throws Exception{
        log.info("[관리자] 코드그룹 등록 시작 userId={}", SsoLoginUserVO.userIdOf(session));

        String groupCd = StringUtil.nvl(params.get("groupCd"));
        String groupNm = StringUtil.nvl(params.get("groupNm"));

        if("".equals(groupCd) || "".equals(groupNm)){
            return BaseResponse.error("그룹코드 또는 그룹명이 입력되지 않았습니다.");
        }

        SsoLoginUserVO ssoLoginUserVO = (SsoLoginUserVO) session.getAttribute("ssoLoginUser");
        if(ssoLoginUserVO == null){
            throw new IllegalStateException("로그인 정보가 없습니다.");
        }

        String userId = ssoLoginUserVO.getUserId();

        int cnt = codeManagementService.saveGroupCode(GroupCodeEntity.builder()
                .groupCd(groupCd)
                .groupNm(groupNm)
                .userId(userId)
                .build());

        if(cnt > 0 ){
            commonService.initialComCode();
        }

        return BaseResponse.ok("성공", ComCodeManager.getInstance().getGroups());
    }

    /****************************************************
     *  그룹코드 삭제
     *****************************************************/
    @Operation(summary = "그룹코드 삭제")
    @GetMapping("/deleteGroupCode/{groupCd}")
    @ResponseBody
    public ResponseEntity<?> deleteGroupCode(@PathVariable("groupCd") String groupCd) throws Exception {
        log.debug("[코드그룹 삭제] 시작");

        codeManagementService.deleteGroupCode(groupCd);

        commonService.initialComCode();

        return BaseResponse.ok("성공", ComCodeManager.getInstance().getGroups());
    }

    /****************************************************
     *  코드상세 삭제
     *****************************************************/
    @Operation(summary = "상세코드 삭제")
    @PostMapping("/deleteComCode")
    @ResponseBody
    public ResponseEntity<?> deleteCodeDetail(@RequestBody Map<String, Object> param) throws Exception {
        log.debug("[코드상세 삭제] 시작");

        String groupCd = StringUtil.nvl(param.get("groupCd"));
        String cdVal = StringUtil.nvl(param.get("cdVal"));

        int cnt = codeManagementService.deleteComCode(ComCodeEntity.builder()
                .groupCd(groupCd)
                .cdVal(cdVal)
                .build());

        if(cnt == 0){
            return BaseResponse.ok("성공", "삭제할 데이터가 없습니다.[" + groupCd + "." + cdVal + "]");
        }

        return BaseResponse.ok("성공", "삭제되었습니다[" + groupCd + "." + cdVal + "]");
    }
}

