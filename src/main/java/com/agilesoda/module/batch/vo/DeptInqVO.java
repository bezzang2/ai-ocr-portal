package com.agilesoda.module.batch.vo;

import lombok.Data;

@Data
public class DeptInqVO {

    private String brCd; //부서코드
    private String brNm; //부서명
    private String brTpCd; //부서유형코드
    private String posiMobrCd; //관련 부서코드
    private String sidoDscd; //시도코드
    private String telNo; //전화번호
    private String faxNo; //펙스번호
    private String brAd; //부서주소
    private String brOpenDt; //개설일
    private String brShutDt; //페쇄일
    private String itgbrCd; //통합부서코드
    private String fstRegSdH; //최초등록 관련일자
    private String fstRegPeNo; //최초등록 관련코드
    private String lstChgDh; //최종변경 관련일자
    private String lstChgPeNo; //최종변경 관련코드
    private String giroCd; //지로코드
    private String delYn; //삭제여부

}

