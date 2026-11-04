package com.agilesoda.module.apply.entity;

import lombok.Data;

import java.util.List;

@Data
public class TaskListResEntity {
    private long appcId;
    private String wkflowNm;
    private String wkflowId;

    List<OcuocBzwkfEntity> wkflows;
}
