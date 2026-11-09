package com.agilesoda.module.common;

import com.agilesoda.common.sso.SsoConfig;
import com.agilesoda.common.util.StringUtil;
import com.agilesoda.module.admin.vo.SsoLoginUserVO;
import com.agilesoda.module.common.entity.CommonMenuEntity;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;
import java.util.Map;

@Slf4j
@Controller
@RequiredArgsConstructor
public class CommonController {

    @Value("${sso.server-url}")
    private String serverUrl;

    @Value("${sso.nls-url}")
    private String nlsUrl;

    @Value("${sso.nls-port}")
    private int nlsPort;

    /** SSO 서버가 없는 개발 환경(local·demo·devenv)에서 켠다. 은행 환경은 기본값 false 로 로그인 오류 화면을 보여 준다. */
    @Value("${sso.dev-user-enabled:false}")
    private boolean devUserEnabled;

    private final CommonService commonService;

    /****************************************************
     *   최초 메인화며 연결부분
     *****************************************************/
    @GetMapping({"","/","/index"})
    public String index(HttpServletRequest request,
                        HttpServletResponse response,
                        HttpSession session,
                        Model model) throws Exception {
        log.debug("######################## index START ####################");
        /* ================================================================
         * 		1. 로그인 사용자
         *  ================================================================ */
        SsoLoginUserVO ssoLoginUser = (SsoLoginUserVO) session.getAttribute("ssoLoginUser");
        //세션에 로그인 정보가 없을 경우에만 생성
        String userId = null;
        log.debug("############# ssoLoginUser = {} ", ssoLoginUser);

        //  SSO 로그인 페이지에서 redirect로 진입 시 세션에 정보가 없기 때문에
        //  SSO의 정보를 가져와서 세팅한다.
        if(ssoLoginUser == null && devUserEnabled){
        	/*
            log.debug("[메인] SSO 없는 환경, 개발용 사용자로 진입");
            ssoLoginUser = new SsoLoginUserVO();
            ssoLoginUser.setUserId("TEST");
            ssoLoginUser.setUserName("테스트");
            ssoLoginUser.setDeptCode("D001");
            ssoLoginUser.setDeptName("개발팀");
            session.setAttribute("ssoLoginUser", ssoLoginUser);
            */
        	return "test/login";
        }

        if(ssoLoginUser == null ){
            SsoConfig ssoConfig = new SsoConfig(serverUrl, nlsUrl, nlsPort);
            //  step1 : SSO ID 수신
            String ssoId = StringUtil.nvl(ssoConfig.getSsoId(request));;
            log.debug("step1: SSO ID => {}", ssoId);

            if(!"".equals(ssoId)){
                //  step4 : 쿠키 유효성 확인 : 정상(0)
                String retCode = ssoConfig.getEamSessionCheck(request, response);
                log.debug("step4: retCode => {}", retCode);

                if(!"0".equals(retCode)) {
                    model.addAttribute("status", "500");
                    model.addAttribute("error", "세션 쿠키 유효성 검증에 실패했습니다.[" + retCode + "]");
                    model.addAttribute("path", request.getRequestURI());
                    model.addAttribute("message", "SSO 로그인 중 오류가 발생했습니다.");
                    return "error/error";
                }

                userId = ssoId;
                ssoLoginUser = new SsoLoginUserVO();
            }
            else{
                model.addAttribute("status", "500");
                model.addAttribute("error", "로그인 정보가 존재하지 않습니다.");
                model.addAttribute("path", request.getRequestURI());
                model.addAttribute("message", "SSO 로그인 중 오류가 발생했습니다.");
                return "error/error";
            }
        }
        else{
            userId = ssoLoginUser.getUserId();
        }

        // userId가 존재하면 사용자정보 DB조회
        SsoLoginUserVO userInfo = commonService.selectUserInfo(userId);
        if(userInfo == null && devUserEnabled){
            log.warn("[메인] DB에 사용자 정보가 없어 개발용 사용자 그대로 사용 userId={}", userId);
            userInfo = ssoLoginUser;
        }
        if(userInfo == null){
            model.addAttribute("status", "500");
            model.addAttribute("error", "사용자 정보 조회에 실패 했습니다.");
            model.addAttribute("path", request.getRequestURI());
            model.addAttribute("message", "사용자 정보가 존재하지 않습니다.[" + userId + "]");
            return "error/error";
        }

        // 기존 로그인 객체를 DB 조회 정보로 갱신
        ssoLoginUser.setUserId(userInfo.getUserId());
        ssoLoginUser.setUserName(userInfo.getUserName());
        ssoLoginUser.setDeptCode(userInfo.getDeptCode());
        ssoLoginUser.setDeptName(userInfo.getDeptName());

        session.setAttribute("ssoLoginUser",ssoLoginUser);

        /* ================================================================
         * 		2. LNB 메뉴 최초 1번만 DB조회
         *  ================================================================ */
        List<CommonMenuEntity> menuList = (List<CommonMenuEntity>) session.getAttribute("menuList");
        if(menuList == null) {
            menuList = commonService.commonMenuList("", userId);
            session.setAttribute("menuList", menuList);
        }

        /* ================================================================
         * 		3. 메뉴의 버튼 권한
         *  ================================================================ */
        List<CommonMenuEntity> buttonList = commonService.commonRoleButtonList(userId);
        session.setAttribute("buttonList", buttonList);
        //model.addAttribute("buttonList",buttonList);

        return "module/dash/dashboard";

    }
}
