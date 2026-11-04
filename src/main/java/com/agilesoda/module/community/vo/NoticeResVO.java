package com.agilesoda.module.community.vo;

import lombok.Data;

@Data
public class NoticeResVO {
    private int noticeSeqno; // 공지일련번호
    private String noticeTtl; //공지제목
    private String noticeCn; //공지내용
    private String faqAnsmer;       // faq답변내용
    private String noticeTypeCd;       // 공지타입구분
    private String viewCnt; //조회수
    private String useYn; //사용여부
    private String noticeMsgDvcd; //공지메시지구분
    private String stdd; //시작일자
    private String endt; //종료일자
    private String atchFileId; //첨부파일 그룹키
    private String lastChngEmpno;       // 최종변경자사번


}

