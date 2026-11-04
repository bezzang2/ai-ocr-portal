package com.agilesoda.module.ocr;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Agent 가 소유한 OCR 업무 테이블은 Portal 이 직접 읽거나 쓰지 않는다(Agent API 로만 다룬다).
 * DB 권한을 앱별로 나누기 전까지 이 테스트가 경계를 지킨다. 소유 표는 db/README.md 에 있다.
 * 업무–서식 매핑(OCRWF_M·OCRWL_L)은 Portal 데이터라 대상이 아니다. 시퀀스 이름은 테이블 접근이 아니다.
 */
class OcrTableBoundaryTest {
    private static final Pattern AGENT_TABLE = Pattern.compile(
            "\\bocuoc_(ocrwf_l|ocrif_l|ocrdo_l|ocrdf_l|ocrdg_l|ocrdt_l|ocrtc_l|ocrtr_l|ocrvr_l|ocrcv_l|ocrxr_l|ocrxv_l|agent_m|token_m|tkath_m)\\b",
            Pattern.CASE_INSENSITIVE);

    @Test
    void portalMappersDoNotTouchAgentTables() throws IOException {
        Path mappers = Path.of("src/main/resources/sqlmap");
        assertTrue(Files.isDirectory(mappers), "run from the portal module directory");
        List<String> violations = new ArrayList<>();
        try (Stream<Path> files = Files.walk(mappers)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".xml")).toList()) {
                List<String> lines = Files.readAllLines(file);
                for (int i = 0; i < lines.size(); i++) {
                    if (AGENT_TABLE.matcher(lines.get(i)).find()) {
                        violations.add(mappers.relativize(file) + ":" + (i + 1) + " " + lines.get(i).trim());
                    }
                }
            }
        }
        assertEquals(List.of(), violations);
    }
}
