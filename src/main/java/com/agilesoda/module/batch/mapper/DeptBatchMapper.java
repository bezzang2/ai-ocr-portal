package com.agilesoda.module.batch.mapper;

import com.agilesoda.module.batch.vo.DeptInqVO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Map;

@Mapper
public interface DeptBatchMapper {
    int mergeDept(DeptInqVO vo);
}
