package com.agilesoda.module.apply.service;

import com.agilesoda.module.apply.mapper.CrossRuleMapper;
import com.agilesoda.module.apply.vo.CrossRoleVO;
import com.agilesoda.module.apply.vo.CrossRuleVO;
import com.agilesoda.module.ocr.mapper.OcrReqMapper;
import com.agilesoda.module.ocr.service.OcrAgentDataClient;
import com.agilesoda.module.ocr.vo.OcrAgentData;
import com.agilesoda.module.ocr.vo.OcrResultLefrmVO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 규칙 정의 검사와 버전 저장. DB·Agent 는 mock 이다. */
class CrossRuleDefServiceTest {
    private static final String VALID = """
            {"schemaVersion":1,"sources":[{"alias":"a","formId":1},{"alias":"b","formId":2}],
             "cases":[{"when":null,"checks":[{"id":"c1","uiType":"compare","op":"EQ","match":"ANY",
               "left":{"kind":"item","source":"a","keys":["성명"]},
               "right":{"kind":"item","source":"b","keys":["임원.성명"],
                 "where":[{"key":"임원.직위","op":"EQ","value":{"kind":"text","value":"대표자"}}]}}]}]}""";

    private CrossRuleMapper mapper;
    private OcrReqMapper ocr;
    private OcrAgentDataClient agent;
    private CrossRuleDefService service;

    /**
     * 저장 검사와 Agent 실행 검사가 같은지 확인한다. 자료는 Agent 시험 자료 하나를 함께 읽는다(Gradle 시험의 작업 폴더는 portal 이다).
     * 서식은 자료가 쓰는 1·2번을 연결한 것으로 두고, 역할은 자료에 쓰지 않는다.
     */
    @TestFactory
    List<DynamicTest> sharedValidationCases() throws IOException {
        JsonNode cases = new ObjectMapper().readTree(Files.readString(Path.of("../agent/src/test/resources/cross-rule/def-validation.json")));
        Map<Long, String> forms = Map.of(1L, "신분증", 2L, "등기부");
        List<DynamicTest> tests = new ArrayList<>();
        for (JsonNode testCase : cases) {
            String def = testCase.path("def").toString();
            tests.add(DynamicTest.dynamicTest(testCase.path("name").asText(), () -> {
                if (testCase.path("valid").asBoolean()) {
                    assertDoesNotThrow(() -> service.validate(def, forms, Map.of()));
                } else {
                    assertThrows(CrossRuleException.class, () -> service.validate(def, forms, Map.of()));
                }
            }));
        }
        return tests;
    }

    @BeforeEach
    void setUp() {
        mapper = mock(CrossRuleMapper.class);
        ocr = mock(OcrReqMapper.class);
        agent = mock(OcrAgentDataClient.class);
        service = new CrossRuleDefService(mapper, ocr, agent);
        OcrResultLefrmVO a = new OcrResultLefrmVO();
        a.setLefrmId(1L);
        a.setLefrmNm("신분증");
        a.setFormId("ID");
        OcrResultLefrmVO b = new OcrResultLefrmVO();
        b.setLefrmId(2L);
        b.setLefrmNm("등기");
        when(ocr.selectWkFormList("WK")).thenReturn(List.of(a, b));
    }

    private static CrossRuleVO rule(Long id, String def) {
        CrossRuleVO rule = new CrossRuleVO();
        rule.setRuleId(id);
        rule.setWkId("WK");
        rule.setRuleNm(" 대표자 확인 ");
        rule.setDefJson(def);
        return rule;
    }

    @Test
    void 수정하면_새_버전을_추가한다() {
        CrossRuleVO current = rule(7L, VALID);
        current.setCurVer(2);
        when(mapper.selectDefRule(7L)).thenReturn(current);
        service.save(rule(7L, VALID), "U1");
        verify(mapper).insertVersion(eq(7L), eq(3), eq(1), eq(VALID), eq("U1"));
    }

    @Test
    void 다른_업무의_규칙은_수정할_수_없다() {
        CrossRuleVO current = rule(7L, VALID);
        current.setWkId("OTHER");
        current.setCurVer(1);
        when(mapper.selectDefRule(7L)).thenReturn(current);
        CrossRuleException e = assertThrows(CrossRuleException.class, () -> service.save(rule(7L, VALID), "U1"));
        assertEquals(HttpStatus.NOT_FOUND, e.getStatus());
        verify(mapper, never()).insertVersion(anyLong(), anyInt(), anyInt(), any(), any());
    }

    @Test
    void 잘못된_정의는_거절한다() {
        Map<Long, String> forms = Map.of(1L, "신분증", 2L, "등기");
        for (String bad : List.of("{not json", VALID.replace("\"schemaVersion\":1", "\"schemaVersion\":9"),
                VALID.replace("\"formId\":2", "\"formId\":99"), VALID.replace("\"op\":\"EQ\",\"match\"", "\"op\":\"DROP\",\"match\""),
                VALID.replace("\"source\":\"b\"", "\"source\":\"zz\""), VALID.replace("\"kind\":\"text\"", "\"kind\":\"text\"").replace("\"keys\":[\"성명\"]", "\"keys\":[]"))) {
            assertThrows(CrossRuleException.class, () -> service.validate(bad, forms, Map.of()), bad);
        }
    }

    private static final String ITEM_CHECK = "{\"id\":\"%s\",\"left\":{\"kind\":\"item\",\"source\":\"a\",\"keys\":[\"k\"]},\"op\":\"%s\",\"right\":{\"kind\":\"text\",\"value\":\"x\"}%s}";
    private static final String DOC_WHEN = "{\"left\":{\"kind\":\"docCount\",\"source\":\"a\"},\"op\":\"GE\",\"right\":{\"kind\":\"number\",\"value\":1},\"compareAs\":\"NUMBER\"}";

    private static String ruleOf(String cases, String otherwise) {
        return "{\"schemaVersion\":1,\"sources\":[{\"alias\":\"a\",\"formId\":1}],\"cases\":[" + cases + "],\"otherwise\":" + otherwise + "}";
    }

    @Test
    void 경우_나누기와_그_외의_비교도_검사한다() {
        Map<Long, String> forms = Map.of(1L, "신분증");
        String c1 = ITEM_CHECK.formatted("c1", "EQ", "");
        String c2 = ITEM_CHECK.formatted("c2", "EQ", "");
        String first = "{\"when\":" + DOC_WHEN + ",\"checks\":[" + c1 + "]}";
        service.validate(ruleOf(first, "{\"checks\":[" + c2 + "]}"), forms, Map.of());
        // 비교 ID 는 경우가 달라도 겹치면 안 된다
        assertThrows(CrossRuleException.class, () -> service.validate(ruleOf(first, "{\"checks\":[" + c1 + "]}"), forms, Map.of()));
        // 그 외의 비교도 같은 기준으로 검사한다
        assertThrows(CrossRuleException.class, () -> service.validate(ruleOf(first, "{\"checks\":[" + ITEM_CHECK.formatted("c2", "BAD", "") + "]}"), forms, Map.of()));
        // 경우가 둘이면 조건 없는 경우를 둘 수 없다
        assertThrows(CrossRuleException.class, () -> service.validate(ruleOf(first + ",{\"checks\":[" + c2 + "]}", "{}"), forms, Map.of()));
    }

    @Test
    void 찾은_서류_수_범위를_검사한다() {
        Map<Long, String> forms = Map.of(1L, "신분증");
        service.validate(ruleOf("{\"checks\":[" + ITEM_CHECK.formatted("c1", "EQ", ",\"rightDocCount\":{\"min\":0,\"max\":1}") + "]}", "{}"), forms, Map.of());
        for (String bad : List.of("{\"min\":2,\"max\":1}", "{\"min\":-1,\"max\":1}", "{\"min\":\"1\",\"max\":1}", "{\"min\":1}")) {
            String rule = ruleOf("{\"checks\":[" + ITEM_CHECK.formatted("c1", "EQ", ",\"rightDocCount\":" + bad) + "]}", "{}");
            assertThrows(CrossRuleException.class, () -> service.validate(rule, forms, Map.of()), bad);
        }
        assertThrows(CrossRuleException.class, () -> service.validate(ruleOf("{\"checks\":[" + ITEM_CHECK.formatted("c1", "NE", ",\"rightDocCount\":{\"min\":0,\"max\":1}") + "]}", "{}"), forms, Map.of()));
    }

    @Test
    void 비교가_빈_경우와_그_외는_거절한다() {
        Map<Long, String> forms = Map.of(1L, "신분증");
        String c1 = ITEM_CHECK.formatted("c1", "EQ", "");
        String first = "{\"when\":" + DOC_WHEN + ",\"checks\":[" + c1 + "]}";
        assertThrows(CrossRuleException.class, () -> service.validate(ruleOf(first + ",{\"when\":" + DOC_WHEN + ",\"checks\":[]}", "{}"), forms, Map.of()));
        assertThrows(CrossRuleException.class, () -> service.validate(ruleOf(first, "{\"checks\":[]}"), forms, Map.of()));
    }

    @Test
    void 서식_연결이_풀린_규칙은_켤_수_없다() {
        CrossRuleVO current = new CrossRuleVO();
        current.setWkId("W");
        current.setCurVer(1);
        current.setDefJson(VALID);
        when(mapper.selectDefRule(7L)).thenReturn(current);
        assertThrows(CrossRuleException.class, () -> service.setUse("W", 7L, true, "u"));
        verify(mapper, never()).updateUse(anyLong(), anyString(), anyString());
    }

    @Test
    void 합집합_계산_서류_고르기의_항목도_참조_목록에_넣는다() {
        String def = """
                {"schemaVersion":1,"sources":[{"alias":"a","formId":1},
                  {"alias":"b","formId":2,"documentWhere":[{"op":"EQ","left":{"kind":"item","source":"$self","keys":["상호"]},
                    "right":{"kind":"item","source":"a","keys":["법인명"]}}]}],
                 "cases":[{"when":null,"checks":[
                  {"id":"c1","op":"IN_LIST","match":"ALL","left":{"kind":"item","source":"a","keys":["성명"]},
                   "right":{"kind":"union","items":[{"kind":"item","source":"b","keys":["대표자"]},{"kind":"item","source":"a","keys":["가족"]}]}},
                  {"id":"c2","op":"GE","match":"ANY","compareAs":"NUMBER","left":{"kind":"item","source":"a","keys":["소득"]},
                   "right":{"kind":"calc","op":"MUL","left":{"kind":"item","source":"b","keys":["금액"]},"right":{"kind":"number","value":2}}}]}]}""";
        assertDoesNotThrow(() -> service.validate(def, Map.of(1L, "신분증", 2L, "등기"), Map.of()));
    }

    @Test
    void 계산이_3단을_넘거나_모르는_연산이면_거절한다() {
        String item = "{\"kind\":\"item\",\"source\":\"a\",\"keys\":[\"성명\"]}";
        String number = "{\"kind\":\"number\",\"value\":1}";
        String deep = item;
        for (int i = 0; i < 4; i++) {
            deep = "{\"kind\":\"calc\",\"op\":\"ADD\",\"left\":" + deep + ",\"right\":" + number + "}";
        }
        Map<Long, String> forms = Map.of(1L, "신분증");
        String template = "{\"schemaVersion\":1,\"sources\":[{\"alias\":\"a\",\"formId\":1}],\"cases\":[{\"checks\":[{\"id\":\"c1\",\"op\":\"GE\",\"left\":" + item + ",\"right\":%s}]}]}";
        String tooDeep = deep;
        assertThrows(CrossRuleException.class, () -> service.validate(String.format(template, tooDeep), forms, Map.of()));
        String badOp = "{\"kind\":\"calc\",\"op\":\"POW\",\"left\":" + item + ",\"right\":" + number + "}";
        assertThrows(CrossRuleException.class, () -> service.validate(String.format(template, badOp), forms, Map.of()));
    }

    @Test
    void 규칙_변경은_Agent_를_부르지_않아_Agent_가_멈춰도_저장된다() {
        CrossRuleVO current = rule(7L, VALID);
        current.setCurVer(1);
        when(mapper.selectDefRule(7L)).thenReturn(current);
        service.save(rule(7L, VALID), "U1");
        service.setUse("WK", 7L, false, "U1");
        service.delete("WK", 7L, "U1");
        verifyNoInteractions(agent);
    }

    private static String withRole(String role) {
        return VALID.replace("{\"alias\":\"b\",\"formId\":2}", "{\"alias\":\"b\",\"formId\":2," + role + "}");
    }

    private static CrossRoleVO role(long formId, String name, String method, String key) {
        CrossRoleVO role = new CrossRoleVO();
        role.setWkId("WK");
        role.setFormId(formId);
        role.setRoleNm(name);
        role.setMethodCd(method);
        role.setFieldKey(key);
        return role;
    }

    @Test void sourceRoleMustBeSetForThatForm() {
        Map<Long, String> forms = Map.of(1L, "신청서", 2L, "등기부");
        Map<Long, Set<String>> roles = Map.of(2L, Set.of("신청인"));
        assertDoesNotThrow(() -> service.validate(withRole("\"role\":\"신청인\""), forms, roles));
        assertThrows(CrossRuleException.class, () -> service.validate(withRole("\"role\":\"관계인\""), forms, roles));
        assertThrows(CrossRuleException.class, () -> service.validate(withRole("\"role\":\"신청인\",\"roleKey\":\"성명\""), forms, roles));
    }

    @Test void oldSplitCheckIdIsRejected() {
        assertThrows(CrossRuleException.class, () -> service.validate(VALID.replace("\"c1\"", "\"c1:b\""), Map.of(1L, "신청서", 2L, "등기부"), Map.of()));
    }

    @Test void roleOptionsComeFromSettingsOfThatForm() {
        when(mapper.selectRoles("wk")).thenReturn(List.of(role(2L, "신청인", "CUSTOMER_NAME", "성명")));
        assertEquals(List.of("신청인"), service.roleOptions("wk", 2L));
        assertEquals(List.of(), service.roleOptions("wk", 1L));
    }

    @Test void saveRoleChecksMethodKeyAndUniqueness() {
        when(mapper.selectRoles("WK")).thenReturn(List.of(role(1L, "신청인", "CUSTOMER_NAME", "성명")));
        service.saveRole(role(1L, "관계인", "REMAINDER", "성명"), "u");
        verify(mapper).mergeRole(argThat(saved -> saved.getFieldKey() == null), eq("u"));
        assertThrows(CrossRuleException.class, () -> service.saveRole(role(1L, "본인", "CUSTOMER_NAME", "성명"), "u"));
        assertThrows(CrossRuleException.class, () -> service.saveRole(role(1L, "2024", "VALUE_CONTAINS", " "), "u"));
        assertThrows(CrossRuleException.class, () -> service.saveRole(role(1L, "기타", "SCRIPT", null), "u"));
        assertThrows(CrossRuleException.class, () -> service.saveRole(role(99L, "기타", "MANUAL", null), "u"));
        when(mapper.selectRoles("WK")).thenReturn(List.of());
        assertThrows(CrossRuleException.class, () -> service.saveRole(role(1L, "관계인", "REMAINDER", null), "u"));
    }

    @Test void roleUsedByRuleCannotBeDeleted() {
        CrossRuleVO off = new CrossRuleVO();
        off.setUseYn("N");
        off.setRuleNm("꺼 둔 규칙");
        off.setDefJson(withRole("\"role\":\"신청인\""));
        when(mapper.selectDefRuleList("wk")).thenReturn(List.of(off));
        assertEquals(HttpStatus.CONFLICT, assertThrows(CrossRuleException.class, () -> service.deleteRole("wk", 2L, "신청인", "u")).getStatus());
        verify(mapper, never()).deleteRole(any(), anyLong(), any());
        service.deleteRole("wk", 2L, "관계인", "u");
        verify(mapper).deleteRole("wk", 2L, "관계인");
    }

    /** 비교 하나짜리 정의를 저장 검사에 넣는다. 값은 모두 가짜다 */
    private void validate(String check) {
        service.validate("{\"schemaVersion\":1,\"sources\":[{\"alias\":\"a\",\"formId\":1}],\"cases\":[{\"checks\":[{\"id\":\"c1\"," + check + "}]}]}",
                Map.of(1L, "신분증"), Map.of());
    }

    private static final String KEY = "{\"kind\":\"item\",\"source\":\"a\",\"keys\":[\"k\"]";

    @Test void presenceNeedsNoRightAndOneItem() {
        assertDoesNotThrow(() -> validate("\"left\":" + KEY + "},\"op\":\"NOT_EMPTY\""));
        assertThrows(CrossRuleException.class, () -> validate("\"left\":{\"kind\":\"item\",\"source\":\"a\",\"keys\":[\"k\",\"m\"]},\"op\":\"EMPTY\""));
        assertThrows(CrossRuleException.class, () -> validate("\"left\":" + KEY + "},\"op\":\"EMPTY\",\"rightDocCount\":{\"min\":1,\"max\":1}"));
    }

    @Test void regexIsCompiledOnSave() {
        assertDoesNotThrow(() -> validate("\"left\":" + KEY + "},\"op\":\"MATCHES\",\"right\":{\"kind\":\"text\",\"value\":\"^a$\"}"));
        assertThrows(CrossRuleException.class, () -> validate("\"left\":" + KEY + "},\"op\":\"MATCHES\",\"right\":{\"kind\":\"text\",\"value\":\"(\"}"));
        assertThrows(CrossRuleException.class, () -> validate("\"left\":" + KEY + "},\"op\":\"MATCHES\",\"right\":" + KEY + "}"));
    }

    @Test void whereOperatorsAreChecked() {
        String right = ",\"op\":\"EQ\",\"right\":{\"kind\":\"text\",\"value\":\"x\"}";
        assertDoesNotThrow(() -> validate("\"left\":" + KEY + ",\"where\":[{\"key\":\"구분\",\"op\":\"IN\",\"values\":[\"부\",\"모\"]}]}" + right));
        assertThrows(CrossRuleException.class, () -> validate("\"left\":" + KEY + ",\"where\":[{\"key\":\"구분\",\"op\":\"IN\",\"values\":[]}]}" + right));
        assertThrows(CrossRuleException.class, () -> validate("\"left\":" + KEY + ",\"where\":[{\"key\":\"구분\",\"op\":\"GT\"}]}" + right));
    }

    @Test void sortNeedsLimit() {
        String right = ",\"op\":\"EQ\",\"right\":{\"kind\":\"text\",\"value\":\"x\"}";
        String sort = ",\"sort\":{\"by\":\"수\",\"order\":\"DESC\",\"as\":\"NUMBER\"}";
        assertDoesNotThrow(() -> validate("\"left\":" + KEY + sort + ",\"limit\":1}" + right));
        assertThrows(CrossRuleException.class, () -> validate("\"left\":" + KEY + sort + "}" + right));
        assertThrows(CrossRuleException.class, () -> validate("\"left\":" + KEY + ",\"limit\":1}" + right));
        assertThrows(CrossRuleException.class, () -> validate("\"left\":" + KEY + sort + ",\"limit\":0}" + right));
        assertThrows(CrossRuleException.class, () -> validate("\"left\":" + KEY + ",\"sort\":{\"by\":\"수\",\"as\":\"TEXT\"},\"limit\":1}" + right));
    }

    @Test void aggregateChecksFunctionTargetAndCompareType() {
        String left = "\"left\":{\"kind\":\"aggregate\",\"fn\":\"%s\",\"as\":\"%s\",\"of\":" + KEY + "}}";
        String number = ",\"op\":\"EQ\",\"right\":{\"kind\":\"number\",\"value\":100},\"compareAs\":\"NUMBER\"";
        assertDoesNotThrow(() -> validate(left.formatted("SUM", "NUMBER") + number));
        assertThrows(CrossRuleException.class, () -> validate(left.formatted("AVG", "NUMBER") + number));
        assertThrows(CrossRuleException.class, () -> validate(left.formatted("SUM", "DATE") + number));
        assertThrows(CrossRuleException.class, () -> validate(left.formatted("SUM", "NUMBER") + number.replace("NUMBER", "TEXT")));
        assertThrows(CrossRuleException.class, () -> validate("\"left\":{\"kind\":\"aggregate\",\"fn\":\"SUM\",\"of\":{\"kind\":\"text\",\"value\":\"1\"}}" + number));
    }

    @Test void sliceAndValueListAreAccepted() {
        String list = ",\"op\":\"IN_LIST\",\"right\":{\"kind\":\"union\",\"items\":[{\"kind\":\"text\",\"value\":\"5\"}]}";
        assertDoesNotThrow(() -> validate("\"left\":{\"kind\":\"slice\",\"of\":" + KEY + "},\"start\":7,\"length\":1}" + list));
        assertThrows(CrossRuleException.class, () -> validate("\"left\":{\"kind\":\"slice\",\"of\":" + KEY + "},\"start\":0,\"length\":1}" + list));
    }
}
