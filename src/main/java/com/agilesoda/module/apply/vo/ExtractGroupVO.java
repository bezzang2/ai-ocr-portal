package com.agilesoda.module.apply.vo;

import lombok.Data;

import java.util.List;

@Data
public class ExtractGroupVO {
    private String path;
    private String displayLabel;
    private String mode;
    private String description;
    private List<ExtractFieldVO> items;
}
