package com.agilesoda.module.apply.vo;

import lombok.Builder;
import lombok.Data;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class WorkflowDtlInfo {
    private String workflowId;
    private String workflowNm;
    private String tag;

    private List<FieldInfo> fields;
}
