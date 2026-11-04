package com.agilesoda.module.admin.vo;

import lombok.Data;

@Data
public class AuditLogResVO {
    private String auditId; //감사로그id
    private String userId; //사용자id
    private String userNm; //사용자명
    private String menuId; //메뉴id
    private String menuNm; //메뉴명
    private String btnId; //버튼id
    private String actionCd; //행위코드
    private String actionNm; //행위명
    private String reqUrl; //요청url
    private String reqIp; //요청ip
    private String frstRegDttm; //등록일시
    private String brNm; //부서명

}
