package com.agilesoda.module.admin.controller;


import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.agilesoda.module.admin.service.PermissionManagementService;
import com.agilesoda.module.admin.vo.CommonUserListVO;
import com.agilesoda.module.admin.vo.PermissionManagementInqVO;
import com.agilesoda.module.admin.vo.PermissionManagementResVO;
import com.agilesoda.module.admin.vo.PermissionManagementUserResVO;
import com.agilesoda.module.admin.vo.PermissionUserBtnResVO;
import com.agilesoda.module.admin.vo.PermissionUserMenuListVO;
import com.agilesoda.module.admin.vo.PermissionUserMenuVO;
import com.agilesoda.module.admin.vo.SsoLoginUserVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/****************************************************
 *   권한관리 화면 호출 서비스를 관리하는 Controller
 *****************************************************/
@Slf4j
@Controller
@RequestMapping("/module/admin")
@RequiredArgsConstructor
@Tag(name = "권한 관리", description = "역할·사용자·메뉴·버튼 권한 관리")
public class PermissionManagementController {


    private final PermissionManagementService permissionManagementService;

    /****************************************************
     *   permissionManagement 권한관리 화면 연결
     *****************************************************/
    @GetMapping("/permissionManagement")
    public String permissionManagement(@RequestParam(value = "menuId", defaultValue = "") String menuId,
                                       Model model) throws Exception {
        log.debug("[permissionManagement] 시작");

        return "module/admin/permissionManagement";

    }

    /****************************************************
     *   역활권한 리스트  조회
     *****************************************************/
    @Operation(summary = "역할별 권한 조회")
    @PostMapping("/searchRoleAuth")
    @ResponseBody
    public Map<String, Object> searchRoleAuth(@RequestBody PermissionManagementInqVO vo) {
        log.debug("[역활권한 조회] 시작");

        return permissionManagementService.searchRoleAuth(vo);
    }

    /****************************************************
     *   사용자권한 리스트  조회
     *****************************************************/
    @Operation(summary = "사용자 권한 조회")
    @PostMapping("/selectUserAuth")
    @ResponseBody
    public Map<String, Object> selectUserAuth(@RequestBody Map<String, Object> param) {
        log.debug("[사용자권한 조회] 시작");

        String roleId = String.valueOf(param.get("roleId"));

        return permissionManagementService.selectUserAuth(roleId);
    }

    /****************************************************
     *  역활권한 저장
     *****************************************************/
    @Operation(summary = "역할 권한 저장")
    @PostMapping("/saveRole")
    @ResponseBody
    public Map<String, Object> saveRole(@RequestBody List<PermissionManagementResVO> roleList,
                                          HttpSession session){
        log.info("[관리자] 역활권한 저장 시작 userId={}", SsoLoginUserVO.userIdOf(session));
        Map<String, Object> result = new HashMap<>();
        SsoLoginUserVO ssoLoginUserVO = (SsoLoginUserVO) session.getAttribute("ssoLoginUser");
        try {
            for(PermissionManagementResVO vo : roleList){
                vo.setFrstRegEmpno(ssoLoginUserVO.getUserId());
            }
            permissionManagementService.saveRole(roleList);
            result.put("result","SUCCESS");
        }catch(Exception e){
            log.error("[관리자] saveRole 실패 userId={}", SsoLoginUserVO.userIdOf(session), e);
            result.put("result","FAIL");
            result.put("message","처리 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.");
        }
        return result;
    }

    /****************************************************
     *   역활권한 삭제
     *****************************************************/
    @Operation(summary = "역할 권한 삭제")
    @PostMapping("/deleteRole")
    @ResponseBody
    public Map<String, Object> deleteRole(@RequestBody PermissionManagementResVO vo){
        log.debug("[역활권한 삭제] 시작");
        Map<String, Object> result = new HashMap<>();
        try {
            permissionManagementService.deleteRole(vo);
            result.put("result","SUCCESS");
        }catch(Exception e){
            log.error("[관리자] deleteRole 실패", e);
            result.put("result","FAIL");
            result.put("message","처리 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.");
        }
        return result;
    }

    /****************************************************
     *  사용자권한 저장
     *****************************************************/
    @Operation(summary = "사용자 권한 저장")
    @PostMapping("/saveUser")
    @ResponseBody
    public Map<String, Object> saveUser(@RequestBody List<PermissionManagementUserResVO> userList,
                                        HttpSession session){
        log.info("[관리자] 사용자권한 저장 시작 userId={}", SsoLoginUserVO.userIdOf(session));
        Map<String, Object> result = new HashMap<>();
        SsoLoginUserVO ssoLoginUserVO = (SsoLoginUserVO) session.getAttribute("ssoLoginUser");
        try {
            for(PermissionManagementUserResVO vo : userList){
                vo.setFrstRegEmpno(ssoLoginUserVO.getUserId());
            }
            permissionManagementService.saveUser(userList);
            result.put("result","SUCCESS");
        }catch(Exception e){
            log.error("[관리자] saveUser 실패 userId={}", SsoLoginUserVO.userIdOf(session), e);
            result.put("result","FAIL");
            result.put("message","처리 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.");
        }
        return result;
    }

    /****************************************************
     *   사용자권한 삭제
     *****************************************************/
    @Operation(summary = "사용자 권한 삭제")
    @PostMapping("/deleteUser")
    @ResponseBody
    public Map<String, Object> deleteUser(@RequestBody PermissionManagementUserResVO vo){
        log.debug("[사용자권한 삭제] 시작");
        Map<String, Object> result = new HashMap<>();
        try {
            permissionManagementService.deleteUser(vo);
            result.put("result","SUCCESS");
        }catch(Exception e){
            log.error("[관리자] deleteUser 실패", e);
            result.put("result","FAIL");
            result.put("message","처리 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.");
        }
        return result;
    }

    /****************************************************
     *   역할권한의 (메뉴/버튼) 상세 화면 연결
     *****************************************************/
    @GetMapping("/permissionDetail")
    public String permissionDetail(@RequestParam(value = "mode", defaultValue = "Detail") String mode,
                                   @RequestParam(value = "menuId", defaultValue = "") String menuId,
                                   @RequestParam(value = "roleId", defaultValue = "") String roleId,
                                   Model model) {
        log.debug("[역할권한의 (메뉴/버튼) 상세] 시작");

        List<PermissionUserMenuVO> menulist = permissionManagementService.getUserMenuList(roleId);
        model.addAttribute("menulist", menulist);

        model.addAttribute("mode",mode);
        model.addAttribute("roleId",roleId);
        model.addAttribute("menuId",menuId);

        return "/module/admin/permissionDetail";
    }

    /****************************************************
     *  역할권한의 메뉴 저장
     *****************************************************/
    @Operation(summary = "사용자 메뉴 권한 저장")
    @PostMapping("/saveMenuParmission")
    @ResponseBody
    public Map<String, Object> saveMenuParmission(@RequestBody PermissionUserMenuListVO request,
                                        HttpSession session){
        log.info("[관리자] 역할권한의 메뉴권한 저장 시작 userId={}", SsoLoginUserVO.userIdOf(session));
        Map<String, Object> result = new HashMap<>();
        try {
            SsoLoginUserVO ssoLoginUserVO = (SsoLoginUserVO) session.getAttribute("ssoLoginUser");
            if(ssoLoginUserVO != null){
                request.setFrstRegEmpno(ssoLoginUserVO.getUserId());
            }
            permissionManagementService.saveMenuParmission(request);

            result.put("result","SUCCESS");
        }catch(Exception e){
            log.error("[관리자] saveMenuParmission 실패 userId={}", SsoLoginUserVO.userIdOf(session), e);
            result.put("result","FAIL");
            result.put("message","처리 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.");
        }
        return result;
    }

    /****************************************************
     *   역할권한의 버튼리스트  조회
     *****************************************************/
    @Operation(summary = "사용자 메뉴 버튼 목록 조회")
    @PostMapping("/selectUserBtnLsit")
    @ResponseBody
    public Map<String, Object> selectUserBtnLsit(@RequestParam(value = "menuId", defaultValue = "") String menuId,
                                                 @RequestParam(value = "roleId", defaultValue = "") String roleId) {
        log.debug("[역할권한의 버튼 조회] 시작");
        Map<String, Object> result = new HashMap<>();
        try {
            List<PermissionUserBtnResVO> list = permissionManagementService.selectUserBtnLsit(roleId,menuId);
            result.put("result","SUCCESS");
            result.put("list",list);

        }catch(Exception e){
            log.error("[관리자] selectUserBtnLsit 실패", e);
            result.put("result","FAIL");
            result.put("message","처리 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.");
        }
        return result;
    }

    /****************************************************
     *  역할권한의 버튼 저장
     *****************************************************/
    @Operation(summary = "사용자 메뉴 버튼 권한 저장")
    @PostMapping("/saveMenuBtnParmission")
    @ResponseBody
    public Map<String, Object> saveMenuBtnParmission(@RequestBody Map<String, Object> param,
                                                  HttpSession session){
        log.info("[관리자] 역할권한의 버튼권한 저장 시작 userId={}", SsoLoginUserVO.userIdOf(session));
        Map<String, Object> result = new HashMap<>();
        try {
            SsoLoginUserVO ssoLoginUserVO = (SsoLoginUserVO) session.getAttribute("ssoLoginUser");
            String frstRegEmpno = null;
            if(ssoLoginUserVO != null){
                frstRegEmpno = ssoLoginUserVO.getUserId();
            }
            String roleId = (String)param.get("roleId");
            String menuId = (String)param.get("menuId");

            List<String> btnList = (List<String>) param.get("btnList");
            permissionManagementService.saveMenuBtnParmission(roleId,menuId,frstRegEmpno,btnList);
            result.put("result","SUCCESS");
        }catch(Exception e){
            log.error("[관리자] saveMenuBtnParmission 실패 userId={}", SsoLoginUserVO.userIdOf(session), e);
            result.put("result","FAIL");
            result.put("message","처리 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.");
        }
        return result;
    }

    /****************************************************
     *   사용자정보  조회
     *****************************************************/
    @Operation(summary = "권한 지정 사용자 검색")
    @PostMapping("/searchUserList")
    @ResponseBody

    public Map<String, Object> searchUserList(@RequestBody Map<String, String> param) {
        log.debug("[사용자정보 조회] 시작");
        Map<String, Object> result = new HashMap<>();

        String empNo = param.getOrDefault("empNo","");
        String empKorFlnm = param.getOrDefault("empKorFlnm","");

        try {
            List<CommonUserListVO> userList = permissionManagementService.searchUserList(empNo,empKorFlnm);
            result.put("result","SUCCESS");
            result.put("userList",userList);

        }catch(Exception e){
            log.error("[관리자] searchUserList 실패", e);
            result.put("result","FAIL");
            result.put("message","처리 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.");
        }
        return result;
    }


}

