package com.agilesoda.module.common.entity;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OcuocAprvlEntity {
    private long aprvlId; //결재ID
    private long appcId; //신청ID
    private String appcTypCd; // 신청유형코드(FORM(서식신청), TASK(업무신청))
    private int aprvlSeqno; // 결재순번
    private String apofId; //결재자ID
    private String appcStaCd;	 // 결재상태코드 (APRV(승인), CANC(취소), RTN(반려), WAIT(대기))
    private String aprvlOpn; // 결재의견
    private String userId;	// 등록자ID
}
