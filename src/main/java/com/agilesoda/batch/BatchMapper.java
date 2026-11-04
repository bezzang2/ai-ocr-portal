package com.agilesoda.batch;

import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Map;

@Mapper
public interface BatchMapper {
    List<Map<String, Object>> selectUserList() throws Exception;
}
