package com.agilesoda.module.batch.mapper;

import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Map;

@Mapper
public interface UserBatchMapper {
    List<Map<String, Object>> selectUserList();

    int mergeUser(Map<String, Object> user);

    int deleteUser(Map<String, Object> user);
}
