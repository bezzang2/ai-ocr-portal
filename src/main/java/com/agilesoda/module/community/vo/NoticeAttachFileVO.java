package com.agilesoda.module.community.vo;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class NoticeAttachFileVO {

    private String atchFileId;   // 첨부파일 그룹키
    private int fileSeq;      // 파일순번
    private String orgFileNm;    // 원본 파일명
    private String saveFileNm;   // 저장 파일명
    private String filePath;	 // 저장경로
    private long fileSize;	 // 파일크기
    private String fileExt;	     // 파일확장자

}
