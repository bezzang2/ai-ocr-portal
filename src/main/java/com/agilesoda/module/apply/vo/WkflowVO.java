package com.agilesoda.module.apply.vo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class WkflowVO {
    @JsonProperty("workflow_id")
    private String workflowId;
    @JsonProperty("name")
    private String name;
    @JsonProperty("description")
    private String description;
    @JsonProperty("doc_type")
    private String docType;
    @JsonProperty("doc_type_code")
    private String docTypeCode;
    @JsonProperty("status")
    private String status;
    @JsonProperty("active_release_id")
    private String activeReleaseId;
    @JsonProperty("activated_at")
    private String activatedAt;

    /** 상세(workflow_ir)의 분류 클래스('기타' 제외). 목록 응답에는 없고, 상세를 못 가져오면 null 이다. */
    @JsonIgnore
    private List<Classify> classes;

    public record Classify(String className, String docTypeCode) {}
}
