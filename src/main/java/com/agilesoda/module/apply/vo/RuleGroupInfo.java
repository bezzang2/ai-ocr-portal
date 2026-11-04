package com.agilesoda.module.apply.vo;

import lombok.Data;

import java.util.List;

@Data
public class RuleGroupInfo {
    private int idx;
    private String name;
    private String type;
    private String typeName;
    private boolean delYn;
    private boolean displayYn;

    List<RuleGroupWorkflow> wkflows;
}
