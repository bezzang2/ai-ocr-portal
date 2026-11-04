package com.agilesoda.module.apply;

import com.agilesoda.module.apply.mapper.AgentApiMapper;
import com.agilesoda.module.apply.mapper.FormLinkMapper;
import com.agilesoda.module.ocr.service.OcrReqService;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 동기화(AgentApiMapper)와 업무·서식 연결 관리(FormLinkMapper) 매퍼가 올라가고 연결 규칙이 어긋나지 않는지 확인한다. DB 실행은 하지 않는다.
 */
class ApplyFormLinkMapperXmlTest {

    private static Configuration load() throws Exception {
        Configuration configuration = new Configuration();
        for (String resource : new String[]{"sqlmap/mapper/apply/FormLinkMapper.xml", "sqlmap/mapper/apply/AgentApiMapper.xml"}) {
            try (InputStream input = ApplyFormLinkMapperXmlTest.class.getClassLoader().getResourceAsStream(resource)) {
                new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
            }
        }
        configuration.getMappedStatementNames(); // 미완성 문장(다른 namespace 조각 참조)을 마저 만든다
        return configuration;
    }

    private static String sql(Configuration configuration, String id, Map<String, Object> params) {
        return configuration.getMappedStatement(id).getBoundSql(params).getSql().replaceAll("\\s+", " ");
    }

    @Test void mappersLoadWithAStatementPerMethod() throws Exception {
        Configuration configuration = load();
        for (Class<?> mapper : new Class<?>[]{AgentApiMapper.class, FormLinkMapper.class}) {
            for (var method : mapper.getDeclaredMethods()) {
                assertTrue(configuration.hasStatement(mapper.getName() + "." + method.getName()), method.getName());
            }
        }
    }

    @Test void screenUsesTheCodeOnlyMatchRule() throws Exception {
        Configuration configuration = load();
        Map<String, Object> params = new HashMap<>();
        params.put("wkId", "wf-1");
        params.put("unlinkedOnly", true);

        String screen = sql(configuration, FormLinkMapper.class.getName() + ".selectClassList", params);
        assertTrue(screen.contains("(F.DOC_ID = WC.DOC_TYP_CD OR 'KJB_' || F.DOC_ID = WC.DOC_TYP_CD)"), screen);
        assertTrue(!screen.contains("LEFRM_NM ="), screen); // 서식명으로는 맞추지 않는다
        assertTrue(screen.contains("S.WK_ID = ? AND S.MATCH_ID IS NULL"), screen);
    }

    /** SQL 규칙과 동기화의 Java 규칙(OcrReqService.sameFormCode)이 같은 표로 맞고, 중복 검사도 같은 클래스에 맞는 코드를 막는다 */
    @Test void javaRuleAgreesWithSqlRuleAndDuplicateCheck() throws Exception {
        assertTrue(OcrReqService.sameFormCode("KJB_1", "1"));
        assertTrue(OcrReqService.sameFormCode("KJB_1", "KJB_1"));
        assertTrue(OcrReqService.sameFormCode("1", "1"));
        assertFalse(OcrReqService.sameFormCode("1", "KJB_1"));
        assertFalse(OcrReqService.sameFormCode("KJB_1", "kjb_1"));
        assertFalse(OcrReqService.sameFormCode(null, "1"));

        Configuration configuration = load();
        Map<String, Object> params = new HashMap<>();
        params.put("docId", "KJB_1");
        params.put("exceptLefrmId", null);
        String check = sql(configuration, FormLinkMapper.class.getName() + ".countDocId", params);
        assertTrue(check.contains("(DOC_ID = ? OR DOC_ID = 'KJB_' || ? OR 'KJB_' || DOC_ID = ?)"), check);
    }
}
