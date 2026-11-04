package com.agilesoda.module.admin.vo;

import lombok.Data;

@Data
public class AuditLogInqVO {
    private String fromDate; //시작일자
    private String toDate; //종료일자
    private String logBrCd; //부서id
    private String userId; //사용자id

    private Integer page;
    private Integer pageSize;
    private Integer offset;
}
