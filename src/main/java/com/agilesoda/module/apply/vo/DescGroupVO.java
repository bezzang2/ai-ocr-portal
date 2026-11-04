package com.agilesoda.module.apply.vo;

import lombok.Data;

import java.util.List;

@Data
public class DescGroupVO {
    private long groupFldId;
    private String groupNm;
    private String groupDesc;
    private List<DescItemVO> items;
}
