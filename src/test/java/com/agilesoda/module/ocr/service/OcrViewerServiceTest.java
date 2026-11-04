package com.agilesoda.module.ocr.service;

import com.agilesoda.common.response.entity.RestResponseEntity;
import com.agilesoda.common.rest.RestAgentProvider;
import com.agilesoda.module.ocr.vo.OcrResultLefrmVO;
import com.agilesoda.module.ocr.vo.OcrViewerMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OcrViewerServiceTest {
    private final OcrReqService results = mock(OcrReqService.class);
    private final RestAgentProvider agent = mock(RestAgentProvider.class);
    private final OcrAgentDataClient agentData = mock(OcrAgentDataClient.class);
    private final OcrViewerService service = new OcrViewerService(results, agent, agentData);
    private OcrResultLefrmVO key;

    @BeforeEach
    void resultKey() {
        key = new OcrResultLefrmVO();
        key.setTransId("trans-1");
        key.setDocId("doc-1");
        key.setRedactYn("N");
        when(results.resolveDocument(7L, "work-1", "trans-1", null)).thenReturn(key);
    }

    @Test
    void processedMetadataUsesBoundDocumentAndContextRelativePageUrls() throws Exception {
        key.setFileNm("scan");
        key.setFileExt("tiff");
        metadata("processed", List.of(Map.of("pageNumber", 2, "width", 1200, "height", 1800)));

        OcrViewerMetadata result = service.metadata(7L, "work-1", "trans-1", null, "/aiportal/module/ocr/result/lefrm/work-1/7");

        assertEquals("doc-1", result.documentId());
        assertEquals("processed", result.imageView());
        assertEquals(2, result.pages().get(0).pageNumber());
        assertEquals("/aiportal/module/ocr/result/lefrm/work-1/7/pages/2/image?transId=trans-1&view=processed",
                result.pages().get(0).imageUrl());
        assertEquals("image/tiff", result.original().mimeType());
        assertEquals("scan.tiff", result.original().fileName());
        assertNull(result.message());
    }

    @Test
    void maskedMetadataNeverOffersOriginalOrFallsBackToProcessed() throws Exception {
        key.setRedactYn("Y");
        when(agent.get(anyString(), any())).thenThrow(new IllegalStateException("upstream"));

        OcrViewerMetadata result = service.metadata(7L, "work-1", "trans-1", null, "/viewer");

        assertEquals("masked", result.imageView());
        assertNull(result.original());
        assertTrue(result.pages().isEmpty());
        assertNotNull(result.message());
        verifyNoInteractions(agentData);
        verify(agent).get(eq("/api/v1/ocr/viewer/transactions/trans-1/documents/doc-1"),
                argThat(query -> "masked".equals(query.getString("view"))));
    }

    @Test
    void invalidOrMixedDocumentMetadataIsNotExposed() throws Exception {
        metadata("processed", List.of(Map.of("pageNumber", 1, "width", 1200, "height", 1800),
                Map.of("pageNumber", 1, "width", 1200, "height", 1800)));
        assertTrue(service.metadata(7L, "work-1", "trans-1", null, "/viewer").pages().isEmpty());

        RestResponseEntity<Object> response = new RestResponseEntity<>();
        response.setStatus(200);
        response.setSuccess(true);
        response.setData(Map.of("documentId", "other-document", "view", "processed", "pages", List.of()));
        doReturn(response).when(agent).get(anyString(), any());
        assertTrue(service.metadata(7L, "work-1", "trans-1", null, "/viewer").pages().isEmpty());
    }

    @Test
    void transactionMismatchAndUnsafeEngineIdsCannotReachAgent() {
        key.setTransId("other-transaction");
        assertEquals(404, assertThrows(ResponseStatusException.class,
                () -> service.metadata(7L, "work-1", "trans-1", null, "/viewer")).getStatusCode().value());
        key.setTransId("trans-1");
        key.setDocId("../secret");
        assertThrows(ResponseStatusException.class, () -> service.pageImage(7L, "work-1", "trans-1", null, 1, null));
        verifyNoInteractions(agent);
    }

    @Test
    void maskedViewCannotRequestProcessedPageOrUploadedOriginal() {
        key.setRedactYn("Y");
        assertEquals(403, assertThrows(ResponseStatusException.class,
                () -> service.pageImage(7L, "work-1", "trans-1", null, 1, "processed")).getStatusCode().value());
        key.setFileNm("scan");
        assertEquals(403, assertThrows(ResponseStatusException.class,
                () -> service.original(7L, "work-1", "trans-1", null)).getStatusCode().value());
        verifyNoInteractions(agent, agentData);
    }

    @Test
    void originalIsRelayedFromAgentAndFailuresAreMapped() {
        RestAgentProvider.Download file = new RestAgentProvider.Download(new byte[]{1}, MediaType.IMAGE_JPEG);
        when(agentData.original("trans-1", "doc-1")).thenReturn(file);
        assertSame(file, service.original(7L, "work-1", "trans-1", null));

        when(agentData.original("trans-1", "doc-1")).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "원본 파일이 없습니다."));
        assertEquals(404, assertThrows(ResponseStatusException.class,
                () -> service.original(7L, "work-1", "trans-1", null)).getStatusCode().value());

        reset(agentData);
        when(agentData.original("trans-1", "doc-1")).thenThrow(new IllegalStateException("Agent down"));
        assertEquals(502, assertThrows(ResponseStatusException.class,
                () -> service.original(7L, "work-1", "trans-1", null)).getStatusCode().value());
    }

    @Test
    void pageBoundsAndExpirationAreHandled() throws Exception {
        assertEquals(400, assertThrows(ResponseStatusException.class,
                () -> service.pageImage(7L, "work-1", "trans-1", null, 0, null)).getStatusCode().value());
        when(agent.getViewerImage(anyString(), eq("processed")))
                .thenThrow(new ResponseStatusException(HttpStatus.GONE, "private upstream details"));
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.pageImage(7L, "work-1", "trans-1", null, 1, "processed"));
        assertEquals(410, ex.getStatusCode().value());
        assertFalse(ex.getReason().contains("private"));
    }

    @Test
    void validPageImageUsesResolvedTransactionAndDocument() throws Exception {
        byte[] expected = {1, 2, 3};
        when(agent.getViewerImage("/api/v1/ocr/viewer/transactions/trans-1/documents/doc-1/pages/3/image", "processed"))
                .thenReturn(expected);
        assertSame(expected, service.pageImage(7L, "work-1", "trans-1", null, 3, null));
    }

    private void metadata(String view, List<Map<String, Integer>> pages) throws Exception {
        RestResponseEntity<Object> response = new RestResponseEntity<>();
        response.setStatus(200);
        response.setSuccess(true);
        response.setData(Map.of("documentId", "doc-1", "view", view, "pages", pages));
        doReturn(response).when(agent).get(anyString(), any());
    }
}
