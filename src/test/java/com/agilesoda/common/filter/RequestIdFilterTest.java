package com.agilesoda.common.filter;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class RequestIdFilterTest {
    private final RequestIdFilter filter = new RequestIdFilter();

    private String run(MockHttpServletRequest request, MockHttpServletResponse response, AtomicReference<String> inChain)
            throws Exception {
        FilterChain chain = (req, res) -> inChain.set(MDC.get(RequestIdFilter.MDC_KEY));
        filter.doFilter(request, response, chain);
        return response.getHeader(RequestIdFilter.HEADER);
    }

    @Test
    void reusesValidIncomingHeaderAndClearsMdc() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/module/ocr/result");
        request.addHeader(RequestIdFilter.HEADER, "abc-123-DEF");
        AtomicReference<String> inChain = new AtomicReference<>();

        String header = run(request, new MockHttpServletResponse(), inChain);

        assertEquals("abc-123-DEF", header);
        assertEquals("abc-123-DEF", inChain.get());
        assertNull(MDC.get(RequestIdFilter.MDC_KEY));
    }

    @Test
    void generatesEightCharIdWhenHeaderMissingOrInvalid() throws Exception {
        AtomicReference<String> inChain = new AtomicReference<>();
        String missing = run(new MockHttpServletRequest("GET", "/index"), new MockHttpServletResponse(), inChain);
        assertEquals(8, missing.length());
        assertEquals(missing, inChain.get());

        MockHttpServletRequest bad = new MockHttpServletRequest("GET", "/index");
        bad.addHeader(RequestIdFilter.HEADER, "bad id\r\nX-Evil: 1");
        String replaced = run(bad, new MockHttpServletResponse(), new AtomicReference<>());
        assertEquals(8, replaced.length());
        assertTrue(replaced.matches("[A-Za-z0-9-]{8}"));
        assertNull(MDC.get(RequestIdFilter.MDC_KEY));
    }

    @Test
    void clearsMdcEvenWhenChainFails() {
        FilterChain failing = (req, res) -> { throw new IllegalStateException("boom"); };
        assertThrows(IllegalStateException.class, () -> filter.doFilter(
                new MockHttpServletRequest("GET", "/index"), new MockHttpServletResponse(), failing));
        assertNull(MDC.get(RequestIdFilter.MDC_KEY));
    }

    @Test
    void rejectsTooLongIncomingId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/index");
        request.addHeader(RequestIdFilter.HEADER, "a".repeat(37));
        String header = run(request, new MockHttpServletResponse(), new AtomicReference<>());
        assertEquals(8, header.length());
    }
}
