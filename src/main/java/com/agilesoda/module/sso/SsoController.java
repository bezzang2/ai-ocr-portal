package com.agilesoda.module.sso;

import com.agilesoda.common.sso.SsoConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.agilesoda.common.util.StringUtil;
import com.agilesoda.module.admin.vo.SsoLoginUserVO;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Controller
@RequestMapping("/sso")
@RequiredArgsConstructor
public class SsoController {

    @Value("${spring.profiles.active}")
    private String active;

    @Value("${sso.server-url}")
    private String serverUrl;

    @Value("${sso.nls-url}")
    private String nlsUrl;

    @Value("${sso.nls-port}")
    private int nlsPort;
    
    @Value("${sso.uurl}")
    private String uurl;

    /****************************************************
     *   SSO 로그인
     *****************************************************/
    @GetMapping("/login")
    public void ssoLogin(HttpServletRequest request, HttpServletResponse response) throws Exception {
        log.debug("######################## ssoLogin START ####################");
        //  step1 : SSO ID 수신
        SsoConfig ssoConfig = new SsoConfig(serverUrl, nlsUrl, nlsPort);
        //  step1 : SSO ID 수신
        String ssoId = StringUtil.nvl(ssoConfig.getSsoId(request)); //"26W0068";
        log.debug("step1: SSO ID => {}", ssoId);

        //  step2 : UURL 수신
        log.debug("step2: UURL => {}", uurl);

        //  step3 : sso id가 없으면 로그인 페이지로 이동
        if("".equals(StringUtil.nvl(ssoId))){
            if("".equals(StringUtil.nvl(uurl))){
                uurl = ssoConfig.getASCP_URL();
            }
            log.debug("step3: UURL => {}", uurl);

            ssoConfig.goLoginPage(response, uurl);
            return;
        }

        SsoLoginUserVO userVo = new SsoLoginUserVO();
        userVo.setUserId(ssoId);
        if(!"local".equals(active)) {
            //  step4 : 쿠키 유효성 확인 : 정상(0)
            String retCode = ssoConfig.getEamSessionCheck(request, response);
            log.debug("step4: retCode => {}", retCode);
            /*TODO: 1005 오류가 왜 나는지 확인 필요.
            if (!"0".equals(retCode)) {
                ssoConfig.goErrorPage(response, Integer.parseInt(retCode));
                return;
            }

             */
        }
        HttpSession session = request.getSession();
        session.setAttribute("ssoLoginUser", userVo);

        log.debug("protocol[{}] : port[{}] : scheme[{}] : isSecure[{}]", request.getProtocol(), request.getServerPort(), request.getScheme(), request.isSecure());

        response.sendRedirect( uurl);
    }
}
