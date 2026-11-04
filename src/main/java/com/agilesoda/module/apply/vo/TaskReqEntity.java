package com.agilesoda.module.apply.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class TaskReqEntity {
    @JsonProperty("name")
    private String name;
    @JsonProperty("project_id")
    private String projectId;
    @JsonProperty("source_workflow_ids")
    private List<String> sourceWorkflowIds;
    @JsonProperty("description")
    private String description;
    @JsonProperty("tag")
    private String tag;
    @JsonProperty("documents")
    private List<Document> documents;

    private String bzwkCd;
    
    class Document{
    	String filename;
    	String format;
    	String data;
    }
}
