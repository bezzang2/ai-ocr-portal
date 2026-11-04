package com.agilesoda.module.apply.vo;

import lombok.Data;

import java.util.List;

@Data
public class FormApplImgDeleteResultVO {
    private boolean applDeleted;
    private List<FormApplImgVO> formImgList;
    private List<FormApplFldVO> fldList;
    private List<FormApplFldGroupVO> groupList;
}
