package com.agilesoda.common.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 요청마다 추적용 요청 ID(reqId)를 정한다.
 * 들어온 X-Request-Id 헤더가 형식에 맞으면 그대로 쓰고, 아니면 새로 만든다.
 * MDC(reqId)와 응답 헤더에 넣어 portal 로그와 Agent 로그를 같은 ID로 따라갈 수 있게 한다.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

	public static final String HEADER = "X-Request-Id";
	public static final String MDC_KEY = "reqId";

	private static final Pattern VALID_ID = Pattern.compile("[A-Za-z0-9-]{1,36}");
	private static final String[] STATIC_PREFIXES = {"/css/", "/js/", "/images/", "/fonts/", "/favicon"};

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String reqId = resolve(request.getHeader(HEADER));
		MDC.put(MDC_KEY, reqId);
		response.setHeader(HEADER, reqId);

		boolean quiet = isStatic(request);
		long started = System.nanoTime();
		if (!quiet) {
			log.debug("[요청] 수신 method={} uri={}", request.getMethod(), request.getRequestURI());
		}
		try {
			chain.doFilter(request, response);
		} finally {
			if (!quiet) {
				log.debug("[요청] 완료 method={} uri={} status={} elapsedMs={}", request.getMethod(),
						request.getRequestURI(), response.getStatus(), (System.nanoTime() - started) / 1_000_000);
			}
			MDC.remove(MDC_KEY);
		}
	}

	/** 형식에 맞는 헤더 값은 재사용하고, 없거나 형식이 다르면 UUID 앞 8자리를 쓴다. */
	static String resolve(String incoming) {
		if (incoming != null && VALID_ID.matcher(incoming).matches()) {
			return incoming;
		}
		return UUID.randomUUID().toString().substring(0, 8);
	}

	private boolean isStatic(HttpServletRequest request) {
		String path = request.getRequestURI().substring(request.getContextPath().length());
		for (String prefix : STATIC_PREFIXES) {
			if (path.startsWith(prefix)) return true;
		}
		return false;
	}
}
