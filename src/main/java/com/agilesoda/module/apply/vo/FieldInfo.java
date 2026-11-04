package com.agilesoda.module.apply.vo;

import lombok.Builder;
import lombok.Data;
import lombok.Getter;

@Getter
@Builder
public class FieldInfo {
    private String path;
    private String displayLabel;
    private String dtype;
    private boolean required;
    private int length;
    private boolean masking;
    private String maskingPattern;
}
