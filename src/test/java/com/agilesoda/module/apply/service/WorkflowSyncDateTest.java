package com.agilesoda.module.apply.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WorkflowSyncDateTest {
    @Test void convertsEngineTimestampsForTheDatabase() {
        assertTrue(AgentApiService.toDbDateTime("2026-09-29T15:40:10.185925+09:00").matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}"));
        assertNotNull(AgentApiService.toDbDateTime("2026-09-29T06:40:10Z"));
        assertNull(AgentApiService.toDbDateTime(null));
        assertNull(AgentApiService.toDbDateTime("not-a-date"));
    }
}
