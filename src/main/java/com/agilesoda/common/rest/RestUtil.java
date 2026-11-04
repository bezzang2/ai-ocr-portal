package com.agilesoda.common.rest;

import com.agilesoda.common.filter.RequestIdFilter;
import com.agilesoda.common.util.StringUtil;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.ObjectMapper;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.net.http.HttpClient;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

@Slf4j
public class RestUtil {
	/** 현재 스레드의 요청 ID(MDC)를 X-Request-Id 헤더로 실어 Agent 로그와 같은 ID로 추적하게 한다. */
	private static void addRequestId(RestClient.RequestHeadersSpec<?> spec) {
		String reqId = MDC.get(RequestIdFilter.MDC_KEY);
		if (reqId != null) {
			spec.header(RequestIdFilter.HEADER, reqId);
		}
	}

	public static RestClient getClient(String url) {
		return RestClient.builder()
				.baseUrl(url)
				.defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
				.defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
				.defaultRequest(RestUtil::addRequestId)
				.build();
	}
	
	public static RestClient getClient(String url, String token) {
		RestClient restClient = null;
		if("".equals(StringUtil.nvl(token))){
			restClient = RestClient.builder()
					.baseUrl(url)
					.defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
					.defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
					.defaultRequest(RestUtil::addRequestId)
					.build();
		}
		else{
			restClient = RestClient.builder()
					.baseUrl(url)
					.defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
					.defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
					.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token)
					.defaultRequest(RestUtil::addRequestId)
					.build();
		}
		return restClient;
	}

	public static RestClient getBypassClient(String url, String token) throws Exception{
		//	1. 모든 인증서를 조건 없이 신뢰하는 TrustManager
		TrustManager[] trustAllCerts = new TrustManager[]{
				new X509TrustManager() {
					@Override
					public void checkClientTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {}

					@Override
					public void checkServerTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {}

					@Override
					public X509Certificate[] getAcceptedIssuers() {
						return new X509Certificate[0];
					}
				}
		};

		//	2. SSLContext 초기화
		SSLContext sslContext = SSLContext.getInstance("SSL");
		sslContext.init(null, trustAllCerts, new SecureRandom());

		//	3. JDK HttpClient에 SSLContext와 호스트네임 검증 우회 설정
		HttpClient httpClient = HttpClient.newBuilder()
				.sslContext(sslContext)
				.sslParameters(new SSLParameters(){{
					setEndpointIdentificationAlgorithm("");
				}})
				.build();

		RestClient restClient = null;
		if("".equals(StringUtil.nvl(token))){
			restClient = RestClient.builder()
					.requestFactory(new JdkClientHttpRequestFactory(httpClient))
					.baseUrl(url)
					.defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
					.defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
					.defaultRequest(RestUtil::addRequestId)
					.build();
		}
		else{
			restClient = RestClient.builder()
					.baseUrl(url)
					.requestFactory(new JdkClientHttpRequestFactory(httpClient))
					.defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
					.defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
					.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token)
					.defaultRequest(RestUtil::addRequestId)
					.build();
		}
		return restClient;
	}
	
	public static <T> @Nullable T getData(Object obj, Class<T> toType) {
		T t;
		try {
			ObjectMapper om = new ObjectMapper();
			t = om.convertValue(obj, toType);
		}
		catch(IllegalArgumentException e) {
			log.debug("[변환] 응답 데이터 타입 변환 실패, null 반환 toType={}", toType.getSimpleName(), e);
			t = null;
		}
		return t;
	}
	

}
