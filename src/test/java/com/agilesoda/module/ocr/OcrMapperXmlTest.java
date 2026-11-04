package com.agilesoda.module.ocr;

import com.agilesoda.module.ocr.mapper.OcrReqMapper;
import com.agilesoda.module.ocr.mapper.OcrReviewMapper;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** 판독결과 매퍼 XML 이 MyBatis 에 올라가고 인터페이스 메서드마다 문장이 있는지 확인한다. DB 실행은 하지 않는다. */
class OcrMapperXmlTest {
    @Test
    void ocrMappersLoadWithAStatementPerMethod() throws Exception {
        Configuration configuration = new Configuration();
        for (String resource : new String[]{"sqlmap/mapper/ocr/OcrReqMapper.xml", "sqlmap/mapper/ocr/OcrReviewMapper.xml"}) {
            try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
                new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
            }
        }
        for (Class<?> mapper : new Class<?>[]{OcrReqMapper.class, OcrReviewMapper.class}) {
            for (var method : mapper.getDeclaredMethods()) {
                assertTrue(configuration.hasStatement(mapper.getName() + "." + method.getName()), method.getName());
            }
        }
    }
}
