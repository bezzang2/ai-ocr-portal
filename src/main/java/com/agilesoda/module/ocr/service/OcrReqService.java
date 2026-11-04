package com.agilesoda.module.ocr.service;

import com.agilesoda.common.response.entity.RestResponseEntity;
import com.agilesoda.common.rest.RestAgentProvider;
import com.agilesoda.module.ocr.mapper.OcrReqMapper;
import com.agilesoda.module.ocr.vo.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sf.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 판독결과 화면 데이터. 업무·서식은 Portal DB, 판독작업·문서·인식값은 Agent API 에서 받는다.
 * Agent HTTP 호출 동안 DB 연결을 잡지 않도록 트랜잭션을 두지 않는다(조회 문장마다 연결을 바로 돌려준다).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OcrReqService {

    private final OcrReqMapper ocrReqMapper;
    private final RestAgentProvider agentProvider;
    private final OcrAgentDataClient agentData;
    /** 단말시스템 전송용. 응답 지연이 검증완료 응답을 오래 붙잡지 않도록 시간 제한을 둔다. */
    private final RestTemplate restTemplate = terminalRestTemplate();

    /** 단말시스템(C#) 검증완료 결과 수신 API URL. */
    @Value("${aiocr.terminal.verify-url:}")
    private String terminalVerifyUrl;

    /** Agent 판독 결과 다시 가져오기 API (트랜잭션 ID 를 %s 에 넣는다). */
    @Value("${aiocr.agent.apis.result-refresh}")
    private String resultRefreshUrl;

    /** Agent 판독 결과 원본 조회 API (트랜잭션 ID 를 %s 에 넣는다). */
    @Value("${aiocr.agent.apis.result-raw}")
    private String resultRawUrl;

    /**
     * "결과 다시 가져오기" - 결과를 받지 못한 판독작업의 결과를 Agent 에 다시 조회·저장하게 한다.
     * Agent 는 오류도 HTTP 200 본문의 status 로 돌려주므로, 200 이 아니면 ResponseStatusException 으로 올린다(컨트롤러가 처리).
     * @return Agent 응답 data: transaction_id, status(엔진 상태), result_ready
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> refreshResult(String transId) throws Exception {
        long started = System.currentTimeMillis();
        RestResponseEntity<?> res = agentProvider.post(String.format(resultRefreshUrl, transId), new JSONObject());
        if (res.getStatus() != 200) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(res.getStatus()), res.getMessage());
        }
        Map<String, Object> data = res.getData() instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
        log.info("[판독결과] 결과 다시 가져오기 완료 transId={} status={} engineStatus={} elapsedMs={}",
                transId, res.getStatus(), data.get("status"), System.currentTimeMillis() - started);
        return data;
    }

    /**
     * "원본 JSON" - 엔진 결과 조회 응답을 Agent 를 거쳐 그대로 받는다. 저장하지 않으며 본문은 로그에 남기지 않는다.
     * Agent 는 오류도 HTTP 200 본문의 status 로 돌려주므로, 200 이 아니면 ResponseStatusException 으로 올린다(컨트롤러가 처리).
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getRawResult(String transId) throws Exception {
        long started = System.currentTimeMillis();
        RestResponseEntity<?> res = agentProvider.get(String.format(resultRawUrl, transId));
        if (res.getStatus() != 200) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(res.getStatus()), res.getMessage());
        }
        Map<String, Object> data = res.getData() instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
        log.info("[판독결과] 원본 JSON 조회 완료 transId={} engineStatus={} elapsedMs={}",
                transId, data.get("status"), System.currentTimeMillis() - started);
        return data;
    }

    /** 조회 조건 선택칸에 보여 줄 업무 목록 */
    public List<OcrResultWkGroupVO> getWorkflowList() {
        return ocrReqMapper.selectWkGroupList(null);
    }

    /** 업무별 연결 서식. wkId 가 비면 전체 업무다. 판독결과 검토 목록의 "결과 없음" 건수 계산에 쓴다. */
    public Map<String, List<OcrResultLefrmVO>> linkedFormsByWorkflow(String wkId) {
        Map<String, List<OcrResultLefrmVO>> formsByWkId = new LinkedHashMap<>();
        for (OcrResultLefrmVO lefrm : ocrReqMapper.selectWkFormList(wkId)) {
            formsByWkId.computeIfAbsent(lefrm.getWkId(), id -> new ArrayList<>()).add(lefrm);
        }
        return formsByWkId;
    }

    /** 서식목록에 업무마다 보여 줄 최근 판독작업 건수 */
    static final int RECENT_TRANSACTIONS_PER_WORKFLOW = 3;

    /**
     * 서식판독결과 화면 서식목록 조회. wkId 가 없으면 전체 업무를 본다.
     * 업무·서식 설정은 Portal DB 에서, 판독작업·문서는 Agent API 에서 받아 서식과 문서를 맞춘다.
     * 업무마다 최근 스캔 판독작업 3건(transId 가 있으면 그 1건)을 판독작업 1건당 묶음 1개로 보여 준다.
     * 판독한 적 없는 업무는 숨긴다. 서식은 결과(lefrmList)·결과 없음(emptyLefrmList)·실패(failedLefrmList)로
     * 나눈다. 판독작업 자체가 실패하면 연결 서식을 모두 실패로 둔다.
     */
    public List<OcrResultWkGroupVO> getResultWkGroupList(String wkId, String transId) {
        // 업무 ID 는 엔진 형식(소문자 UUID)으로 저장된다. 단말 화면이 대문자로 넘겨도 같은 업무로 찾는다.
        wkId = wkId == null ? null : wkId.trim().toLowerCase(Locale.ROOT);
        boolean oneTransaction = transId != null && !transId.isBlank();
        Map<String, List<OcrAgentData.Transaction>> recent = new LinkedHashMap<>();
        for (OcrAgentData.Transaction transaction : agentData.latestTransactions(wkId, transId,
                oneTransaction ? 1 : RECENT_TRANSACTIONS_PER_WORKFLOW)) {
            recent.computeIfAbsent(transaction.getWorkflowId(), id -> new ArrayList<>()).add(transaction);
        }
        Map<String, List<OcrResultLefrmVO>> formsByWkId = new LinkedHashMap<>();
        for (OcrResultLefrmVO lefrm : ocrReqMapper.selectWkFormList(wkId)) {
            formsByWkId.computeIfAbsent(lefrm.getWkId(), id -> new ArrayList<>()).add(lefrm);
        }

        List<OcrResultWkGroupVO> groups = new ArrayList<>();
        int noResult = 0;
        int failedDocs = 0;
        for (OcrResultWkGroupVO workflow : ocrReqMapper.selectWkGroupList(wkId)) {
            // 판독한 적 없는 업무(동기화로만 생긴 워크플로)는 묶음을 만들지 않는다.
            for (OcrAgentData.Transaction transaction : recent.getOrDefault(workflow.getWkId(), List.of())) {
                OcrResultWkGroupVO group = workflow.forTransaction(
                        transaction.getTransactionId(), transaction.getStatus(), transaction.getScanDateTime());
                List<OcrResultLefrmVO> forms = new ArrayList<>();
                for (OcrResultLefrmVO lefrm : formsByWkId.getOrDefault(workflow.getWkId(), List.of()).stream()
                        .flatMap(linked -> documentRows(linked, transaction).stream()).toList()) {
                    forms.add(lefrm);
                    if (lefrm.isFailed()) {
                        group.getFailedLefrmList().add(lefrm);
                        failedDocs++;
                    } else if (lefrm.getDocId() != null) {
                        group.getLefrmList().add(lefrm);
                    } else if (group.isTransFailed()) {
                        group.getFailedLefrmList().add(lefrm);
                    } else if (!group.isPending()) {
                        // 판독이 끝났는데 이 서식의 문서가 없다. 대기 그룹은 상태 문구로 대신한다.
                        group.getEmptyLefrmList().add(lefrm);
                        noResult++;
                    }
                }
                for (OcrResultLefrmVO doc : unmatchedFailedDocs(transaction, forms)) {
                    group.getFailedLefrmList().add(doc);
                    failedDocs++;
                }
                groups.add(group);
            }
        }

        List<OcrResultWkGroupVO> shown = groups.stream()
                .filter(OcrResultWkGroupVO::isListed)
                .collect(Collectors.toList());
        log.debug("[판독결과] 서식목록 구성 wkId={} transId={} transactions={} shown={} formsWithoutResult={} failedDocs={}",
                wkId, transId, groups.size(), shown.size(), noResult, failedDocs);
        return shown;
    }

    /**
     * 서식목록 항목(업무에 연결된 서식) 1건의 판독 문서. 화면이 보낸 transId 가 있으면 그 판독작업, 없으면 업무의 최근 판독작업에서 찾는다.
     * 맞는 판독작업·문서가 없으면 null.
     */
    public OcrResultLefrmVO resolveDocument(long lefrmId, String wkId, String transId) {
        return resolveDocument(lefrmId, wkId, transId, null);
    }

    /** docId 가 있으면 첫 문서 대신 그 문서를 고른다. 같은 서식이 여러 건일 때 쓴다. 판독작업·서식에 속하지 않으면 null */
    public OcrResultLefrmVO resolveDocument(long lefrmId, String wkId, String transId, String docId) {
        OcrResultLefrmVO form = ocrReqMapper.selectWkForm(lefrmId, wkId);
        if (form == null) {
            log.debug("[판독결과] 업무에 연결되지 않은 서식 lefrmId={} wkId={}", lefrmId, wkId);
            return null;
        }
        bindDocument(form, latestByWorkflow(wkId, transId).get(wkId), docId);
        if (form.getTransId() == null || form.getDocId() == null) {
            log.debug("[판독결과] 매칭되는 판독 문서 없음 lefrmId={} wkId={} transId={} docId={}", lefrmId, wkId, transId, docId);
            return null;
        }
        return form;
    }

    /**
     * 판독작업의 서식별 문서(결과 파일에 넣을 서식 목록). 판독 문서가 없거나 실패·취소된 서식은 뺀다.
     */
    public List<OcrAgentData.Form> resultForms(String wkId, String transId) {
        log.debug("[판독결과] 결과 파일용 서식 목록 구성 wkId={} transId={}", wkId, transId);
        OcrAgentData.Transaction transaction = latestByWorkflow(wkId, transId).get(wkId);
        List<OcrAgentData.Form> forms = new ArrayList<>();
        for (OcrResultLefrmVO linked : ocrReqMapper.selectWkFormList(wkId)) {
            for (OcrResultLefrmVO lefrm : documentRows(linked, transaction)) {
                if (lefrm.getDocId() != null && !lefrm.isFailed()) {
                    forms.add(new OcrAgentData.Form(lefrm.getDocId(), lefrm.getLefrmId(), lefrm.getLefrmNm()));
                }
            }
        }
        return forms;
    }

    /**
     * 서식판독결과 화면 인식/확인 정보 - 서식목록 항목 1건에 대한 필드 목록.
     * 매칭되는 판독작업/문서가 없으면 빈 목록을 반환한다.
     */
    public List<OcrFldVO> getResultRecogFieldList(long lefrmId, String wkId, String transId, String docId) {
        OcrResultLefrmVO key = resolveDocument(lefrmId, wkId, transId, docId);
        if (key == null) {
            return new ArrayList<>();
        }
        List<OcrFldVO> fields = new ArrayList<>();
        List<OcrAgentData.Field> agentFields = agentData.fields(key.getTransId(), key.getDocId());
        log.debug("[판독결과] 인식 필드 조회 lefrmId={} wkId={} transId={} docId={} fieldCount={}", lefrmId, wkId, key.getTransId(), key.getDocId(), agentFields.size());
        for (OcrAgentData.Field field : agentFields) {
            OcrFldVO fld = new OcrFldVO();
            fld.setFildId(field.getFieldId());
            fld.setDocId(key.getDocId());
            fld.setGrpId(field.getGroupId());
            fld.setFildKey(field.getKey());
            fld.setFildVal(field.getValue());
            fld.setFildCd(field.getCode());
            fld.setTokenBbox(field.getBbox());
            fld.setCfdn(field.getConfidence());
            fld.setCnfmVal(field.getConfirmedValue());
            fld.setCnfmYn(field.isConfirmed() ? "Y" : "N");
            fields.add(fld);
        }
        return fields;
    }

    /**
     * 서식판독결과 화면 인식/확인 정보 - 표 목록(컬럼/행(셀) 구조).
     * 매칭되는 판독작업/문서가 없으면 빈 목록을 반환한다.
     */
    public List<OcrResultTblVO> getResultTblList(long lefrmId, String wkId, String transId, String docId) {
        OcrResultLefrmVO key = resolveDocument(lefrmId, wkId, transId, docId);
        if (key == null) {
            return new ArrayList<>();
        }
        List<OcrResultTblVO> result = new ArrayList<>();
        List<OcrAgentData.Table> agentTables = agentData.tables(key.getTransId(), key.getDocId());
        log.debug("[판독결과] 표 조회 lefrmId={} wkId={} transId={} docId={} tableCount={}", lefrmId, wkId, key.getTransId(), key.getDocId(), agentTables.size());
        for (OcrAgentData.Table table : agentTables) {
            OcrResultTblVO resultTbl = new OcrResultTblVO();
            resultTbl.setTblId(table.getTableId());
            resultTbl.setTblKey(table.getKey());
            List<OcrTblColVO> columns = new ArrayList<>();
            for (OcrAgentData.Column column : table.getColumns()) {
                OcrTblColVO col = new OcrTblColVO();
                col.setColId(column.getColumnId());
                col.setTblId(table.getTableId());
                col.setColSeq(column.getSeq());
                col.setColNm(column.getName());
                columns.add(col);
            }
            resultTbl.setColumns(columns);
            List<OcrResultTblRowVO> rows = new ArrayList<>();
            for (OcrAgentData.Row row : table.getRows()) {
                OcrResultTblRowVO resultRow = new OcrResultTblRowVO();
                resultRow.setRowSeq(row.getRowSeq());
                List<OcrTblCellVO> cells = new ArrayList<>();
                for (OcrAgentData.Cell cell : row.getCells()) {
                    OcrTblCellVO tblCell = new OcrTblCellVO();
                    tblCell.setCellId(cell.getCellId());
                    tblCell.setTblId(table.getTableId());
                    tblCell.setRowSeq(cell.getRowSeq());
                    tblCell.setColId(cell.getColumnId());
                    tblCell.setFildKey(cell.getKey());
                    tblCell.setFildVal(cell.getValue());
                    tblCell.setFildCd(cell.getCode());
                    tblCell.setTokenBbox(cell.getBbox());
                    tblCell.setCfdn(cell.getConfidence());
                    tblCell.setCnfmVal(cell.getConfirmedValue());
                    tblCell.setCnfmYn(cell.isConfirmed() ? "Y" : "N");
                    cells.add(tblCell);
                }
                resultRow.setCells(cells);
                rows.add(resultRow);
            }
            resultTbl.setRows(rows);
            result.add(resultTbl);
        }
        return result;
    }

    /** 업무별 최근 판독작업(Agent). transId 가 있으면 그 판독작업만 */
    private Map<String, OcrAgentData.Transaction> latestByWorkflow(String wkId, String transId) {
        Map<String, OcrAgentData.Transaction> latest = new LinkedHashMap<>();
        for (OcrAgentData.Transaction transaction : agentData.latestTransactions(wkId, transId)) {
            latest.putIfAbsent(transaction.getWorkflowId(), transaction);
        }
        return latest;
    }

    /** 서식에 판독작업과 첫 번째로 맞는 문서(순서, 문서 ID 순)를 채운다. 판독작업이 없으면 그대로 둔다. */
    static void bindDocument(OcrResultLefrmVO lefrm, OcrAgentData.Transaction transaction) {
        bindDocument(lefrm, transaction, null);
    }

    /**
     * 서식 1개를 서식목록 행으로 펼친다. 같은 서식이 여러 건이면 문서마다 한 행이고(docSeq/docTotal), 문서가 없으면 빈 행 하나다.
     */
    static List<OcrResultLefrmVO> documentRows(OcrResultLefrmVO linked, OcrAgentData.Transaction transaction) {
        List<OcrAgentData.Document> documents = transaction == null ? List.of()
                : transaction.getDocuments().stream().filter(document -> matches(document, linked)).toList();
        if (documents.isEmpty()) {
            OcrResultLefrmVO row = linked.linkOnly();
            bindDocument(row, transaction);
            return List.of(row);
        }
        List<OcrResultLefrmVO> rows = new ArrayList<>();
        for (OcrAgentData.Document document : documents) {
            OcrResultLefrmVO row = linked.linkOnly();
            bindDocument(row, transaction, document.getDocumentId());
            row.setDocSeq(rows.size() + 1);
            row.setDocTotal(documents.size());
            rows.add(row);
        }
        return rows;
    }

    private static void bindDocument(OcrResultLefrmVO lefrm, OcrAgentData.Transaction transaction, String docId) {
        if (transaction == null) {
            return;
        }
        lefrm.setTransId(transaction.getTransactionId());
        for (OcrAgentData.Document document : transaction.getDocuments()) {
            if (matches(document, lefrm) && (docId == null || docId.isBlank() || docId.equals(document.getDocumentId()))) {
                lefrm.setDocId(document.getDocumentId());
                lefrm.setDocStatCd(document.getStatus());
                lefrm.setRedactYn(document.getRedactYn());
                lefrm.setFileNm(document.getFileName());
                lefrm.setFileExt(document.getFileExtension());
                lefrm.setOrgFileNm(document.getOriginalFileName());
                lefrm.setDocRole(document.getRole());
                lefrm.setDocRoleSrc(document.getRoleSource());
                return;
            }
        }
    }

    /** OCR 엔진(솔루션)이 문서 종류 영문코드 앞에 붙이는 접두어. 은행 서식 DOC_ID 에는 없다(예: KJB_50621004 ↔ 50621004). */
    static final String ENGINE_CODE_PREFIX = "KJB_";

    /**
     * 판독 문서와 서식의 연결. 문서 종류 영문코드가 서식 DOC_ID(formId)와 같을 때만 맞춘다.
     * 정합성 확인을 위해 이름으로는 맞추지 않는다. 코드가 없거나 다르면 연결하지 않아 "결과 없음"·미연결로 드러난다.
     */
    static boolean matches(OcrAgentData.Document document, OcrResultLefrmVO lefrm) {
        return sameFormCode(document.getTypeCode(), lefrm.getFormId());
    }

    /**
     * 엔진 영문코드와 서식 DOC_ID 가 같은 서식인가. 엔진 접두어(KJB_)는 있어도 없어도 된다.
     * 워크플로 동기화(AgentApiService)·업무·서식 연결 SQL(FormLinkMapper.lefrmMatchesClass)·Agent 서식간 검증(CrossRuleJudge.sameFormCode)과 같은 규칙이다.
     */
    public static boolean sameFormCode(String engineCode, String formDocId) {
        if (engineCode == null || engineCode.isBlank() || formDocId == null || formDocId.isBlank()) {
            return false;
        }
        return engineCode.equals(formDocId) || engineCode.equals(ENGINE_CODE_PREFIX + formDocId);
    }

    /**
     * 실패 묶음: 판독작업의 실패·취소 문서 중 업무에 연결된 어떤 서식과도 맞지 않는 문서(분류 단계 실패 등).
     * 서식에 맞는 실패 문서는 서식 행에 docStatCd 와 함께 들어간다.
     */
    static List<OcrResultLefrmVO> unmatchedFailedDocs(OcrAgentData.Transaction transaction, List<OcrResultLefrmVO> forms) {
        List<OcrResultLefrmVO> docs = new ArrayList<>();
        if (transaction == null) {
            return docs;
        }
        for (OcrAgentData.Document document : transaction.getDocuments()) {
            if (!OcrResultLefrmVO.isFailedStatus(document.getStatus())
                    || forms.stream().anyMatch(form -> matches(document, form))) {
                continue;
            }
            OcrResultLefrmVO doc = new OcrResultLefrmVO();
            doc.setWkId(transaction.getWorkflowId());
            doc.setTransId(transaction.getTransactionId());
            doc.setDocId(document.getDocumentId());
            doc.setDocStatCd(document.getStatus());
            doc.setLefrmNm(document.getTypeName());
            doc.setOrgFileNm(document.getOriginalFileName());
            doc.setFileExt(document.getFileExtension());
            docs.add(doc);
        }
        return docs;
    }

    private static RestTemplate terminalRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(10_000);
        return new RestTemplate(factory);
    }

    /**
     * 검증완료한 서식 1건의 확인값을 단말시스템(C#) API 에 JSON 으로 전송한다.
     * 전송은 부가 연계라 실패해도 검증완료를 되돌리지 않고 WARN 만 남긴다(URL 미설정 포함).
     * 요청 형식(식별자 + 확인값)은 단말 연계 규격·매핑코드 확정 전 임시 형식이다.
     */
    public void sendVerifyResultToTerminal(String wkId, long lefrmId, String transId, String docId, String verifierId,
                                           OcrVerifyRequestVO request) {
        if (terminalVerifyUrl == null || terminalVerifyUrl.isBlank()) {
            log.warn("[단말전송] 건너뜀, aiocr.terminal.verify-url 미설정 wkId={} lefrmId={} transId={}",
                    wkId, lefrmId, transId);
            return;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("wkId", wkId);
        body.put("lefrmId", lefrmId);
        body.put("transId", transId);
        body.put("docId", docId);
        body.put("verifierId", verifierId);
        body.put("fields", request.getFields());
        body.put("tables", request.getTables());
        long started = System.currentTimeMillis();
        try {
            restTemplate.postForEntity(terminalVerifyUrl, body, Void.class);
            log.info("[단말전송] 검증완료 결과 전송 wkId={} lefrmId={} transId={} elapsedMs={}",
                    wkId, lefrmId, transId, System.currentTimeMillis() - started);
        } catch (RestClientException e) {
            log.warn("[단말전송] 전송 실패, 검증완료는 유지 wkId={} lefrmId={} transId={} elapsedMs={}",
                    wkId, lefrmId, transId, System.currentTimeMillis() - started, e);
        }
    }
}
