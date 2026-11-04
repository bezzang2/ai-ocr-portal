package com.agilesoda.module.apply.vo;

import lombok.Data;

@Data
public class ExtractFieldVO {
    private String path;
    private String displayLabel;
    private String dtype;
    private boolean required;
    private String description;
}
