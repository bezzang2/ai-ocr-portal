package com.agilesoda.module.ocr.service;

import com.agilesoda.module.ocr.mapper.OcrReqMapper;
import com.agilesoda.module.ocr.vo.OcrAgentData;
import com.agilesoda.module.ocr.vo.OcrResultLefrmVO;
import com.agilesoda.module.ocr.vo.OcrResultWkGroupVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 통합검색 > 서식간 검증 결과. 판정과 항목 값은 Agent API 로 받고, 서식(LEFRM_ID)·업무명 같은 Portal 데이터만 붙인다.
 * 항목 수정은 판독결과 검토의 확인값 저장을 그대로 쓰고, 저장이 커밋되면 Agent 가 다시 판정한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CrossVerifyService {
    private final OcrAgentDataClient agentData;
    private final OcrReqMapper ocrReqMapper;

    public List<OcrResultWkGroupVO> workflows() {
        return ocrReqMapper.selectWkGroupList(null);
    }

    /** 기간은 스캔일 yyyyMMdd */
    public List<OcrAgentData.CrossSummary> search(String wkId, String result, String from, String to) {
        return agentData.crossVerifications(wkId, result, from, to);
    }

    /** 규칙 변경 뒤 조회 조건의 판독작업을 한꺼번에 다시 판정. 기간은 스캔일 yyyyMMdd */
    public OcrAgentData.CrossBulkResult evaluateAll(String wkId, String from, String to) {
        return agentData.evaluateCrossVerifications(wkId, from, to);
    }

    public OcrAgentData.CrossDetail detail(String transId) {
        return enrich(agentData.crossVerification(transId));
    }

    public OcrAgentData.CrossDetail evaluate(String transId) {
        return enrich(agentData.evaluateCrossVerification(transId));
    }

    /** 화면이 쓰는 Portal 데이터를 붙인다: 서식(LEFRM_ID), 규칙 문장, 업무명 */
    OcrAgentData.CrossDetail enrich(OcrAgentData.CrossDetail detail) {
        String wkId = detail.getWorkflowId();
        if (wkId == null) return detail;
        List<Map<String, Object>> forms = new ArrayList<>();
        for (OcrResultLefrmVO form : ocrReqMapper.selectWkFormList(wkId)) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("formId", form.getLefrmId());
            row.put("formCode", form.getFormId());
            row.put("formName", form.getLefrmNm());
            forms.add(row);
        }
        detail.setForms(forms);
        ocrReqMapper.selectWkGroupList(wkId).stream().findFirst()
                .ifPresent(workflow -> detail.setWorkflowName(workflow.getWkNm()));
        log.debug("[서식간검증] 상세 구성 transId={} wkId={} rules={} forms={} result={}", detail.getTransactionId(),
                wkId, detail.getRules().size(), forms.size(), detail.getResult());
        return detail;
    }
}
