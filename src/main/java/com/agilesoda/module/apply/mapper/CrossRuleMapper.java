package com.agilesoda.module.apply.mapper;

import com.agilesoda.module.apply.vo.CrossRoleVO;
import com.agilesoda.module.apply.vo.CrossRuleVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CrossRuleMapper {
    int insertRule(@Param("rule") CrossRuleVO rule, @Param("userId") String userId);

    int updateRule(@Param("rule") CrossRuleVO rule, @Param("userId") String userId);

    int deleteRule(@Param("ruleId") long ruleId);

    int insertHistory(@Param("ruleId") long ruleId, @Param("chngCtnt") String chngCtnt, @Param("userId") String userId);

    List<CrossRuleVO> selectDefRuleList(@Param("wkId") String wkId);

    CrossRuleVO selectDefRule(@Param("ruleId") long ruleId);

    int insertVersion(@Param("ruleId") long ruleId, @Param("ruleVer") int ruleVer, @Param("schemaVer") int schemaVer,
                      @Param("defJson") String defJson, @Param("userId") String userId);

    int updateDefHeader(@Param("rule") CrossRuleVO rule);

    int updateUse(@Param("ruleId") long ruleId, @Param("useYn") String useYn, @Param("userId") String userId);

    List<CrossRoleVO> selectRoles(@Param("wkId") String wkId);

    int mergeRole(@Param("role") CrossRoleVO role, @Param("userId") String userId);

    int deleteRole(@Param("wkId") String wkId, @Param("formId") long formId, @Param("roleNm") String roleNm);
}
