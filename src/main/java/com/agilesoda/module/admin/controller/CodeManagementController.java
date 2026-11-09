package com.agilesoda.module.admin.controller;


import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.agilesoda.common.ComCodeManager;
import com.agilesoda.module.admin.service.CodeManagementService;
import com.agilesoda.module.admin.vo.CodeManagementDetailResVO;
import com.agilesoda.module.admin.vo.CodeManagementInqVO;
import com.agilesoda.module.admin.vo.CodeManagementResVO;
import com.agilesoda.module.admin.vo.CodeManagementSaveVO;
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
     *   그룹코드 리스트  조회
     *****************************************************/
    @Operation(summary = "그룹코드 목록 조회")
    @PostMapping("/searchGropCode")
    @ResponseBody
    public Map<String, Object> searchGropCode(@RequestBody CodeManagementInqVO vo) {
        log.debug("[그룹코드 조회] 시작");

        return codeManagementService.searchGropCode(vo);
    }

    /****************************************************
     *  그룹코드로 코드상세 리스트 조회
     *****************************************************/
    @Operation(summary = "상세코드 조회")
    @PostMapping("/selectCodeDetail")
    @ResponseBody
    public Map<String, Object> selectCodeDetail(@RequestBody Map<String, Object> param) {
        log.debug("[코드상세 조회] 시작");

        String cdGroup = String.valueOf(param.get("cdGroup"));

        return codeManagementService.selectCodeDetail(cdGroup);
    }

    /****************************************************
     *  그룹코드 등록
     *****************************************************/
    @Operation(summary = "그룹코드와 상세코드 저장")
    @PostMapping("/saveGroupCode")
    @ResponseBody
    public Map<String, Object> saveGroupCode(@RequestBody CodeManagementSaveVO saveVO,
                                               HttpSession session) {
        log.info("[관리자] 코드그룹 등록 시작 userId={}", SsoLoginUserVO.userIdOf(session));

        Map<String, Object> result = new HashMap<>();
        try {
            SsoLoginUserVO ssoLoginUserVO = (SsoLoginUserVO) session.getAttribute("ssoLoginUser");
            if(ssoLoginUserVO == null){
                throw new IllegalStateException("로그인 정보가 없습니다.");
            }
            String userId = ssoLoginUserVO.getUserId();
            //그룹 등록자 세팅
            saveVO.getGroup().setLastChngEmpno(userId);
            // 상세 등록자 세팅
            for(CodeManagementDetailResVO detail : saveVO.getDetailList()){
                detail.setCdGroup(saveVO.getGroup().getCdGroup());
                detail.setLastChngEmpno(userId);
            }

            codeManagementService.saveGroupCode(saveVO);
            result.put("result","SUCCESS");
        }catch (Exception e){
            log.error("[관리자] saveGroupCode 실패 userId={}", SsoLoginUserVO.userIdOf(session), e);
            result.put("result","FAIL");
            result.put("message","처리 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.");
        }
        return result;
    }

    /****************************************************
     *  그룹코드 삭제
     *****************************************************/
    @Operation(summary = "그룹코드 삭제")
    @PostMapping("/deleteGroupCode")
    @ResponseBody
    public Map<String, Object> deleteGroupCode(@RequestBody CodeManagementResVO vo) {
        log.debug("[코드그룹 삭제] 시작");

        Map<String, Object> result = new HashMap<>();
        try {
            codeManagementService.deleteGroupCode(vo.getCdGroup());
            result.put("result","SUCCESS");
        }catch (Exception e){
            log.error("[관리자] deleteGroupCode 실패", e);
            result.put("result","FAIL");
            result.put("message","처리 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.");
        }
        return result;
    }

    /****************************************************
     *  코드상세 삭제
     *****************************************************/
    @Operation(summary = "상세코드 삭제")
    @PostMapping("/deleteCodeDetail")
    @ResponseBody
    public Map<String, Object> deleteCodeDetail(@RequestBody Map<String, Object> param) {
        log.debug("[코드상세 삭제] 시작");

        Map<String, Object> result = new HashMap<>();
        try {
            int cnt = codeManagementService.deleteCodeDetail(param);
            if(cnt > 0){
                result.put("result","SUCCESS");
            }else{
                result.put("result","FAIL");
                result.put("message","삭제할 데이터가 없습니다.");
            }
        }catch (Exception e){
            log.error("[관리자] deleteCodeDetail 실패", e);
            result.put("result","FAIL");
            result.put("message","처리 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.");
        }
        return result;
    }
}

