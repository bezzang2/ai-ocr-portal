package com.agilesoda.module.apply.vo;

import lombok.Data;

import java.util.List;

@Data
public class AiSuggestPreviewResultVO {
    private List<FormApplFldVO> fldList;
    private List<FormApplFldGroupVO> groupList;
    private List<DescGroupVO> descGroupList;
}
