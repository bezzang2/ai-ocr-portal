package com.agilesoda.module.ocr.service;

import com.agilesoda.common.response.entity.RestResponseEntity;
import com.agilesoda.common.rest.RestAgentProvider;
import com.agilesoda.module.ocr.vo.OcrResultLefrmVO;
import com.agilesoda.module.ocr.vo.OcrViewerMetadata;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sf.json.JSONObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaTypeFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class OcrViewerService {
    private static final String IMAGE_ERROR = "판독 이미지를 불러올 수 없습니다. 원본 보기 또는 다시 시도를 이용해 주세요.";
    private final OcrReqService resultService;
    private final RestAgentProvider agent;
    private final OcrAgentDataClient agentData;

    public OcrViewerMetadata metadata(long lefrmId, String wkId, String transId, String docId, String baseUrl) {
        long started = System.nanoTime();
        OcrResultLefrmVO key = resultKey(lefrmId, wkId, transId, docId);
        String view = imageView(key);
        log.debug("[판독뷰어] 메타데이터 요청 lefrmId={} wkId={} transId={} docId={} view={}",
                lefrmId, wkId, key.getTransId(), key.getDocId(), view);
        OcrViewerMetadata.Original original = null;
        // A masked document must never expose its uploaded original through this viewer.
        if (!"masked".equals(view)) {
            String name = originalName(key);
            if (name != null) {
                String mime = MediaTypeFactory.getMediaType(name).map(Object::toString)
                        .orElse("application/octet-stream");
                original = new OcrViewerMetadata.Original(url(baseUrl + "/view", key.getTransId(), docId, null), mime, name);
            } else {
                log.debug("[판독뷰어] 원본 파일 정보 없음 lefrmId={} wkId={} transId={}", lefrmId, wkId, key.getTransId());
            }
        } else {
            log.debug("[판독뷰어] 마스킹 문서라 원본 숨김 lefrmId={} wkId={} transId={} docId={}",
                    lefrmId, wkId, key.getTransId(), key.getDocId());
        }
        List<OcrViewerMetadata.Page> pages = new ArrayList<>();
        String message = null;
        try {
            JSONObject query = new JSONObject();
            query.put("view", view);
            RestResponseEntity<?> response = agent.get(agentPath(key), query);
            if (response == null || response.getStatus() != 200 || !response.isSuccess()
                    || !(response.getData() instanceof Map<?, ?> data)
                    || !key.getDocId().equals(data.get("documentId")) || !view.equals(data.get("view"))
                    || !(data.get("pages") instanceof List<?> entries) || entries.size() > 1000) {
                throw new IllegalStateException("Invalid viewer metadata");
            }
            log.debug("[판독뷰어] Agent 응답 수신 lefrmId={} transId={} docId={} view={} pageEntries={}",
                    lefrmId, key.getTransId(), key.getDocId(), view, entries.size());
            int previous = 0;
            for (Object entry : entries) {
                if (!(entry instanceof Map<?, ?> page)) throw new IllegalStateException("Invalid page");
                int number = positiveInteger(page.get("pageNumber"), 1000);
                int width = positiveInteger(page.get("width"), 30000);
                int height = positiveInteger(page.get("height"), 30000);
                if (number <= previous || (long) width * height > 100_000_000L) {
                    throw new IllegalStateException("Invalid page bounds");
                }
                previous = number;
                pages.add(new OcrViewerMetadata.Page(number, width, height,
                        url(baseUrl + "/pages/" + number + "/image", key.getTransId(), docId, view)));
            }
            if (pages.isEmpty()) {
                message = "표시할 판독 이미지가 없습니다. 처리 상태와 보관기간을 확인해 주세요.";
                log.warn("[판독뷰어] 표시할 페이지 없음 lefrmId={} wkId={} transId={} docId={} view={}",
                        lefrmId, wkId, key.getTransId(), key.getDocId(), view);
            }
        } catch (IllegalStateException ex) {
            // Expected business failure: agent returned data that failed validation.
            log.warn("[판독뷰어] Agent 응답 검증 실패 lefrmId={} wkId={} transId={} docId={} view={} cause={}",
                    lefrmId, wkId, key.getTransId(), key.getDocId(), view, ex.getMessage());
            pages.clear();
            message = IMAGE_ERROR;
        } catch (Exception ex) {
            log.error("[판독뷰어] Agent 호출 실패 lefrmId={} wkId={} transId={} docId={} view={}",
                    lefrmId, wkId, key.getTransId(), key.getDocId(), view, ex);
            pages.clear();
            message = IMAGE_ERROR;
        }
        log.info("[판독뷰어] 메타데이터 완료 lefrmId={} wkId={} transId={} docId={} view={} pages={} original={} elapsedMs={}",
                lefrmId, wkId, key.getTransId(), key.getDocId(), view, pages.size(), original != null,
                (System.nanoTime() - started) / 1_000_000);
        return new OcrViewerMetadata(key.getDocId(), view, List.copyOf(pages), original, message);
    }

    public byte[] pageImage(long lefrmId, String wkId, String transId, String docId, int pageNumber, String requestedView) {
        if (pageNumber < 1 || pageNumber > 1000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "페이지 번호가 올바르지 않습니다.");
        }
        OcrResultLefrmVO key = resultKey(lefrmId, wkId, transId, docId);
        String view = imageView(key);
        if (requestedView != null && !view.equals(requestedView)) {
            log.warn("[판독뷰어] 이미지 보기 종류 거부 lefrmId={} wkId={} transId={} requestedView={} allowedView={}",
                    lefrmId, wkId, key.getTransId(), requestedView, view);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "허용되지 않은 이미지 보기입니다.");
        }
        log.debug("[판독뷰어] 페이지 이미지 요청 lefrmId={} wkId={} transId={} docId={} page={} view={}",
                lefrmId, wkId, key.getTransId(), key.getDocId(), pageNumber, view);
        try {
            return agent.getViewerImage(agentPath(key) + "/pages/" + pageNumber + "/image", view);
        } catch (ResponseStatusException ex) {
            int status = ex.getStatusCode().value();
            if (status == 404 || status == 410 || status == 409) {
                log.warn("[판독뷰어] 페이지 이미지 없음 또는 보관기간 만료 lefrmId={} wkId={} transId={} docId={} page={} view={} agentStatus={}",
                        lefrmId, wkId, key.getTransId(), key.getDocId(), pageNumber, view, status);
                throw new ResponseStatusException(ex.getStatusCode(), "판독 이미지가 없거나 보관기간이 지났습니다.", ex);
            }
            log.error("[판독뷰어] 페이지 이미지 Agent 오류 lefrmId={} wkId={} transId={} docId={} page={} view={} agentStatus={}",
                    lefrmId, wkId, key.getTransId(), key.getDocId(), pageNumber, view, status, ex);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, IMAGE_ERROR, ex);
        } catch (Exception ex) {
            log.error("[판독뷰어] 페이지 이미지 조회 실패 lefrmId={} wkId={} transId={} docId={} page={} view={}",
                    lefrmId, wkId, key.getTransId(), key.getDocId(), pageNumber, view, ex);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, IMAGE_ERROR, ex);
        }
    }

    /**
     * 요청 원본 파일(Agent 가 제공). 마스킹 문서는 원본을 주지 않는다. 원본이 없으면 404,
     * Agent 연계 실패는 502 로 바꾸고 이곳에서 로그를 남긴다.
     */
    public RestAgentProvider.Download original(long lefrmId, String wkId, String transId, String docId) {
        OcrResultLefrmVO key = resultKey(lefrmId, wkId, transId, docId);
        if ("masked".equals(imageView(key))) {
            log.warn("[판독뷰어] 마스킹 문서 원본 요청 거부 lefrmId={} wkId={} transId={} docId={}",
                    lefrmId, wkId, key.getTransId(), key.getDocId());
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "마스킹 문서는 판독 이미지로 확인해 주세요.");
        }
        log.debug("[판독뷰어] 원본 요청 lefrmId={} wkId={} transId={} docId={}", lefrmId, wkId, key.getTransId(), key.getDocId());
        try {
            return agentData.original(key.getTransId(), key.getDocId());
        } catch (ResponseStatusException ex) {
            int status = ex.getStatusCode().value();
            if (status == 404 || status == 403) {
                log.warn("[판독뷰어] 원본 파일 없음 lefrmId={} wkId={} transId={} docId={} agentStatus={}",
                        lefrmId, wkId, key.getTransId(), key.getDocId(), status);
                throw new ResponseStatusException(ex.getStatusCode(), "원본 파일을 찾을 수 없습니다.", ex);
            }
            log.error("[판독뷰어] 원본 Agent 오류 lefrmId={} wkId={} transId={} docId={} agentStatus={}",
                    lefrmId, wkId, key.getTransId(), key.getDocId(), status, ex);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "원본 파일을 불러올 수 없습니다.", ex);
        } catch (Exception ex) {
            log.error("[판독뷰어] 원본 조회 실패 lefrmId={} wkId={} transId={} docId={}",
                    lefrmId, wkId, key.getTransId(), key.getDocId(), ex);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "원본 파일을 불러올 수 없습니다.", ex);
        }
    }

    /** 원본 파일 이름(저장 파일명.확장자). 판독 문서에 맞는 요청 파일이 없으면 null */
    private static String originalName(OcrResultLefrmVO key) {
        if (key.getFileNm() == null || key.getFileNm().isBlank()) {
            return null;
        }
        return key.getFileExt() == null || key.getFileExt().isBlank() ? key.getFileNm() : key.getFileNm() + "." + key.getFileExt();
    }

    private OcrResultLefrmVO resultKey(long lefrmId, String wkId, String transId, String docId) {
        OcrResultLefrmVO key = resultService.resolveDocument(lefrmId, wkId, transId, docId);
        if (key == null || !validId(key.getTransId()) || !validId(key.getDocId())
                || transId != null && !transId.isBlank() && !transId.equals(key.getTransId())) {
            log.warn("[판독뷰어] 판독 문서 키 없음 lefrmId={} wkId={} transId={}", lefrmId, wkId, transId);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "판독 문서를 찾을 수 없습니다.");
        }
        return key;
    }

    private static boolean validId(String value) {
        return value != null && value.matches("[A-Za-z0-9_-]{1,80}");
    }

    private static String imageView(OcrResultLefrmVO key) {
        return "Y".equalsIgnoreCase(key.getRedactYn()) ? "masked" : "processed";
    }

    private static String agentPath(OcrResultLefrmVO key) {
        return "/api/v1/ocr/viewer/transactions/" + key.getTransId() + "/documents/" + key.getDocId();
    }

    private static int positiveInteger(Object value, int maximum) {
        if (!(value instanceof Number number) || number.doubleValue() != number.intValue()
                || number.intValue() <= 0 || number.intValue() > maximum) {
            throw new IllegalStateException("Invalid metadata bounds");
        }
        return number.intValue();
    }

    private static String url(String path, String transId, String docId, String view) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath(path).queryParam("transId", transId);
        if (docId != null && !docId.isBlank()) builder.queryParam("docId", docId);
        if (view != null) builder.queryParam("view", view);
        return builder.build().encode().toUriString();
    }
}
