package com.agilesoda.module.apply.mapper;

import org.apache.ibatis.annotations.Mapper;
import java.util.*;


@Mapper
public interface OcrTaskUsageApplyMapper {
	
	int selectUsageCnt(Map<String, Object> param) throws Exception;
	
	List<Map<String, Object>> selectUsageList(Map<String, Object> param) throws Exception;
	
	int updateUsage(Map<String, Object> param) throws Exception;
	
	int insertApply(Map<String, Object> param) throws Exception;
	
	int insertApplyLine(Map<String, Object> param) throws Exception;
	
	List<Map<String, Object>> selectTaskList(Map<String, Object> param) throws Exception;
	
	
}
