package com.agilesoda.module.apply.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class WkflowListVO {
    @JsonProperty("items")
    private List<WkflowVO> items;
    @JsonProperty("page")
    private int page;
    @JsonProperty("page_size")
    private int pageSize;
    @JsonProperty("total")
    private int total;
}
