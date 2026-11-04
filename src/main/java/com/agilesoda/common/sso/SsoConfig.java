package com.agilesoda.common.sso;

import com.initech.eam.api.*;
import com.initech.eam.base.APIException;
import com.initech.eam.base.EmptyResultException;
import com.initech.eam.nls.CookieManager;
import com.initech.eam.smartenforcer.SECode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

@Slf4j
@Data
public class SsoConfig{

    /***[SERVICE CONFIGURATION]***********************************************************************/
    private String SERVICE_NAME = "Web";
    private String SERVER_URL 	= "https://smartit.kjbank.com/aiportal";
    private String SERVER_PORT = "";
    private String ASCP_URL = SERVER_URL + "/index";
/*************************************************************************************************/


    /***[SSO CONFIGURATION]**]***********************************************************************/
    private String NLS_URL;
    private int NLS_PORT;
    private String NLS_LOGIN_URL;
    private String NLS_LOGOUT_URL;
    private String NLS_ERROR_URL;

    /***[SSO ND LIST 운영]**]***********************************************************************/
    private static String ND_URL1 = "http://sso.kjbank.com:5480";

    private static Vector PROVIDER_LIST = new Vector();

    private static final int COOKIE_SESSTION_TIME_OUT = 3000000;

    // 인증 타입 (ID/PW 방식 : 1, 인증서 : 3)
    private String TOA = "1";
    private String SSO_DOMAIN = ".kjbank.com";

    private static final int timeout = 15000;
    private static NXContext context = null;
    static{
        List<String> serverurlList = new ArrayList<String>();
        serverurlList.add(ND_URL1);

        context = new NXContext(serverurlList,timeout);
        CookieManager.setEncStatus(true);

        PROVIDER_LIST.add("dsso.kjbank.com");
        SECode.setCookiePadding("_V42");
    }

    public SsoConfig(String serverUrl, String nlsUrl, int nlsPort) {
        SERVER_URL = serverUrl;
        NLS_URL = nlsUrl;
        NLS_PORT = nlsPort;

        NLS_LOGIN_URL = NLS_URL + ":" + NLS_PORT + "/nls3/clientLogin.jsp";
        NLS_LOGOUT_URL = NLS_URL + ":" + NLS_PORT + "/nls3/NCLogout.jsp";
        NLS_ERROR_URL = NLS_URL + ":" + NLS_PORT + "/nls3/error.jsp";
    }

    // by Kang
    public NXContext getContext(){
        NXContext context = null;
        try
        {
            List serverurlList = new ArrayList();
            serverurlList.add(ND_URL1);
            context = new NXContext(serverurlList);
            CookieManager.setEncStatus(true);

            PROVIDER_LIST.add("dsso.kjbank.com");
            //NLS3 web.xml의 CookiePadding 값과 같아야 한다. 안그럼 검증 페일남
            SECode.setCookiePadding("_V42");
        }
        catch (Exception e)
        {
            log.error("[SSO] ND 컨텍스트 초기화 실패 ndUrl={}", ND_URL1, e);
        }
        return context;
    }

    // 통합 SSO ID 조회
    public String getSsoId(HttpServletRequest request) {
        String sso_id = null;
        sso_id = CookieManager.getCookieValue(SECode.USER_ID, request);
        return sso_id;
    }

    public String getSystemAccount(String sso_id) {
        NXContext context = null;
        NXUserAPI userAPI = null;
        String retValue = null;

        try {
            context = getContext();
            userAPI = new NXUserAPI(context);
            System.out.println("##############################################");
            System.out.println("sso_id 2: "+sso_id);
            System.out.println("SERVICE_NAME : "+SERVICE_NAME);
            System.out.println("##############################################");
            NXAccount account = userAPI.getUserAccount(sso_id, SERVICE_NAME);
            System.out.println(account.toString());
            retValue = account.getAccountName();
            System.out.println("retValue = " + retValue);
        } catch (Exception e) {
            log.error("[SSO] 시스템 계정 조회 실패 ssoId={} service={}", sso_id, SERVICE_NAME, e);
        }
        return retValue;
    }

    // 통합 SSO 로그인페이지 이동
    public void goLoginPage(HttpServletResponse response, String uurl)
            throws Exception {
        com.initech.eam.nls.CookieManager.addCookie(SECode.USER_URL, uurl, SSO_DOMAIN, response);
        com.initech.eam.nls.CookieManager.addCookie(SECode.R_TOA, TOA, SSO_DOMAIN, response);
        response.sendRedirect(NLS_LOGIN_URL + "?UURL=" + uurl);
    }

    // 통합 SSO 로그인페이지 이동
    public void goLoginPage(HttpServletResponse response)throws Exception {
        CookieManager.addCookie(SECode.USER_URL, ASCP_URL, SSO_DOMAIN, response);
        CookieManager.addCookie(SECode.R_TOA, TOA, SSO_DOMAIN, response);
        response.sendRedirect(NLS_LOGIN_URL);
    }

    // 통합인증 세션을 체크 하기 위하여 사용되는 API
    public String getEamSessionCheck(HttpServletRequest request,HttpServletResponse response){
        String retCode = "";
        try {
            retCode = CookieManager.verifyNexessCookie(request, response, 10, COOKIE_SESSTION_TIME_OUT,PROVIDER_LIST);
        } catch(Exception npe) {
            log.error("[SSO] 세션 쿠키 검증 실패", npe);
        }
        return retCode;
    }

    //ND API를 사용해서 쿠키검증하는것(현재 표준에서는 사용안함, 근데 해도 되기는 함)
    public String getEamSessionCheck2(HttpServletRequest request,HttpServletResponse response)
    {
        String retCode = "";
        try {
            NXNLSAPI nxNLSAPI = new NXNLSAPI(context);
            retCode = nxNLSAPI.readNexessCookie(request, response, 0, 0);
        } catch(Exception npe) {
            log.error("[SSO] ND API 쿠키 검증 실패", npe);
        }
        return retCode;
    }

    // SSO 에러페이지 URL
    public void goErrorPage(HttpServletResponse response, int error_code)throws Exception {
        CookieManager.removeNexessCookie(SSO_DOMAIN, response);
        CookieManager.addCookie(SECode.USER_URL, ASCP_URL, SSO_DOMAIN, response);
        response.sendRedirect(NLS_ERROR_URL + "?errorCode=" + error_code);
    }

    /**
     *	사용자 기본정보 가져오기.
     *	@param	userid
     *	@return	Properties
     */
    public Properties getUserInfos(String userid) throws Exception {

        NXContext context = null;
        context = getContext();

        NXUserAPI userAPI = new NXUserAPI(context);
        Properties propt = null;

        if (userid==null || userid.length() <= 0)
            return propt;

        try {
            NXUserInfo userInfo = userAPI.getUserInfo(userid);
            propt = new Properties();
            //System.out.println("userInfo.getName()=["+userInfo.getName()+"]");
            propt.setProperty("USERID", userInfo.getUserId());
            propt.setProperty("EMAIL", userInfo.getEmail());
            propt.setProperty("ENABLE", String.valueOf(userInfo.getEnable()));
            propt.setProperty("STARTVALID", userInfo.getStartValid());
            propt.setProperty("ENDVALID", userInfo.getEndValid());
            propt.setProperty("NAME", userInfo.getName());
            propt.setProperty("ENCPASSWD", userInfo.getEncpasswd());
            propt.setProperty("LASTPASSWDCHANGE", userInfo.getLastpasswdchange());
            propt.setProperty("LastLoginIP", userInfo.getLastLoginIp());
            propt.setProperty("LastLoginTime", userInfo.getLastLoginTime());
            propt.setProperty("LastLoginAuthLevel",	userInfo.getLastLoginAuthLevel());

        } catch (EmptyResultException e) {
            log.error("[SSO] 사용자 정보 없음 userId={}", userid, e);
        } catch (APIException e) {
            log.error("[SSO] 사용자 정보 조회 API 오류 userId={}", userid, e);
        } catch (Exception e) {
            log.error("[SSO] 사용자 정보 조회 실패 userId={}", userid, e);
        }
        return propt;
    }

    /**
     *	사용자의 기본정보 중에서 입력된 키 값만 가지고 온다.
     *	@param	userid
     *	@param	key
     *	@return	String
     */
    public String getUserInfo(String userid, String key) throws Exception {
        if(userid==null||userid.length()<1) return null;

        String userInfo = "";

        try{
            Properties propt = getUserInfos(userid);
            userInfo = propt.getProperty(key);
        } catch (Exception e) {
            //throw e;
            log.error("[SSO] 사용자 정보 항목 조회 실패 userId={} key={}", userid, key, e);
        }
        return userInfo;
    }

    /**
     * 사용자 확장정보 조회
     * return : Properties
     * 사용자가 존재하지 않거나 확장필드가 없다면 return null
     */
    public Properties getUserExFields(String userid)
            throws Exception {
        NXContext context = getContext();
        NXUserAPI userAPI = new NXUserAPI(context);
        Properties prop = null;
        NXExternalFieldSet nxefs = null;

        try {
            nxefs = userAPI.getUserExternalFields(userid);
            prop = new Properties();

            if (nxefs != null) {
                Iterator iter = nxefs.iterator();
                while(iter.hasNext()) {
                    NXExternalField nxef = (NXExternalField) iter.next();
                    if(nxef.getName().equals("PASSINIT")){
                        String sPassintVal = (String) nxef.getValue();
                        prop.setProperty(nxef.getName(), sPassintVal.equals("") ? "0" : sPassintVal);
                    }
                    else
                        prop.setProperty(nxef.getName(), (String) nxef.getValue());
                    //System.out.println("확장 필드 " + nxef.getName() + " : " + nxef.getValue());
                }
            } else {
                // prop is null
            }
        } catch (EmptyResultException e) {
            // 사용자가 존재하지 않거나 확장필드가 없음
        } catch (APIException e) {
            throw e;
            //e.printStackTrace();
        }
        //System.out.println("getUserExFields:[" + prop + "]");
        return prop;
    }

    /**
     * 사용자의 특정 확장 필드 정보 조회
     * return : String
     * 사용자가 존재하지 않거나 확장필드가 없다면 return null
     */
    public String getUserExField(String userid, String exName)
            throws Exception {
        NXContext context = getContext();
        NXUserAPI userAPI = new NXUserAPI(context);
        NXExternalField nxef = null;
        String returnValue = null;

        try {

            System.out.println("getUserExField===" + userid);
            nxef = userAPI.getUserExternalField(userid, exName);
            System.out.println("nxef===" + (String) nxef.getValue());
            returnValue = (String) nxef.getValue();
        } catch (EmptyResultException e) {
            // 사용자가 존재하지 않거나 확장필드가 없음
        } catch (APIException e) {
            throw e;
            //e.printStackTrace();
        } catch (IllegalArgumentException e) {
            throw e;
            //e.printStackTrace();
        }
		/*
		System.out.println("getUserExField:[" + userid + ":"
			+ exName + ":" + returnValue+ "]");
		*/
        return returnValue;
    }
}
