package com.agilesoda.module.apply.service;

import com.agilesoda.module.apply.mapper.CrossRuleMapper;
import com.agilesoda.module.apply.vo.CrossRoleVO;
import com.agilesoda.module.apply.vo.CrossRuleVO;
import com.agilesoda.module.ocr.mapper.OcrReqMapper;
import com.agilesoda.module.ocr.service.OcrAgentDataClient;
import com.agilesoda.module.ocr.service.OcrReqService;
import com.agilesoda.module.ocr.vo.OcrAgentData;
import com.agilesoda.module.ocr.vo.OcrResultLefrmVO;
import com.agilesoda.module.ocr.vo.OcrResultWkGroupVO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * 서식간 규칙 관리. 규칙 정의를 JSON 으로 버전마다 저장(OCUOC_BZCRV_L)하고, 화면 미리보기에 쓸 항목과 판독값을 모아 준다.
 * 판정은 Agent 가 한다. Agent 는 판정할 때 이 표를 직접 읽고, 화면 미리보기는 저장 전 정의를 Agent 에 보내 묻는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CrossRuleDefService {

    static final int SCHEMA_VERSION = 1;
    /** 미리보기에 불러올 최근 판독작업 수 */
    private static final int RECENT_TRANSACTIONS = 5;
    private static final int MAX_JUDGE_RULES = 200;
    private static final int NAME_MAX_BYTES = 100;
    private static final int DEF_MAX_CHARS = 100_000;
    private static final Set<String> OPERATORS = Set.of("EQ", "NE", "CONTAINS", "NOT_CONTAINS", "IN_LIST", "NOT_IN_LIST",
            "GT", "GE", "LT", "LE", "BETWEEN", "EMPTY", "NOT_EMPTY", "MATCHES");
    /** 오른쪽 값 없이 왼쪽 값이 있는지만 보는 연산자 */
    private static final Set<String> UNARY_OPERATORS = Set.of("EMPTY", "NOT_EMPTY");
    /** 글자에는 크기가 없고 날짜·숫자에는 포함 관계와 정규식이 없다. 뜻이 없는 조합은 다른 비교로 동작하므로 거절한다. 값 있음·없음은 형식과 무관하다 */
    private static final Set<String> TEXT_OPERATORS = Set.of("EQ", "NE", "CONTAINS", "NOT_CONTAINS", "IN_LIST", "NOT_IN_LIST", "MATCHES", "EMPTY", "NOT_EMPTY");
    private static final Set<String> ORDERED_OPERATORS = Set.of("EQ", "NE", "IN_LIST", "NOT_IN_LIST", "GT", "GE", "LT", "LE", "BETWEEN", "EMPTY", "NOT_EMPTY");
    private static final Set<String> NEGATIVE_OPERATORS = Set.of("NE", "NOT_CONTAINS", "NOT_IN_LIST");
    private static final Set<String> COMPARE_AS = Set.of("TEXT", "DATE", "NUMBER");
    private static final Set<String> KINDS = Set.of("item", "union", "text", "number", "baseDate", "docCount", "calc", "aggregate", "slice");
    private static final Set<String> WHERE_OPERATORS = Set.of("EQ", "NE", "CONTAINS", "IN");
    private static final Set<String> AGGREGATE_FUNCTIONS = Set.of("SUM", "COUNT", "MAX", "MIN");
    private static final Set<String> AGGREGATE_SCOPES = Set.of("DOCUMENT", "ALL");
    private static final Set<String> SORT_ORDERS = Set.of("ASC", "DESC");
    private static final Set<String> ORDERED_TYPES = Set.of("NUMBER", "DATE");
    /** 정규식 길이 상한. Agent CrossRuleDefinition 의 상한과 같다 */
    private static final int MAX_PATTERN_LENGTH = 200;
    private static final Set<String> CALC_OPERATORS = Set.of("ADD", "SUB", "MUL", "DIV");
    /** 빈 값은 안쪽 계산이다. 화면은 마지막 계산에만 처리 방법을 적는다 */
    private static final Set<String> ROUNDS = Set.of("", "NONE", "ROUND", "FLOOR");
    private static final Set<String> NORMALIZE = Set.of("IGNORE_SPACE", "IGNORE_HYPHEN", "IGNORE_CASE", "IGNORE_CORP");
    private static final String SELF = "$self";
    /** PRD: 계산은 3단까지 */
    private static final int CALC_MAX_DEPTH = 3;
    /** 통과 방법 수. 화면 탭으로 다룰 수 있는 범위로 2~5개만 받는다 */
    private static final int MIN_METHODS = 2;
    private static final int MAX_METHODS = 5;
    private static final Set<String> MATCHES = Set.of("ANY", "ALL", "ONE", "NONE");
    private static final Set<String> ROLE_METHODS = Set.of("CUSTOMER_NAME", "REMAINDER", "VALUE_CONTAINS", "MANUAL");
    private static final Set<String> ROLE_METHODS_WITH_KEY = Set.of("CUSTOMER_NAME", "VALUE_CONTAINS");

    private final CrossRuleMapper mapper;
    private final OcrReqMapper ocrReqMapper;
    private final OcrAgentDataClient agentData;
    private final ObjectMapper json = new ObjectMapper();

    public List<OcrResultWkGroupVO> workflows() {
        return ocrReqMapper.selectWkGroupList(null);
    }

    public List<CrossRuleVO> list(String wkId) {
        return mapper.selectDefRuleList(wkId);
    }

    /** 이 서식에 설정한 역할 이름 */
    public List<String> roleOptions(String wkId, long formId) {
        return mapper.selectRoles(wkId).stream().filter(role -> role.getFormId() == formId).map(CrossRoleVO::getRoleNm).toList();
    }

    public List<CrossRoleVO> roles(String wkId) {
        return mapper.selectRoles(wkId);
    }

    /** 역할 설정을 저장한다. 고객명 비교와 나머지는 서식마다 하나만 쓸 수 있다 */
    @Transactional
    public void saveRole(CrossRoleVO role, String userId) {
        if (role == null || role.getWkId() == null || role.getFormId() == null) {
            throw invalid("업무와 서식을 선택해 주세요.");
        }
        if (!linkedForms(role.getWkId()).containsKey(role.getFormId())) {
            throw invalid("업무에 연결된 서식만 선택할 수 있습니다.");
        }
        role.setRoleNm(role.getRoleNm() == null ? "" : role.getRoleNm().trim());
        if (role.getRoleNm().isEmpty() || bytes(role.getRoleNm()) > NAME_MAX_BYTES) {
            throw invalid("역할 이름을 입력해 주세요(한글 33자 이내).");
        }
        if (!ROLE_METHODS.contains(String.valueOf(role.getMethodCd()))) {
            throw invalid("구분 방식이 올바르지 않습니다.");
        }
        boolean usesKey = ROLE_METHODS_WITH_KEY.contains(role.getMethodCd());
        String key = role.getFieldKey() == null ? "" : role.getFieldKey().trim();
        if (usesKey && (key.isEmpty() || bytes(key) > NAME_MAX_BYTES)) {
            throw invalid("구분에 쓸 항목을 선택해 주세요.");
        }
        role.setFieldKey(usesKey ? key : null);
        List<CrossRoleVO> others = mapper.selectRoles(role.getWkId()).stream()
                .filter(other -> other.getFormId().equals(role.getFormId()) && !other.getRoleNm().equals(role.getRoleNm())).toList();
        if (Set.of("CUSTOMER_NAME", "REMAINDER").contains(role.getMethodCd())
                && others.stream().anyMatch(other -> other.getMethodCd().equals(role.getMethodCd()))) {
            throw invalid("이 구분 방식은 서식마다 하나만 쓸 수 있습니다.");
        }
        if ("REMAINDER".equals(role.getMethodCd()) && others.isEmpty()) {
            throw invalid("나머지 역할은 다른 역할을 먼저 등록해야 합니다.");
        }
        mapper.mergeRole(role, userId);
        log.info("[서식간규칙관리] 역할 저장 wkId={} formId={} role={} method={} userId={}", role.getWkId(), role.getFormId(), role.getRoleNm(), role.getMethodCd(), userId);
    }

    /** 규칙이 쓰는 역할은 지우지 못한다. 꺼 둔 규칙도 다시 켤 수 있으므로 함께 본다 */
    @Transactional
    public void deleteRole(String wkId, long formId, String roleNm, String userId) {
        for (CrossRuleVO rule : mapper.selectDefRuleList(wkId)) {
            for (JsonNode source : parse(rule.getDefJson()).path("sources")) {
                if (sourceId(source) == formId && roleNm.equals(source.path("role").asText(""))) {
                    throw new CrossRuleException(HttpStatus.CONFLICT, "\"" + rule.getRuleNm() + "\" 규칙이 쓰는 역할은 지울 수 없습니다.");
                }
            }
        }
        List<CrossRoleVO> others = mapper.selectRoles(wkId).stream()
                .filter(role -> role.getFormId() == formId && !role.getRoleNm().equals(roleNm)).toList();
        if (others.size() == 1 && "REMAINDER".equals(others.get(0).getMethodCd())) {
            throw new CrossRuleException(HttpStatus.CONFLICT, "나머지 역할이 남아 있어 지울 수 없습니다. 나머지 역할을 먼저 지워 주세요.");
        }
        mapper.deleteRole(wkId, formId, roleNm);
        log.info("[서식간규칙관리] 역할 삭제 wkId={} formId={} role={} userId={}", wkId, formId, roleNm, userId);
    }

    /** 업무의 서식과 최근 판독작업. 항목 목록은 판독 결과에 실제로 나온 키로 만든다 */
    public Map<String, Object> context(String wkId) {
        List<OcrResultLefrmVO> forms = ocrReqMapper.selectWkFormList(wkId);
        Map<String, Set<String>> keysByCode = new HashMap<>();
        List<Map<String, Object>> transactions = new ArrayList<>();
        for (OcrAgentData.Transaction transaction : agentData.latestTransactions(wkId, null, RECENT_TRANSACTIONS)) {
            List<Map<String, Object>> documents = new ArrayList<>();
            for (OcrAgentData.Document document : transaction.getDocuments()) {
                List<Map<String, Object>> values = values(transaction.getTransactionId(), document.getDocumentId());
                Set<String> keys = keysByCode.computeIfAbsent(String.valueOf(document.getTypeCode()), code -> new LinkedHashSet<>());
                values.forEach(value -> keys.add((String) value.get("key")));
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("documentId", document.getDocumentId());
                row.put("formCode", document.getTypeCode());
                row.put("status", document.getStatus());
                row.put("role", document.getRole());
                documents.add(row);
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("transactionId", transaction.getTransactionId());
            row.put("scanDateTime", transaction.getScanDateTime());
            row.put("documents", documents);
            transactions.add(row);
        }
        List<Map<String, Object>> formRows = new ArrayList<>();
        for (OcrResultLefrmVO form : forms) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("formId", form.getLefrmId());
            row.put("formCode", form.getFormId());
            row.put("formName", form.getLefrmNm());
            row.put("keys", keysByCode.entrySet().stream()
                    .filter(entry -> OcrReqService.sameFormCode(entry.getKey(), form.getFormId()))
                    .map(Map.Entry::getValue).findFirst().orElse(Set.of()));
            formRows.add(row);
        }
        log.debug("[서식간규칙관리] 문맥 조회 wkId={} forms={} transactions={}", wkId, formRows.size(), transactions.size());
        return Map.of("forms", formRows, "transactions", transactions, "roles", mapper.selectRoles(wkId));
    }

    /** 일반 필드는 행 없음, 그룹은 GRP_ID, 표는 ROW_SEQ 를 한 행으로 본다. 확인값이 있으면 확인값을 쓴다 */
    private List<Map<String, Object>> values(String transId, String docId) {
        List<Map<String, Object>> values = new ArrayList<>();
        for (OcrAgentData.Field field : agentData.fields(transId, docId)) {
            Long group = field.getGroupId();
            Map<String, Object> value = value(field.getKey(), field.isConfirmed() ? field.getConfirmedValue() : field.getValue(),
                    group == null || group == 0 ? null : "g" + group);
            value.put("fieldId", field.getFieldId());
            value.put("bbox", field.getBbox());
            value.put("confirmed", field.isConfirmed());
            value.put("recognizedValue", field.getValue());
            values.add(value);
        }
        for (OcrAgentData.Table table : agentData.tables(transId, docId)) {
            for (OcrAgentData.Row row : table.getRows()) {
                for (OcrAgentData.Cell cell : row.getCells()) {
                    Map<String, Object> value = value(table.getKey() + "." + cell.getKey(),
                            cell.isConfirmed() ? cell.getConfirmedValue() : cell.getValue(),
                            "t" + table.getTableId() + "#" + row.getRowSeq());
                    value.put("bbox", cell.getBbox());
                    value.put("confirmed", cell.isConfirmed());
                    value.put("recognizedValue", cell.getValue());
                    values.add(value);
                }
            }
        }
        return values;
    }

    private static Map<String, Object> value(String key, String value, String row) {
        Map<String, Object> out = new HashMap<>();
        out.put("key", key);
        out.put("value", value);
        out.put("row", row);
        return out;
    }

    /** ruleId 가 없으면 등록, 있으면 새 버전을 추가한다. 지난 버전은 고치지 않는다 */
    @Transactional
    public CrossRuleVO save(CrossRuleVO rule, String userId) {
        if (rule == null || rule.getWkId() == null || rule.getWkId().isBlank()) {
            throw new CrossRuleException(HttpStatus.BAD_REQUEST, "업무를 선택해 주세요.");
        }
        rule.setRuleNm(rule.getRuleNm() == null ? "" : rule.getRuleNm().trim());
        if (rule.getRuleNm().isEmpty() || bytes(rule.getRuleNm()) > NAME_MAX_BYTES) {
            throw new CrossRuleException(HttpStatus.BAD_REQUEST, "규칙명을 입력해 주세요(한글 33자 이내).");
        }
        validate(rule.getDefJson(), linkedForms(rule.getWkId()), rolesByForm(rule.getWkId()));

        persist(rule, userId, null);
        log.info("[서식간규칙관리] 저장 완료 ruleId={} wkId={} version={} userId={}",
                rule.getRuleId(), rule.getWkId(), rule.getCurVer(), userId);
        return mapper.selectDefRule(rule.getRuleId());
    }

    private void persist(CrossRuleVO rule, String userId, String historyNote) {
        boolean created = rule.getRuleId() == null;
        int version = 1;
        if (created) {
            rule.setCmprTypCd(ruleType(rule.getDefJson()));
            mapper.insertRule(rule, userId);
        } else {
            CrossRuleVO current = mapper.selectDefRule(rule.getRuleId());
            if (current == null || !rule.getWkId().equals(current.getWkId())) {
                throw new CrossRuleException(HttpStatus.NOT_FOUND, "수정할 규칙을 찾지 못했습니다. 목록을 새로 고친 뒤 다시 시도해 주세요.");
            }
            version = current.getCurVer() + 1;
            rule.setCmprTypCd(ruleType(rule.getDefJson()));
            mapper.updateRule(rule, userId);
        }
        mapper.insertVersion(rule.getRuleId(), version, SCHEMA_VERSION, rule.getDefJson(), userId);
        rule.setCurVer(version);
        rule.setUseYn("N".equals(rule.getUseYn()) ? "N" : "Y");
        mapper.updateDefHeader(rule);
        mapper.insertHistory(rule.getRuleId(), historyNote != null ? historyNote : (created ? "등록 v" : "수정 v") + version + ": " + rule.getRuleNm(), userId);
    }

    @Transactional
    public void setUse(String wkId, long ruleId, boolean use, String userId) {
        CrossRuleVO current = require(wkId, ruleId);
        // 복사했거나 서식 연결이 풀린 규칙은 고치기 전에 켤 수 없다.
        if (use) validate(current.getDefJson(), linkedForms(wkId), rolesByForm(wkId));
        mapper.updateUse(ruleId, use ? "Y" : "N", userId);
        mapper.insertHistory(ruleId, use ? "사용 켜기" : "사용 끄기", userId);
        log.info("[서식간규칙관리] 사용 변경 완료 ruleId={} wkId={} use={} userId={}", ruleId, wkId, use, userId);
    }

    /** 머리글만 지운다. 버전과 이력은 남긴다 */
    @Transactional
    public void delete(String wkId, long ruleId, String userId) {
        CrossRuleVO current = require(wkId, ruleId);
        mapper.deleteRule(ruleId);
        mapper.insertHistory(ruleId, "삭제 v" + current.getCurVer() + ": " + current.getRuleNm(), userId);
        log.info("[서식간규칙관리] 삭제 완료 ruleId={} wkId={} version={} name={} userId={}", ruleId, wkId, current.getCurVer(), current.getRuleNm(), userId);
    }

    /** 저장하지 않고 판정한다(화면 미리보기). rules 의 각 항목은 ruleId(없을 수 있음)와 defJson */
    public List<Map<String, Object>> judge(String wkId, String transactionId, List<CrossRuleVO> rules) {
        if (rules == null || rules.size() > MAX_JUDGE_RULES) {
            throw new CrossRuleException(HttpStatus.BAD_REQUEST, "판정할 규칙을 확인해 주세요.");
        }
        List<OcrAgentData.CrossDefinitionRule> requests = new ArrayList<>();
        for (CrossRuleVO rule : rules) {
            requests.add(new OcrAgentData.CrossDefinitionRule(rule.getRuleId() == null ? 0 : rule.getRuleId(),
                    rule.getCurVer() == null ? 0 : rule.getCurVer(), "", rule.getDefJson()));
        }
        long started = System.currentTimeMillis();
        List<Map<String, Object>> results = agentData.judgeCrossRules(new OcrAgentData.CrossJudgeRequest(transactionId, requests));
        log.info("[서식간규칙관리] 미리보기 완료 wkId={} transId={} rules={} elapsedMs={}", wkId, transactionId, results.size(),
                System.currentTimeMillis() - started);
        return results;
    }

    private Map<Long, String> linkedForms(String wkId) {
        Map<Long, String> forms = new HashMap<>();
        ocrReqMapper.selectWkFormList(wkId).forEach(form -> forms.put(form.getLefrmId(), form.getLefrmNm()));
        return forms;
    }

    private Map<Long, Set<String>> rolesByForm(String wkId) {
        Map<Long, Set<String>> roles = new HashMap<>();
        mapper.selectRoles(wkId).forEach(role -> roles.computeIfAbsent(role.getFormId(), id -> new LinkedHashSet<>()).add(role.getRoleNm()));
        return roles;
    }

    private CrossRuleVO require(String wkId, long ruleId) {
        CrossRuleVO current = mapper.selectDefRule(ruleId);
        if (current == null || !current.getWkId().equals(wkId)) {
            throw new CrossRuleException(HttpStatus.NOT_FOUND, "규칙을 찾지 못했습니다. 목록을 새로 고친 뒤 다시 시도해 주세요.");
        }
        return current;
    }

    /**
     * 정의 구조를 검사한다. 정해진 값만 받는다.
     * 구조 검사는 Agent CrossRuleDefinition.parse 와 같아야 한다. 저장된 규칙이 판정 때 거절되면 안 되기 때문이다.
     * 두 검사가 같은지는 agent 시험 자료 cross-rule/def-validation.json 을 양쪽 시험이 함께 읽어 확인한다.
     * 정의 크기, 항목명 길이, 업무에 연결된 서식, 등록된 역할은 저장 때의 정책이거나 DB 가 필요해 여기서만 검사한다.
     */
    void validate(String defJson, Map<Long, String> forms, Map<Long, Set<String>> roles) {
        JsonNode def = parse(defJson);
        if (def.path("schemaVersion").asInt(-1) != SCHEMA_VERSION) {
            throw invalid("지원하지 않는 규칙 형식입니다.");
        }
        Map<String, Long> sources = new TreeMap<>();
        for (JsonNode source : def.path("sources")) {
            long formId = sourceId(source);
            if (!forms.containsKey(formId)) {
                throw new CrossRuleException(HttpStatus.BAD_REQUEST, "업무에 연결된 서식만 선택할 수 있습니다.");
            }
            String alias = source.path("alias").asText("");
            if (alias.isEmpty() || sources.put(alias, formId) != null) {
                throw invalid("서류 별칭이 비었거나 중복됩니다.");
            }
        }
        for (JsonNode source : def.path("sources")) {
            if (source.has("roleKey")) {
                throw invalid("지원하지 않는 규칙 형식입니다.");
            }
            String role = source.path("role").asText("");
            if (!role.isEmpty() && !roles.getOrDefault(sourceId(source), Set.of()).contains(role)) {
                throw invalid("서식에 없는 역할입니다: " + role);
            }
            for (JsonNode where : source.path("documentWhere")) {
                if (UNARY_OPERATORS.contains(where.path("op").asText())) {
                    throw invalid("서류 고르기에는 값 있음·없음 비교를 쓸 수 없습니다.");
                }
                documentPick(where, sources);
            }
        }
        JsonNode cases = def.path("cases");
        if (!cases.isArray() || cases.isEmpty()) {
            throw invalid("비교를 하나 이상 지정해 주세요.");
        }
        Set<String> checkIds = new LinkedHashSet<>();
        for (JsonNode ruleCase : cases) {
            if (!ruleCase.path("when").isNull() && !ruleCase.path("when").isMissingNode()) {
                when(ruleCase.path("when"), sources);
            } else if (cases.size() > 1) {
                throw invalid("경우를 나누면 경우마다 조건을 지정해 주세요.");
            }
            caseChecks(ruleCase, sources, checkIds);
        }
        if (def.path("otherwise").has("checks")) {
            caseChecks(def.path("otherwise"), sources, checkIds);
        }
    }

    /** 적용 조건은 비교 하나 또는 {all:[비교...]} 다. 조건은 모두 맞아야 한다 */
    private void when(JsonNode when, Map<String, Long> sources) {
        if (!when.has("all")) {
            check(when, sources, sources);
            return;
        }
        JsonNode all = when.path("all");
        if (!all.isArray() || all.isEmpty()) {
            throw invalid("적용 조건을 지정해 주세요.");
        }
        for (JsonNode condition : all) {
            check(condition, sources, sources);
        }
    }

    /** 서류 고르기의 왼쪽은 고르는 서류 자신의 항목 하나다. 값 한 쌍만 비교하므로 범위와 찾은 서류 수를 쓰면 조건이 무시된다 */
    private void documentPick(JsonNode where, Map<String, Long> sources) {
        check(where, Map.of(SELF, 0L), sources);
        // 고르기는 문서의 값을 바로 비교하므로 왼쪽 정렬은 적용되지 않는다.
        if (!"item".equals(where.path("left").path("kind").asText()) || where.path("left").path("keys").size() != 1 || where.path("left").path("sort").isObject()) {
            throw invalid("서류 고르기의 왼쪽은 고르는 서류의 항목 하나여야 합니다.");
        }
        JsonNode range = where.path("rightDocCount");
        if ("BETWEEN".equals(where.path("op").asText()) || !(range.isMissingNode() || range.isNull())) {
            throw invalid("서류 고르기에는 범위 비교와 찾은 서류 수를 쓸 수 없습니다.");
        }
    }

    /** 결과 화면이 비교 ID 로 정의를 찾으므로 ID 는 규칙 전체에서 하나다. 비교 없는 경우는 항상 일치가 되므로 거절한다 */
    private void caseChecks(JsonNode ruleCase, Map<String, Long> sources, Set<String> checkIds) {
        JsonNode checks = ruleCase.path("checks");
        if (!checks.isArray() || checks.isEmpty()) {
            throw invalid("비교를 하나 이상 지정해 주세요.");
        }
        for (JsonNode check : checks) {
            if (check.has("any")) {
                if (checks.size() != 1) {
                    throw invalid("통과 방법 묶음은 경우의 비교 전체여야 합니다.");
                }
                anyGroup(check, sources, checkIds);
            } else {
                caseCheck(check, sources, checkIds);
            }
        }
    }

    /** 통과 방법 묶음. 화면이 다시 열 수 있는 한 가지 모양(방법 2~5개, 방법 안은 비교 목록)만 받는다 */
    private void anyGroup(JsonNode group, Map<String, Long> sources, Set<String> checkIds) {
        JsonNode methods = group.path("any");
        if (!methods.isArray() || methods.size() < MIN_METHODS || methods.size() > MAX_METHODS) {
            throw invalid("통과 방법은 " + MIN_METHODS + "~" + MAX_METHODS + "개여야 합니다.");
        }
        checkId(group, checkIds);
        for (JsonNode method : methods) {
            checkId(method, checkIds);
            JsonNode all = method.path("all");
            if (!all.isArray() || all.isEmpty()) {
                throw invalid("통과 방법마다 비교를 하나 이상 지정해 주세요.");
            }
            for (JsonNode check : all) {
                caseCheck(check, sources, checkIds);
            }
        }
    }

    private void caseCheck(JsonNode check, Map<String, Long> sources, Set<String> checkIds) {
        checkId(check, checkIds);
        check(check, sources, sources);
    }

    /** ':' 는 옛 범위 표현(c1:b)이다. 다른 뜻으로 읽지 않게 거절한다 */
    private void checkId(JsonNode node, Set<String> checkIds) {
        String id = node.path("id").asText();
        if (id.contains(":")) {
            throw invalid("지원하지 않는 규칙 형식입니다.");
        }
        if (!checkIds.add(id)) {
            throw invalid("비교 ID 가 중복됩니다.");
        }
    }

    /** 같은 행 비교는 글자로 양쪽 항목 수를 맞춰 비교한다 */
    private void rowKeys(JsonNode check) {
        int left = "item".equals(check.path("left").path("kind").asText()) ? check.path("left").path("keys").size() : 0;
        int right = "item".equals(check.path("right").path("kind").asText()) ? check.path("right").path("keys").size() : 0;
        if (left <= 1 && right <= 1) return;
        if (left != right || !"TEXT".equals(check.path("compareAs").asText("TEXT"))) {
            throw invalid("같은 행 비교는 양쪽 항목 수가 같은 글자 비교만 쓸 수 있습니다.");
        }
    }

    /** 찾은 서류 수는 찾았는지를 세므로 부정 비교와 범위 비교에는 쓸 수 없다 */
    private void docCountRange(JsonNode check) {
        JsonNode range = check.path("rightDocCount");
        if (range.isMissingNode() || range.isNull()) return;
        JsonNode min = range.path("min");
        JsonNode max = range.path("max");
        if (!min.isIntegralNumber() || !max.isIntegralNumber() || min.asInt() < 0 || min.asInt() > max.asInt()) {
            throw invalid("찾은 서류 수는 0 이상의 정수이고 최소가 최대 이하여야 합니다.");
        }
        // 찾은 서류 수는 서류별로 값을 찾았는지 세므로 값 있음 비교와 집계에는 쓸 수 없다.
        if (UNARY_OPERATORS.contains(check.path("op").asText()) || "aggregate".equals(check.path("right").path("kind").asText())
                || "aggregate".equals(check.path("left").path("kind").asText())) {
            throw invalid("찾은 서류 수는 값 있음·없음이나 집계 비교에 쓸 수 없습니다.");
        }
        String op = check.path("op").asText();
        if (NEGATIVE_OPERATORS.contains(op) || "BETWEEN".equals(op)) {
            throw invalid("찾은 서류 수는 '같다·있다·포함한다' 비교에만 쓸 수 있습니다.");
        }
    }

    private static long sourceId(JsonNode source) {
        return source.path("formId").asLong(-1);
    }

    /** leftScope·rightScope 는 왼쪽·오른쪽 값이 가리킬 수 있는 서류다. 서류 고르기의 왼쪽만 고르는 서류 자신($self)이다 */
    private void check(JsonNode check, Map<String, Long> leftScope, Map<String, Long> rightScope) {
        String op = check.path("op").asText();
        if (!OPERATORS.contains(op)) {
            throw invalid("비교 방법이 올바르지 않습니다.");
        }
        String compareAs = check.path("compareAs").asText("TEXT");
        if (!COMPARE_AS.contains(compareAs)) {
            throw invalid("비교 형식이 올바르지 않습니다.");
        }
        if (!("TEXT".equals(compareAs) ? TEXT_OPERATORS : ORDERED_OPERATORS).contains(op)) {
            throw invalid("이 비교 형식에는 쓸 수 없는 비교 방법입니다.");
        }
        if (!MATCHES.contains(check.path("match").asText("ANY"))) {
            throw invalid("여러 값 판정 방식이 올바르지 않습니다.");
        }
        for (JsonNode option : check.path("normalize")) {
            if (!NORMALIZE.contains(option.asText())) {
                throw invalid("표기 통일 방식이 올바르지 않습니다.");
            }
        }
        tolerance(check.path("tolerance"));
        tolerance(check.path("tolerancePct"));
        operand(check.path("left"), leftScope, 0);
        if (UNARY_OPERATORS.contains(op)) {
            JsonNode left = check.path("left");
            if (!"item".equals(left.path("kind").asText()) || left.path("keys").size() != 1 || left.path("sort").isObject()) {
                throw invalid("값 있음·없음 비교는 항목 하나만 볼 수 있습니다.");
            }
            docCountRange(check);
            return;
        }
        if ("BETWEEN".equals(op)) {
            bound(check.path("low"));
            bound(check.path("high"));
        } else {
            operand(check.path("right"), rightScope, 0);
        }
        docCountRange(check);
        rowKeys(check);
        if ("MATCHES".equals(op)) {
            pattern(check.path("right"));
        }
        aggregateCompare(check);
    }

    private void tolerance(JsonNode value) {
        if (value.isMissingNode() || value.isNull()) return;
        try {
            if (new BigDecimal(value.asText()).signum() < 0) {
                throw invalid("허용 오차는 0 이상의 숫자여야 합니다.");
            }
        } catch (NumberFormatException e) {
            throw new CrossRuleException(HttpStatus.BAD_REQUEST, "허용 오차는 0 이상의 숫자여야 합니다.", e);
        }
    }

    /** 정규식은 저장할 때 걸러 판정 시점의 검증불가를 막는다 */
    private void pattern(JsonNode right) {
        String regex = right.path("value").asText("");
        if (!"text".equals(right.path("kind").asText()) || regex.isEmpty() || regex.length() > MAX_PATTERN_LENGTH) {
            throw invalid("형식은 " + MAX_PATTERN_LENGTH + "자 이내의 정규식으로 입력해 주세요.");
        }
        try {
            Pattern.compile(regex);
        } catch (PatternSyntaxException e) {
            throw new CrossRuleException(HttpStatus.BAD_REQUEST, "정규식이 올바르지 않습니다.", e);
        }
    }

    /** 집계 결과는 숫자·날짜다. 글자로 비교하면 100 과 100.0 이 달라지므로 형식을 맞추게 한다 */
    private void aggregateCompare(JsonNode check) {
        for (JsonNode side : List.of(check.path("left"), check.path("right"))) {
            if ("aggregate".equals(side.path("kind").asText())
                    && !side.path("as").asText("NUMBER").equals(check.path("compareAs").asText())) {
                throw invalid("집계 값은 같은 형식(숫자·날짜)으로 비교해 주세요.");
            }
        }
    }

    /** 범위의 경계는 값마다 비교하므로 값이 하나인 종류만 쓴다 */
    private void bound(JsonNode operand) {
        if (!Set.of("number", "text", "baseDate").contains(operand.path("kind").asText())) {
            throw invalid("범위의 경계는 숫자·글자·기준일만 쓸 수 있습니다.");
        }
        operand(operand, Map.of(), 0);
    }

    private void operand(JsonNode operand, Map<String, Long> sources, int calcDepth) {
        String kind = operand.path("kind").asText();
        if (!KINDS.contains(kind)) {
            throw invalid("비교 대상 형식이 올바르지 않습니다.");
        }
        if ("calc".equals(kind)) {
            if (!CALC_OPERATORS.contains(operand.path("op").asText()) || !ROUNDS.contains(operand.path("round").asText()) || calcDepth >= CALC_MAX_DEPTH) {
                throw invalid("계산식이 올바르지 않습니다. 계산은 3단계까지 쓸 수 있습니다.");
            }
            operand(operand.path("left"), sources, calcDepth + 1);
            operand(operand.path("right"), sources, calcDepth + 1);
            return;
        }
        if ("union".equals(kind)) {
            if (operand.path("items").isEmpty()) {
                throw invalid("합칠 항목을 선택해 주세요.");
            }
            for (JsonNode item : operand.path("items")) {
                // 글자·숫자는 '자릿수가 5,6,7,8 중 하나' 같은 값 목록을 만들 때 쓴다.
                if (!Set.of("item", "text", "number").contains(item.path("kind").asText())) {
                    throw invalid("합칠 수 있는 것은 항목과 값뿐입니다.");
                }
                operand(item, sources, calcDepth);
            }
            return;
        }
        if ("aggregate".equals(kind)) {
            aggregate(operand, sources, calcDepth);
            return;
        }
        if ("slice".equals(kind)) {
            if (operand.path("start").asInt() < 1 || operand.path("length").asInt() < 1) {
                throw invalid("자릿수는 1 이상이어야 합니다.");
            }
            singleItem(operand.path("of"), sources, calcDepth, "자릿수 추출");
            return;
        }
        if ("text".equals(kind) || "number".equals(kind)) {
            if (operand.path("value").isMissingNode() || operand.path("value").isNull()) {
                throw invalid("비교할 값을 입력해 주세요.");
            }
            return;
        }
        if ("baseDate".equals(kind)) {
            for (String unit : List.of("years", "months", "days")) {
                JsonNode amount = operand.path("offset").path(unit);
                if (!amount.isMissingNode() && !amount.isIntegralNumber()) {
                    throw invalid("기준일의 기간은 정수여야 합니다.");
                }
            }
            return;
        }
        Long formId = sources.get(operand.path("source").asText());
        if (formId == null) {
            throw invalid("비교 대상 서류를 찾지 못했습니다.");
        }
        if ("docCount".equals(kind)) {
            return;
        }
        List<String> keys = new ArrayList<>();
        operand.path("keys").forEach(key -> keys.add(key.asText()));
        for (JsonNode where : operand.path("where")) {
            keys.add(where.path("key").asText());
            where(where);
        }
        JsonNode sort = operand.path("sort");
        if (sort.isObject() != operand.path("limit").isIntegralNumber() || (sort.isObject() && operand.path("limit").asInt() < 1)) {
            throw invalid("정렬은 1 이상의 상위 건수와 함께 써야 합니다.");
        }
        if (sort.isObject()) {
            if (!SORT_ORDERS.contains(sort.path("order").asText("DESC")) || !ORDERED_TYPES.contains(sort.path("as").asText("NUMBER"))) {
                throw invalid("정렬은 숫자·날짜의 큰 순·작은 순만 쓸 수 있습니다.");
            }
            keys.add(sort.path("by").asText());
        }
        if (operand.path("keys").isEmpty() || keys.stream().anyMatch(String::isBlank)) {
            throw new CrossRuleException(HttpStatus.BAD_REQUEST, "비교할 항목을 선택해 주세요.");
        }
        for (String key : keys) {
            if (bytes(key) > NAME_MAX_BYTES) {
                throw new CrossRuleException(HttpStatus.BAD_REQUEST, "항목명이 너무 깁니다.");
            }
        }
    }

    private void aggregate(JsonNode operand, Map<String, Long> sources, int calcDepth) {
        String fn = operand.path("fn").asText();
        String as = operand.path("as").asText("NUMBER");
        if (!AGGREGATE_FUNCTIONS.contains(fn) || !AGGREGATE_SCOPES.contains(operand.path("scope").asText("DOCUMENT")) || !ORDERED_TYPES.contains(as)) {
            throw invalid("집계 방법이 올바르지 않습니다.");
        }
        if ("DATE".equals(as) && !Set.of("MAX", "MIN").contains(fn)) {
            throw invalid("날짜는 최댓값·최솟값만 구할 수 있습니다.");
        }
        singleItem(operand.path("of"), sources, calcDepth, "집계");
    }

    /** 집계와 자릿수 추출은 항목 하나의 값만 받는다. 같은 행 묶음 값은 숫자나 글자 일부로 다룰 수 없다 */
    private void singleItem(JsonNode item, Map<String, Long> sources, int calcDepth, String name) {
        if (!"item".equals(item.path("kind").asText()) || item.path("keys").size() != 1) {
            throw invalid(name + " 대상은 항목 하나여야 합니다.");
        }
        operand(item, sources, calcDepth);
    }

    private void where(JsonNode where) {
        String op = where.path("op").asText("EQ");
        if (!WHERE_OPERATORS.contains(op)) {
            throw invalid("조건 방법이 올바르지 않습니다.");
        }
        if ("IN".equals(op) && where.path("values").isEmpty()) {
            throw invalid("조건 값 목록을 입력해 주세요.");
        }
        JsonNode value = where.path("value").path("value");
        if (!"IN".equals(op) && (value.isMissingNode() || value.isNull())) {
            throw invalid("조건 값을 입력해 주세요.");
        }
    }

    private String ruleType(String defJson) {
        String type = parse(defJson).path("cases").path(0).path("checks").path(0).path("uiType").asText("compare");
        return type.length() > 20 ? type.substring(0, 20) : type;
    }

    private JsonNode parse(String defJson) {
        if (defJson == null || defJson.isBlank() || defJson.length() > DEF_MAX_CHARS) {
            throw invalid("규칙 정의가 비었거나 너무 큽니다.");
        }
        try {
            return json.readTree(defJson);
        } catch (JsonProcessingException e) {
            throw new CrossRuleException(HttpStatus.BAD_REQUEST, "규칙 정의 형식이 올바르지 않습니다.", e);
        }
    }

    private static CrossRuleException invalid(String message) {
        return new CrossRuleException(HttpStatus.BAD_REQUEST, message);
    }

    private static int bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8).length;
    }
}
