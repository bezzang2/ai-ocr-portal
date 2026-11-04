package com.agilesoda.module.apply.vo;

import lombok.Data;

import java.util.List;

@Data
public class FormApplTempSaveRequestVO {
    private long applId;
    private String formNm;
    private List<FormApplWriteCntnVO> writeCntnList;
    private List<String> attachSrcTypeCdList;
    private List<FormApplFldVO> fldList;
    private List<FormApplFldGroupVO> groupList;
    private List<FormApplImgMetaVO> formImgList;
    private Long activeImgId;
    private List<FormApplMgrVO> mgrList;
    private boolean submit;
}
