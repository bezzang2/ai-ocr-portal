package com.agilesoda.module.apply.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TaskInfoReqEntity {
	@JsonProperty("bzwk_cd")
    private String bzwkCd;
	@JsonProperty("prgs_st_cd")
    private String prgsStCd;
	@JsonProperty("br_nm")
    private String brNm;
	@JsonProperty("from_dt")
    private String fromDt;
	@JsonProperty("to_dt")
    private String toDt;
	@JsonProperty("gubun")
    private int gubun;
	@JsonProperty("keyword")
    private String keyword;
	@JsonProperty("page_no")
    private int pageNo;
	@JsonProperty("page_size")
    private int pageSize;
	@JsonProperty("user_id")
    private String userId;
	@JsonProperty("st_pos")
    private Integer stPos;
	@JsonProperty("end_pos")
    private Integer endPos;
}
