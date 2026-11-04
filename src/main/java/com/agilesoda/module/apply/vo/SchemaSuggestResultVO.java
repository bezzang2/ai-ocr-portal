package com.agilesoda.module.apply.vo;

import lombok.Data;

@Data
public class SchemaSuggestResultVO {
    private String suggestionId;
    private String status;
    private int pollAfterMs;
}
