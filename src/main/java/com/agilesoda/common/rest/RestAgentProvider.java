package com.agilesoda.common.rest;

import com.agilesoda.common.response.entity.RestResponseEntity;
import com.agilesoda.common.util.StringUtil;
import lombok.extern.slf4j.Slf4j;
import net.sf.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;

@Slf4j
@Component
public class RestAgentProvider {
	private final String url;

	private final String userId;

	private final String userPwd;

	private final boolean tokenEnabled;

	private String token;

	public RestAgentProvider(@Value("${aiocr.agent.url}") String url,
							 @Value("${aiocr.agent.id}") String userId,
							 @Value("${aiocr.agent.passwd}") String userPwd,
							 @Value("${aiocr.agent.token-enabled}") boolean tokenEnabled) {
		this.url = url;
		this.userId = userId;
		this.userPwd = userPwd;
		this.tokenEnabled = tokenEnabled;

		if(this.tokenEnabled) {
			try {
				this.token = getToken();
			} catch (Exception e) {
				log.error("[Agent호출] 기동 시 Agent 토큰 요청 실패, 토큰 없이 계속 진행 url={}", url, e);
			}

		}
	}

	/** Bounded binary proxy using the same Agent authentication as JSON requests. */
	public byte[] getViewerImage(String uri, String view) throws Exception {
		final int maximumBytes = 32 * 1024 * 1024;
		final long started = System.nanoTime();
		byte[] image = RestUtil.getBypassClient(this.url, this.token).get()
				.uri(builder -> builder.path(uri).queryParam("view", view).build())
				.accept(MediaType.IMAGE_PNG)
				.exchange((request, response) -> {
					if (!response.getStatusCode().is2xxSuccessful()) {
						throw new ResponseStatusException(response.getStatusCode(), "판독 이미지를 불러올 수 없습니다.");
					}
					if (!MediaType.IMAGE_PNG.equals(response.getHeaders().getContentType())
							|| response.getHeaders().getContentLength() > maximumBytes) {
						throw new IOException("Invalid page image response");
					}
					byte[] bytes = response.getBody().readNBytes(maximumBytes + 1);
					byte[] signature = {(byte) 137, 80, 78, 71, 13, 10, 26, 10};
					if (bytes.length > maximumBytes || bytes.length < signature.length
							|| !Arrays.equals(signature, Arrays.copyOf(bytes, signature.length))) {
						throw new IOException("Invalid page image data");
					}
					return bytes;
				});
		log.info("[Agent호출] 판독 이미지 수신 uri={} view={} bytes={} elapsedMs={}", uri, view,
				image == null ? 0 : image.length, (System.nanoTime() - started) / 1_000_000);
		return image;
	}

	/** 응답 본문과 형식. 원본 파일 중계에 쓴다. */
	public record Download(byte[] body, MediaType contentType) { }

	/**
	 * 바이너리 파일 조회(원본 파일). 2xx 가 아니면 상태를 그대로 담은 ResponseStatusException 을 던진다.
	 * maximumBytes 를 넘으면 IOException.
	 */
	public Download download(String uri, int maximumBytes) throws Exception {
		final long started = System.nanoTime();
		Download download = RestUtil.getBypassClient(this.url, this.token).get()
				.uri(builder -> builder.path(uri).build())
				.accept(MediaType.ALL)
				.exchange((request, response) -> {
					if (!response.getStatusCode().is2xxSuccessful()) {
						throw new ResponseStatusException(response.getStatusCode(), "파일을 불러올 수 없습니다.");
					}
					if (response.getHeaders().getContentLength() > maximumBytes) {
						throw new IOException("Download exceeds limit");
					}
					byte[] bytes = response.getBody().readNBytes(maximumBytes + 1);
					if (bytes.length > maximumBytes) {
						throw new IOException("Download exceeds limit");
					}
					MediaType type = response.getHeaders().getContentType();
					return new Download(bytes, type != null ? type : MediaType.APPLICATION_OCTET_STREAM);
				});
		log.info("[Agent호출] 파일 다운로드 수신 uri={} bytes={} elapsedMs={}", uri, download.body().length,
				(System.nanoTime() - started) / 1_000_000);
		return download;
	}

	/**
	 * Agent 업무 데이터 API 호출. 4xx·5xx 도 예외 없이 본문(status·message)을 담아 돌려준다.
	 * 요청 본문에는 OCR 인식값이 들어갈 수 있어 로그에는 경로·상태·소요 시간만 남긴다.
	 */
	public RestResponseEntity<?> exchange(HttpMethod method, String uri, Map<String, String> query, Object body) throws Exception {
		final long started = System.nanoTime();
		log.debug("[Agent호출] {} 시작 uri={} queryCount={} hasBody={}", method, uri, query == null ? 0 : query.size(), body != null);
		RestClient.RequestBodySpec request = RestUtil.getBypassClient(this.url, this.token).method(method)
				.uri(builder -> {
					if (query != null) query.forEach((name, value) -> { if (value != null) builder.queryParam(name, value); });
					return builder.path(uri).build();
				});
		if (body != null) request.body(body);
		RestResponseEntity<?> response = request.retrieve()
				.onStatus(status -> true, (req, res) -> { })
				.body(RestResponseEntity.class);
		logCall(String.valueOf(method), uri, started, response);
		return response;
	}

	/**
	 * rest api get 메소드
	 * @param uri(String) : 호출할 uri(ex: /aaa/bbb)
	 * @return
	 */
	public RestResponseEntity<?> get(String uri) throws Exception{
		log.debug("[Agent호출] GET 시작 uri={}", uri);
		final long started = System.nanoTime();
		RestResponseEntity<?> response = RestUtil.getBypassClient(this.url, this.token).get()
				.uri(uriBuilder -> uriBuilder.path(uri).build())
				.retrieve()
				.body(RestResponseEntity.class);
		logCall("GET", uri, started, response);
		return response;
	}

	/**
	 * rest api get 메소드
	 * @param uri(String) : 호출할 uri(ex: /aaa/bbb)
	 * @param param(JSONObject) : 	파라미터
	 * @return
	 */
	public RestResponseEntity<?> get(String uri, JSONObject param) throws Exception{
		String queryString = StringUtil.urlDecodeUTF8(param);
		log.debug("[Agent호출] GET 시작 uri={} queryParamCount={}", uri, param == null ? 0 : param.size());

		final long started = System.nanoTime();
		RestResponseEntity<?> response = RestUtil.getBypassClient(this.url, this.token).get()
				.uri(uriBuilder -> uriBuilder.path(uri).query(queryString).build())
				.retrieve()
				.body(RestResponseEntity.class);
		logCall("GET", uri, started, response);
		return response;
	}

	/**
	 * rest api post 메소드
	 * @param uri(String) : 호출할 uri (ex: /aaa/bbb)
	 * @param param(JSONObject) : 파라미터
	 * @return
	 */
	public RestResponseEntity<?> post(String uri, JSONObject param) throws Exception{
		log.debug("[Agent호출] POST 시작 uri={} paramKeyCount={}", uri, param == null ? 0 : param.size());

		final long started = System.nanoTime();
		RestResponseEntity<?> response = RestUtil.getBypassClient(this.url, this.token).post()
				.uri(uriBuilder -> uriBuilder.path(uri).build())
				.body(param)
				.retrieve()
				.body(RestResponseEntity.class);
		logCall("POST", uri, started, response);
		return response;
	}

	/** Agent 호출 결과 요약(경로·상태·소요 시간). 응답 본문은 남기지 않는다. */
	private void logCall(String method, String uri, long startedNanos, RestResponseEntity<?> response) {
		log.info("[Agent호출] {} 완료 uri={} status={} elapsedMs={}", method, uri,
				response != null ? response.getStatus() : null, (System.nanoTime() - startedNanos) / 1_000_000);
	}

	/**
	 * multipart/form-data POST. 단말 mock 의 판독요청 중계용이다.
	 * Agent 의 4xx·5xx 도 예외로 바꾸지 않고 본문(status·message)을 그대로 돌려준다.
	 * @param parts 파라미터와 파일(Resource) 파트
	 */
	public RestResponseEntity<?> postMultipart(String uri, MultiValueMap<String, Object> parts) throws Exception {
		log.debug("[Agent호출] multipart POST 시작 uri={} parts={}", uri, parts.keySet());
		final long started = System.nanoTime();
		RestResponseEntity<?> result = RestUtil.getBypassClient(this.url, this.token).post()
				.uri(uriBuilder -> uriBuilder.path(uri).build())
				.contentType(MediaType.MULTIPART_FORM_DATA)
				.body(parts)
				.exchange((request, response) -> {
					RestResponseEntity<?> body = response.getHeaders().getContentLength() == 0
							? null : response.bodyTo(RestResponseEntity.class);
					if (body == null) {
						body = new RestResponseEntity<>();
						body.setStatus(response.getStatusCode().value());
					}
					return body;
				});
		logCall("POST", uri, started, result);
		return result;
	}

	/**
	 * 연계-Agent에 헤더에 세팅할 Access Token을 요청한다.(/api/token)
	 * 401오류(UNAUTHORIZED) 시 토큰 발행을 요청한다.(/api/token/make)
	 * @return accessToken
	 */
	private String getToken() throws Exception{
		RestClient restClient = RestUtil.getBypassClient(this.url, this.token);

		RestResponseEntity<?> restResponseEntity = restClient.get()
				.uri(uriBuilder -> uriBuilder.path("/api/v1/token").build())
				.retrieve()
				.body(RestResponseEntity.class);

		//	401오류가 나면 token 재발행을 요청한다.
		if(restResponseEntity.getStatus() == HttpStatus.UNAUTHORIZED.value()){
			//	토큰 발행 요청 시 필요한 사용자ID, 비밀번호를 세팅한다.
			//	비밀번호는 application.yml파일에 AES256으로 암호화된 값이다.
			JSONObject jobj = new JSONObject();
			jobj.put("userId", this.userId);
			jobj.put("userPwd", this.userPwd);

			restResponseEntity = restClient.post()
					.uri(uriBuilder -> uriBuilder.path("/api/v1/token").build())
					.body(jobj)
					.retrieve()
					.body(RestResponseEntity.class);
		}

		return RestUtil.getData(restResponseEntity.getData(), String.class);
	}
}
