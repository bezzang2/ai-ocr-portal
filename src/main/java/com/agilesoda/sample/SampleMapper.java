package com.agilesoda.sample;

import java.util.Map;

import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SampleMapper {
	Map<String, Object> selectUser(String userId) throws Exception;
}
