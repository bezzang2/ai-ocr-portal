package com.agilesoda.module.apply.vo;

import lombok.Data;

import java.util.List;

@Data
public class FormApplTempSaveResultVO {
    private long applId;
    private List<FormApplFldVO> fldList;
    private List<FormApplFldGroupVO> groupList;
    private List<DescGroupVO> descGroupList;
    private List<FormApplWriteCntnVO> writeCntnList;
    private List<FormApplFileVO> fileList;
    private List<FormApplImgVO> formImgList;
    private Long activeImgId;
    private List<FormApplMgrVO> mgrList;
    private String procStatCd;
}
