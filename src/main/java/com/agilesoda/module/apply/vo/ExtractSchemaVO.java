package com.agilesoda.module.apply.vo;

import lombok.Data;

import java.util.List;

@Data
public class ExtractSchemaVO {
    private List<ExtractFieldVO> fields;
    private List<ExtractGroupVO> groups;
}
