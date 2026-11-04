package com.agilesoda.module.community.vo;

import lombok.*;
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NoticeInqVO {
    private String noticeTtl; //공지제목
    private String startDate; //시작일자
    private String endDate; //종료일자
    private String noticeTypeCd; //공지타입구분

    private Integer page;
    private Integer pageSize;
    private Integer offset;
}
