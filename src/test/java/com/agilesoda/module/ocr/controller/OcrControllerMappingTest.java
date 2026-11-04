package com.agilesoda.module.ocr.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

/** 화면 매핑: 외부 폴더를 먼저 읽고, 없으면 패키지에 포함한 파일을 읽는다. */
class OcrControllerMappingTest {

    @TempDir
    Path mappingDir;

    private OcrController controller;

    @BeforeEach
    void setUp() {
        controller = new OcrController(null, null, null, null);
        ReflectionTestUtils.setField(controller, "mappingDir", mappingDir.toString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"PCU062000SC00", "PCU062410SC00", "VMM072000SC00", "VMM075401SC00", "VMM075480SC00"})
    void readsPackagedFileWhenExternalIsMissing(String screenNo) {
        ResponseEntity<Object> response = controller.resultMapping(screenNo);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertThat(body.get("screenNo")).isEqualTo(screenNo);
        assertThat((List<?>) body.get("fieldMappings")).isNotEmpty();
    }

    @Test
    void externalFileWinsOverPackagedFile() throws Exception {
        Files.writeString(mappingDir.resolve("매핑정보_VMM072000SC00.json"),
                "﻿{\"screenNo\":\"EXTERNAL\"}", StandardCharsets.UTF_8);

        ResponseEntity<Object> response = controller.resultMapping("VMM072000SC00");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Map<?, ?>) response.getBody()).get("screenNo")).isEqualTo("EXTERNAL");
    }

    @Test
    void returnsNotFoundWhenNoFileExists() {
        assertThat(controller.resultMapping("NOFILE00000").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void rejectsPathTraversal() {
        assertThat(controller.resultMapping("../../secret").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void reportsBrokenExternalFile() throws Exception {
        Files.writeString(mappingDir.resolve("매핑정보_VMM072000SC00.json"), "{broken", StandardCharsets.UTF_8);

        assertThat(controller.resultMapping("VMM072000SC00").getStatusCode())
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
