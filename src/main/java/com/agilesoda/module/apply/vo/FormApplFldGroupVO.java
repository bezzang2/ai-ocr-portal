package com.agilesoda.module.apply.vo;

import lombok.Data;

import java.util.List;

@Data
public class FormApplFldGroupVO {
    private long groupFldId;
    private String groupNm;
    private String srcTypeCd;
    private List<String> columns;
    private List<List<FormApplFldVO>> rows;
}
