package com.agilesoda.module.apply.vo;

import lombok.Data;

import java.util.List;

@Data
public class SchemaSuggestionResultVO {
    private String suggestionId;
    private String status;
    private List<SchemaSuggestionItemVO> results;
    private Integer pollAfterMs;
    private Object error;
}
