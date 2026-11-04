package com.agilesoda.module.ocr.service;

import com.agilesoda.module.admin.vo.SsoLoginUserVO;
import com.agilesoda.module.apply.mapper.CrossRuleMapper;
import com.agilesoda.module.apply.vo.OcrVerifyCellVO;
import com.agilesoda.module.ocr.mapper.OcrReviewMapper;
import com.agilesoda.module.ocr.vo.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.function.Supplier;

/**
 * 판독결과 검토: 검증 담당자 지정, 확인값 저장, 검증완료 상태와 그 취소.
 * 검토 상태·확인값·결과 파일은 Agent 가 소유한다(규칙 확인과 저장을 Agent 가 한 트랜잭션에서 한다).
 * Portal 은 자기 데이터인 로그인 사용자·지정 권한(관리자·팀장)과 지정 대상 직원 확인, 이름 표시를 맡는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OcrReviewService {

    /** 판독작업 전체 담당자를 나타내는 문서 ID */
    public static final String TRANS_LEVEL_DOC_ID = "*";

    private final OcrReviewMapper reviewMapper;
    private final OcrReqService ocrReqService;
    private final OcrAgentDataClient agentData;
    private final CrossRuleMapper crossRuleMapper;

    /** 담당자를 지정할 수 있는 역할(OCUOC_USROL_R.ROLE_ID). 환경 변수 AIOCR_REVIEW_ASSIGNER_ROLES */
    @Value("${aiocr.review.assigner-roles:ROLE_ADMIN}")
    private List<String> assignerRoles = List.of("ROLE_ADMIN");

    /** 담당자를 지정할 수 있는 직위 코드(OCUOC_USERI_M.HRA_PSTN_CD, 팀장). 환경 변수 AIOCR_REVIEW_ASSIGNER_POSITION_CODES */
    @Value("${aiocr.review.assigner-position-codes:P02}")
    private List<String> assignerPositionCodes = List.of("P02");

    /** 로그인 사용자와 담당자 지정 권한. 로그인 정보가 없으면 null */
    public OcrReviewerVO reviewer(SsoLoginUserVO loginUser) {
        if (loginUser == null || loginUser.getUserId() == null || loginUser.getUserId().isBlank()) {
            return null;
        }
        OcrReviewerVO reviewer = new OcrReviewerVO();
        reviewer.setUserId(loginUser.getUserId());
        reviewer.setUserNm(loginUser.getUserName());
        reviewer.setCanAssign(reviewMapper.countAssignAuthority(loginUser.getUserId(),
                clean(assignerRoles), clean(assignerPositionCodes)) > 0);
        log.debug("[검토] 사용자 권한 확인 userId={} canAssign={}", reviewer.getUserId(), reviewer.isCanAssign());
        return reviewer;
    }

    /** 서식목록에 판독작업 담당자와 문서별 검토 상태를 붙인다(판독작업 수와 무관하게 Agent 호출 1회). */
    public void applyReviewStates(List<OcrResultWkGroupVO> groups) {
        Set<OcrAgentData.DocumentKey> keys = new LinkedHashSet<>();
        for (OcrResultWkGroupVO group : groups) {
            if (group.getTransId() != null) keys.add(new OcrAgentData.DocumentKey(group.getTransId(), TRANS_LEVEL_DOC_ID));
            for (OcrResultLefrmVO lefrm : group.getLefrmList()) {
                if (lefrm.getTransId() != null && lefrm.getDocId() != null) {
                    keys.add(new OcrAgentData.DocumentKey(lefrm.getTransId(), lefrm.getDocId()));
                }
            }
        }
        if (keys.isEmpty()) {
            return;
        }
        Map<OcrAgentData.DocumentKey, OcrReviewStateVO> states = toStates(agentData.reviewStates(new ArrayList<>(keys)));
        for (OcrResultWkGroupVO group : groups) {
            OcrReviewStateVO transState = states.get(new OcrAgentData.DocumentKey(group.getTransId(), TRANS_LEVEL_DOC_ID));
            if (transState != null) {
                group.setTransAsgnUserId(transState.getTransAsgnUserId());
                group.setTransAsgnUserNm(transState.getTransAsgnUserNm());
            }
            for (OcrResultLefrmVO lefrm : group.getLefrmList()) {
                if (lefrm.getTransId() == null || lefrm.getDocId() == null) continue;
                lefrm.setReview(states.get(new OcrAgentData.DocumentKey(lefrm.getTransId(), lefrm.getDocId())));
            }
        }
        log.debug("[검토] 문서 검토 상태 반영 groups={} documents={}", groups.size(), keys.size());
    }

    /** 선택한 서식 문서 1건의 검토 상태 */
    public OcrReviewStateVO documentState(String wkId, long lefrmId, String transId, String docId) {
        OcrResultLefrmVO key = resolveDocument(wkId, lefrmId, transId, docId);
        OcrAgentData.DocumentKey documentKey = new OcrAgentData.DocumentKey(key.getTransId(), key.getDocId());
        return agent(() -> toStates(agentData.reviewStates(List.of(documentKey))).get(documentKey));
    }

    /**
     * 판독작업 담당자 지정. 그 판독작업의 모든 서식에 적용된다. targetUserId 가 비면 해제한다.
     */
    public OcrReviewStateVO assignTransaction(String wkId, String transId, String targetUserId, OcrReviewerVO reviewer) {
        requireReviewer(reviewer);
        String target = blankToNull(targetUserId);
        requireUser(target);
        log.debug("[검토] 판독작업 담당자 지정 wkId={} transId={} by={} target={}", wkId, transId, reviewer.getUserId(), target);
        return agent(() -> toState(agentData.assignTransaction(wkId, transId,
                new OcrAgentData.AssignRequest(actor(reviewer), target))));
    }

    /**
     * 판독결과 검토 목록. Agent 의 판독작업 요약에 Portal 데이터(담당자 이름, 결과 없음 서식 수)를 붙인다.
     *
     * @param transId 있으면 그 판독작업 1건만 찾고 나머지 조건은 무시한다(상세 요약줄용)
     * @param from    스캔일 yyyyMMdd. transId 가 없으면 필수다
     * @param to      스캔일 yyyyMMdd. transId 가 없으면 필수다
     * @return 최근 스캔 순 최대 500건
     * @throws ResponseStatusException Agent 가 거절하면 그 상태와 안내 문구
     */
    public List<OcrReviewTransactionVO> reviewTransactions(String wkId, String transId, String assigneeId,
                                                           String reviewStatus, String from, String to) {
        // 업무 ID 는 엔진 형식(소문자 UUID)으로 저장된다. getResultWkGroupList 와 같은 처리다.
        String workflowId = blankToNull(wkId) == null ? null : wkId.trim().toLowerCase(Locale.ROOT);
        List<OcrAgentData.ReviewTransaction> rows =
                agentData.reviewTransactions(workflowId, transId, assigneeId, reviewStatus, from, to);
        Set<String> userIds = new LinkedHashSet<>();
        for (OcrAgentData.ReviewTransaction row : rows) {
            if (row.getAssigneeId() != null) userIds.add(row.getAssigneeId());
        }
        Map<String, String> names = new HashMap<>();
        if (!userIds.isEmpty()) {
            for (OcrReviewerVO user : reviewMapper.selectUserNmList(userIds)) names.put(user.getUserId(), user.getUserNm());
        }
        Map<String, List<OcrResultLefrmVO>> formsByWkId = ocrReqService.linkedFormsByWorkflow(workflowId);
        List<OcrReviewTransactionVO> result = new ArrayList<>();
        for (OcrAgentData.ReviewTransaction row : rows) {
            OcrReviewTransactionVO vo = new OcrReviewTransactionVO();
            vo.setTransId(row.getTransactionId());
            vo.setWkId(row.getWorkflowId());
            vo.setWkNm(row.getWorkflowName());
            vo.setScanDt(row.getScanDateTime());
            vo.setRsltCd(row.getStatus());
            vo.setAsgnUserId(row.getAssigneeId());
            vo.setAsgnUserNm(names.get(row.getAssigneeId()));
            vo.setReviewStatus(row.getReviewStatus());
            vo.setFormCount(row.getDocumentCount() - row.getFailedCount());
            vo.setVerifiedCount(row.getVerifiedCount());
            vo.setFailedCount(row.getFailedCount());
            vo.setMismatchCount(row.getMismatchCount());
            vo.setUnverifiableCount(row.getUnverifiableCount());
            vo.setEmptyCount(emptyFormCount(row, formsByWkId.getOrDefault(row.getWorkflowId(), List.of())));
            result.add(vo);
        }
        return result;
    }

    /**
     * 업무에 연결됐는데 맞는 판독 문서가 없는 서식 수. 판독중·판독 실패는 문서가 아직·아예 없을 뿐이라
     * "결과 없음"으로 세지 않는다(OcrReqService.getResultWkGroupList 의 분류와 같다).
     */
    static int emptyFormCount(OcrAgentData.ReviewTransaction row, List<OcrResultLefrmVO> forms) {
        if ("PROCESSING".equals(row.getReviewStatus()) || "failed".equals(row.getStatus()) || "canceled".equals(row.getStatus())) {
            return 0;
        }
        int empty = 0;
        for (OcrResultLefrmVO form : forms) {
            if (row.getDocuments().stream().noneMatch(document -> OcrReqService.matches(document, form))) empty++;
        }
        return empty;
    }

    /** 확인값 저장(임시저장). 담당자만 할 수 있다. */
    public OcrReviewStateVO saveConfirmValues(String wkId, long lefrmId, String transId, String docId, OcrVerifyRequestVO request,
                                              OcrReviewerVO reviewer) {
        requireReviewer(reviewer);
        OcrResultLefrmVO key = resolveDocument(wkId, lefrmId, transId, docId);
        List<OcrAgentData.ItemValue> fields = fieldValues(request);
        List<OcrAgentData.ItemValue> cells = cellValues(request);
        log.debug("[검토] 확인값 임시저장 요청 wkId={} transId={} docId={} by={} fieldCount={} cellCount={}",
                wkId, key.getTransId(), key.getDocId(), reviewer.getUserId(), fields.size(), cells.size());
        OcrReviewStateVO state = agent(() -> toState(agentData.saveValues(key.getTransId(), key.getDocId(),
                new OcrAgentData.ValuesRequest(actor(reviewer), fields, cells))));
        log.info("[검토] 확인값 임시저장 완료 transId={} docId={} by={} verifyStatus={}", key.getTransId(), key.getDocId(),
                reviewer.getUserId(), state.getVrfyStatCd());
        return state;
    }

    /**
     * 검증완료: Agent 가 확인값 저장·검증완료 상태·결과 파일(result.json)을 한 번에 처리한다.
     * 결과 파일에 넣을 서식(Portal 데이터)과 문서의 연결을 함께 넘긴다.
     */
    public OcrReviewStateVO verify(String wkId, long lefrmId, String transId, String docId, OcrVerifyRequestVO request,
                                   OcrReviewerVO reviewer) {
        requireReviewer(reviewer);
        OcrResultLefrmVO key = resolveDocument(wkId, lefrmId, transId, docId);
        List<OcrAgentData.Form> forms = ocrReqService.resultForms(wkId, key.getTransId());
        List<OcrAgentData.ItemValue> fields = fieldValues(request);
        List<OcrAgentData.ItemValue> cells = cellValues(request);
        log.debug("[검토] 검증완료 요청 wkId={} transId={} docId={} by={} fieldCount={} cellCount={} forms={}",
                wkId, key.getTransId(), key.getDocId(), reviewer.getUserId(), fields.size(), cells.size(), forms.size());
        OcrReviewStateVO state = agent(() -> toState(agentData.verify(key.getTransId(), key.getDocId(),
                new OcrAgentData.VerifyRequest(actor(reviewer), wkId, fields, cells, forms))));
        log.info("[검토] 검증완료 확정 wkId={} transId={} docId={} by={} forms={} verifyStatus={}", wkId, key.getTransId(), key.getDocId(),
                reviewer.getUserId(), forms.size(), state.getVrfyStatCd());
        // Agent 가 검증완료를 확정한 뒤에만 보낸다. 거절·실패는 위에서 예외로 끝난다.
        ocrReqService.sendVerifyResultToTerminal(wkId, lefrmId, key.getTransId(), key.getDocId(), reviewer.getUserId(), request);
        return state;
    }

    /** 문서 역할 지정(담당자만, 판단은 Agent). 역할을 비우면 지정을 지워 자동 구분으로 돌아간다. */
    public OcrReviewStateVO setRole(String wkId, long lefrmId, String transId, String docId, String role, OcrReviewerVO reviewer) {
        requireReviewer(reviewer);
        OcrResultLefrmVO key = resolveDocument(wkId, lefrmId, transId, docId);
        String name = blankToNull(role);
        if (name != null && crossRuleMapper.selectRoles(wkId).stream()
                .noneMatch(setting -> setting.getFormId() == lefrmId && setting.getRoleNm().equals(name.trim()))) {
            throw new OcrReviewException(HttpStatus.BAD_REQUEST, "서식에 없는 역할입니다. 목록에서 골라 주세요.");
        }
        log.debug("[검토] 문서 역할 지정 transId={} docId={} by={}", key.getTransId(), key.getDocId(), reviewer.getUserId());
        return agent(() -> toState(agentData.setRole(key.getTransId(), key.getDocId(),
                new OcrAgentData.RoleRequest(actor(reviewer), blankToNull(role)))));
    }

    /**
     * 검증완료 취소(재검토): 검증한 사람이나 관리자·팀장만 한다(판단은 Agent). 확인값은 그대로 남는다.
     * 단말로 이미 보낸 값의 취소 전송은 하지 않는다(단말 규격 미정). 다시 검증완료하면 새로 보낸다.
     */
    public OcrReviewStateVO cancelVerify(String wkId, long lefrmId, String transId, String docId, OcrReviewerVO reviewer) {
        requireReviewer(reviewer);
        OcrResultLefrmVO key = resolveDocument(wkId, lefrmId, transId, docId);
        OcrReviewStateVO state = agent(() -> toState(agentData.cancelVerify(key.getTransId(), key.getDocId(),
                new OcrAgentData.CancelVerifyRequest(actor(reviewer)))));
        log.info("[검토] 검증완료 취소 wkId={} transId={} docId={} by={} verifyStatus={}", wkId, key.getTransId(), key.getDocId(), reviewer.getUserId(), state.getVrfyStatCd());
        return state;
    }

    /** Agent 의 업무 거절(4xx)은 그 안내 문구로 사용자에게 돌려준다. 그 밖의 실패는 그대로 올린다. */
    private static OcrReviewStateVO agent(Supplier<OcrReviewStateVO> call) {
        try {
            return call.get();
        } catch (ResponseStatusException e) {
            if (e.getStatusCode().is4xxClientError()) {
                throw new OcrReviewException(HttpStatus.valueOf(e.getStatusCode().value()), e.getReason(), e);
            }
            throw e;
        }
    }

    private static OcrAgentData.Actor actor(OcrReviewerVO reviewer) {
        return new OcrAgentData.Actor(reviewer.getUserId(), reviewer.isCanAssign());
    }

    private static List<OcrAgentData.ItemValue> fieldValues(OcrVerifyRequestVO request) {
        List<OcrAgentData.ItemValue> values = new ArrayList<>();
        if (request != null && request.getFields() != null) {
            for (OcrVerifyFieldVO field : request.getFields()) values.add(new OcrAgentData.ItemValue(field.getFildId(), field.getFildVal()));
        }
        return values;
    }

    private static List<OcrAgentData.ItemValue> cellValues(OcrVerifyRequestVO request) {
        List<OcrAgentData.ItemValue> values = new ArrayList<>();
        if (request != null && request.getTables() != null) {
            for (OcrVerifyCellVO cell : request.getTables()) values.add(new OcrAgentData.ItemValue(cell.getCellId(), cell.getFildVal()));
        }
        return values;
    }

    private OcrReviewStateVO toState(OcrAgentData.ReviewState state) {
        return toStates(List.of(state)).values().iterator().next();
    }

    /** Agent 검토 상태를 화면 형식으로 바꾸고 담당자·검증자·저장자 이름(Portal 사용자 정보)을 채운다. */
    private Map<OcrAgentData.DocumentKey, OcrReviewStateVO> toStates(List<OcrAgentData.ReviewState> states) {
        Set<String> userIds = new LinkedHashSet<>();
        for (OcrAgentData.ReviewState state : states) {
            Collections.addAll(userIds, state.getTransactionAssigneeId(),
                    state.getVerifierId(), state.getSaverId());
        }
        userIds.remove(null);
        Map<String, String> names = new HashMap<>();
        if (!userIds.isEmpty()) {
            for (OcrReviewerVO user : reviewMapper.selectUserNmList(userIds)) names.put(user.getUserId(), user.getUserNm());
        }
        Map<OcrAgentData.DocumentKey, OcrReviewStateVO> result = new LinkedHashMap<>();
        for (OcrAgentData.ReviewState state : states) {
            OcrReviewStateVO vo = new OcrReviewStateVO();
            vo.setTransId(state.getTransactionId());
            vo.setDocId(state.getDocumentId());
            vo.setTransAsgnUserId(state.getTransactionAssigneeId());
            vo.setTransAsgnUserNm(names.get(state.getTransactionAssigneeId()));
            vo.setVrfyStatCd(state.getVerifyStatus());
            vo.setVrfyUserId(state.getVerifierId());
            vo.setVrfyUserNm(names.get(state.getVerifierId()));
            vo.setVrfyDt(state.getVerifiedAt());
            vo.setSaveUserNm(names.get(state.getSaverId()));
            vo.setSaveDt(state.getSavedAt());
            result.put(new OcrAgentData.DocumentKey(state.getTransactionId(), state.getDocumentId()), vo);
        }
        return result;
    }

    /** 화면이 보낸 (wkId, lefrmId, transId)로 판독 문서를 서버에서 찾는다. 실패·취소 문서는 검토 대상이 아니다. */
    private OcrResultLefrmVO resolveDocument(String wkId, long lefrmId, String transId, String docId) {
        OcrResultLefrmVO key = ocrReqService.resolveDocument(lefrmId, wkId, transId, docId);
        if (key == null) {
            throw new OcrReviewException(HttpStatus.NOT_FOUND, "판독 문서를 찾을 수 없습니다.");
        }
        if (key.isFailed()) {
            throw new OcrReviewException(HttpStatus.CONFLICT, "실패·취소된 문서는 검토할 수 없습니다.");
        }
        return key;
    }

    private void requireReviewer(OcrReviewerVO reviewer) {
        if (reviewer == null) {
            throw new OcrReviewException(HttpStatus.UNAUTHORIZED, "로그인 정보가 없습니다. 다시 로그인해 주세요.");
        }
    }

    /** 지정 대상 직원 확인(사용자 정보는 Portal 데이터라 Agent 대신 여기서 한다) */
    private void requireUser(String userId) {
        if (userId != null && reviewMapper.selectUserNm(userId) == null) {
            throw new OcrReviewException(HttpStatus.BAD_REQUEST, "직원 정보를 찾을 수 없습니다.");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static List<String> clean(List<String> values) {
        List<String> result = new ArrayList<>();
        if (values != null) {
            for (String value : values) {
                if (value != null && !value.isBlank()) result.add(value.trim());
            }
        }
        return result;
    }
}
