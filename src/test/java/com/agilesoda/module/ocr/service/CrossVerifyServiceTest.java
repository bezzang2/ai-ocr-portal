package com.agilesoda.module.ocr.service;

import com.agilesoda.module.ocr.mapper.OcrReqMapper;
import com.agilesoda.module.ocr.vo.OcrAgentData;
import com.agilesoda.module.ocr.vo.OcrResultLefrmVO;
import com.agilesoda.module.ocr.vo.OcrResultWkGroupVO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 서식간 검증 상세: Agent 결과에 업무명과 항목별 서식(LEFRM_ID)을 붙인다. */
class CrossVerifyServiceTest {

    private static OcrResultLefrmVO form(long id, String docId, String name) {
        OcrResultLefrmVO form = new OcrResultLefrmVO();
        form.setLefrmId(id);
        form.setFormId(docId);
        form.setLefrmNm(name);
        return form;
    }

    @Test
    void detailGetsFormsAndWorkflowName() {
        OcrAgentDataClient agent = mock(OcrAgentDataClient.class);
        OcrReqMapper mapper = mock(OcrReqMapper.class);
        when(mapper.selectWkFormList("wk")).thenReturn(List.of(form(10, "BIZ", "사업자등록증")));
        OcrResultWkGroupVO workflow = new OcrResultWkGroupVO();
        workflow.setWkNm("기업 여신 신규");
        when(mapper.selectWkGroupList("wk")).thenReturn(List.of(workflow));
        OcrAgentData.CrossRuleResult rule = new OcrAgentData.CrossRuleResult();
        rule.setRuleId(7);
        OcrAgentData.CrossDetail detail = new OcrAgentData.CrossDetail();
        detail.setWorkflowId("wk");
        detail.setRules(List.of(rule));
        when(agent.crossVerification("t1")).thenReturn(detail);

        OcrAgentData.CrossDetail result = new CrossVerifyService(agent, mapper).detail("t1");

        assertEquals("기업 여신 신규", result.getWorkflowName());
        assertEquals(10L, result.getForms().get(0).get("formId"));
        assertEquals("BIZ", result.getForms().get(0).get("formCode"));
    }
}
