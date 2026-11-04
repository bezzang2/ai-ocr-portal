package com.agilesoda.module.ocr.controller;

import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.agilesoda.common.rest.RestAgentProvider;
import com.agilesoda.common.menu.ReviewMenuNavigation;
import com.agilesoda.module.admin.vo.SsoLoginUserVO;
import com.agilesoda.module.ocr.service.OcrReqService;
import com.agilesoda.module.ocr.service.OcrReviewService;
import com.agilesoda.module.ocr.vo.OcrResultWkGroupVO;
import com.agilesoda.module.ocr.vo.OcrReviewTransactionVO;
import com.agilesoda.module.ocr.service.OcrViewerService;
import com.agilesoda.module.ocr.vo.OcrViewerMetadata;
import com.agilesoda.module.ocr.vo.OcrFldVO;
import com.agilesoda.module.ocr.vo.OcrResultTblVO;
import jakarta.servlet.http.HttpSession;
import java.time.format.DateTimeFormatter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Pattern;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import java.nio.charset.StandardCharsets;

@Slf4j
@Controller
@RequestMapping("/module")
@RequiredArgsConstructor
@Tag(name = "판독결과 조회", description = "판독 이미지·필드·표·원본 JSON 조회와 결과 갱신")
public class OcrController {

    private final OcrReqService ocrReqService;
    private final ReviewMenuNavigation reviewMenuNavigation;
    private final OcrViewerService ocrViewerService;
    private final OcrReviewService ocrReviewService;

    /** 엔진 트랜잭션 ID(UUID 형식). Agent 경로에 그대로 넣으므로 형식을 제한한다. */
    private static final Pattern TRANS_ID = Pattern.compile("[A-Za-z0-9-]{1,50}");

    /** 판독결과 화면 "원본 JSON" 노출 여부(엔진 결과 원본, OCR 원문 포함). 환경 변수 AIOCR_RAW_RESULT_ENABLED */
    @Value("${aiocr.raw-result.enabled:true}")
    private boolean rawResultEnabled;

    /**
     * eKEOS 화면 필드 매핑 파일 디렉토리(옵션1: 로컬 매핑 파일).
     * 화면번호별 매핑정보_{scrn_no}.json 을 읽는다. 환경 변수 AIOCR_MAPPING_DIR 로 재정의 가능.
     */
    @Value("${aiocr.mapping.dir:C:/KJB_DEV/smartpi/workspace/workspace/aiocr/CONFIG/mapping}")
    private String mappingDir;

    /** 화면번호 형식 제한(경로 탐색 방지). eKEOS 화면번호(SCN_NO, 예: VMM072000SC00). */
    private static final Pattern SCREEN_NO = Pattern.compile("[A-Za-z0-9_-]{1,40}");

    /**
     * 판독결과 검토 진입점. transId·scrn_no 가 없으면 판독작업 목록, 있으면 상세다.
     * 서식간 검증 상세(transId)와 BPR_SCAN 웹뷰(scrn_no)는 주소를 바꾸지 않고 바로 상세로 들어온다.
     *
     * @param list 상세에서 목록으로 돌아갈 조회 조건(목록의 listQuery). 이전·다음 판독작업 계산에도 쓴다
     */
    @GetMapping("/ocr/result")
    public String list(@RequestParam(value = "wkId", required = false) String wkId,
            @RequestParam(value = "transId", required = false) String transId,
            @RequestParam(value = "scrn_no", required = false) String scrnNo,
            @RequestParam(value = "menuId", defaultValue = "") String menuId,
            @RequestParam(value = "reviewStatus", required = false) String reviewStatus,
            @RequestParam(value = "mine", defaultValue = "false") boolean mine,
            @RequestParam(value = "from", required = false) String from,
            @RequestParam(value = "to", required = false) String to,
            @RequestParam(value = "view", required = false) String view,
            @RequestParam(value = "page", required = false) String page,
            @RequestParam(value = "pageSize", required = false) String pageSize,
            @RequestParam(value = "list", required = false) String list,
            Model model, HttpSession session) {

        applyMenu(model, session);
        if (isBlank(transId) && isBlank(scrnNo)) {
            return reviewList(wkId, reviewStatus, mine, from, to, view, page, pageSize, model, session);
        }
        // 외부에서 transId만으로 호출하면 왼쪽 메뉴·상단 헤더 없이 상세만 보인다
        model.addAttribute("embedded", !isBlank(transId) && isBlank(wkId) && isBlank(menuId));
        return reviewDetail(wkId, transId, scrnNo, list, model, session);
//        applyMenu(model, session);
//        if (isBlank(transId) && isBlank(scrnNo)) {
//            return reviewList(wkId, reviewStatus, mine, from, to, view, page, pageSize, model, session);
//        }
//        return reviewDetail(wkId, transId, scrnNo, list, model, session);
    }

    /** 판독작업 목록. 조회 실패·거절이면 화면은 열고 loadError 로 안내한다. */
    private String reviewList(String wkId, String reviewStatus, boolean mine, String from, String to, String view, String page, String pageSize,
                              Model model, HttpSession session) {
        ReviewListQuery query = ReviewListQuery.of(wkId, reviewStatus, mine, from, to, view, page, pageSize);
        List<OcrReviewTransactionVO> rows = List.of();
        List<OcrResultWkGroupVO> workflows = List.of();
        long started = System.nanoTime();
        try {
            workflows = ocrReqService.getWorkflowList();
            rows = searchReviewTransactions(query, session);
            log.info("[판독결과] 목록 조회 완료 {} rows={} elapsedMs={}", query, rows.size(), (System.nanoTime() - started) / 1_000_000);
        } catch (ResponseStatusException e) {
            if (!e.getStatusCode().is4xxClientError()) throw e;
            log.warn("[판독결과] 목록 조회 거절 status={} {} reason={}", e.getStatusCode().value(), query, e.getReason());
            model.addAttribute("loadError", e.getReason());
        } catch (Exception e) {
            // Agent·DB 연계 경계다. 화면은 열고 안내만 보인다.
            log.error("[판독결과] 목록 조회 실패 {}", query, e);
            model.addAttribute("loadError", "판독 결과를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.");
        }
        model.addAttribute("workflowList", workflows);
        model.addAttribute("reviewTransactions", rows);
        model.addAttribute("reviewStatusLabels", OcrReviewTransactionVO.STATUS_LABELS);
        model.addAttribute("wkId", query.wkId());
        model.addAttribute("reviewStatus", query.reviewStatus());
        model.addAttribute("mine", query.mine());
        model.addAttribute("from", query.from().toString());
        model.addAttribute("to", query.to().toString());
        model.addAttribute("view", query.view());
        model.addAttribute("page", query.page());
        model.addAttribute("pageSize", query.pageSize());
        model.addAttribute("listQuery", query.toQueryString());
        model.addAttribute("reviewer", ocrReviewService.reviewer((SsoLoginUserVO) session.getAttribute("ssoLoginUser")));
        return "module/ocr/ocr-result-list";
    }

    /** 판독작업 상세. 서식목록은 지금까지처럼 wkGroupList 로 주고, 요약줄·이전·다음·목록 복귀 값을 더한다. */
    private String reviewDetail(String wkId, String transId, String scrnNo, String list, Model model, HttpSession session) {
        List<OcrResultWkGroupVO> wkGroupList;
        long started = System.nanoTime();
        log.debug("[판독결과] 화면 요청 wkId={} transId={} userId={}", wkId, transId, SsoLoginUserVO.userIdOf(session));
        try {
            wkGroupList = ocrReqService.getResultWkGroupList(wkId, transId);
            ocrReviewService.applyReviewStates(wkGroupList);
            log.debug("[판독결과] 서식목록 조회 완료 wkId={} transId={} groupCount={} elapsedMs={}", wkId, transId,
                    wkGroupList.size(), (System.nanoTime() - started) / 1_000_000);
        } catch (Exception e) {
            // 판독작업·검토 상태는 Agent API 로 받는다. 연계 실패 시 화면은 열고 안내만 보인다.
            log.error("[판독결과] 서식목록 조회 실패 wkId={} transId={}", wkId, transId, e);
            wkGroupList = List.of();
            model.addAttribute("loadError", "판독 결과를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.");
        }
        model.addAttribute("wkGroupList", wkGroupList);
        model.addAttribute("reviewer", ocrReviewService.reviewer((SsoLoginUserVO) session.getAttribute("ssoLoginUser")));
        model.addAttribute("rawResultEnabled", rawResultEnabled);
        model.addAttribute("transId", transId);
        model.addAttribute("scrnNo", scrnNo);
        if (!isBlank(transId)) {
            applyDetailNavigation(transId, list, model, session);
        }
        return "module/ocr/ocr-result";
    }

    /**
     * 상세 요약줄(reviewTransaction)과 이전·다음 판독작업(prevTransaction·nextTransaction), 목록 복귀 조건(listQuery).
     * 부가 정보라 실패해도 상세는 연다. 조건 없이 바로 들어오면(list 없음) 이전·다음은 없다.
     */
    private void applyDetailNavigation(String transId, String list, Model model, HttpSession session) {
        ReviewListQuery query = isBlank(list) ? null : ReviewListQuery.parse(list);
        model.addAttribute("listQuery", query == null ? "" : query.toQueryString());
        try {
            List<OcrReviewTransactionVO> current = ocrReviewService.reviewTransactions(null, transId, null, null, null, null);
            model.addAttribute("reviewTransaction", current.isEmpty() ? null : current.get(0));
            if (query == null) return;
            List<OcrReviewTransactionVO> rows = searchReviewTransactions(query, session);
            for (int i = 0; i < rows.size(); i++) {
                if (!transId.equals(rows.get(i).getTransId())) continue;
                model.addAttribute("prevTransaction", i > 0 ? rows.get(i - 1) : null);
                model.addAttribute("nextTransaction", i < rows.size() - 1 ? rows.get(i + 1) : null);
                break;
            }
        } catch (Exception e) {
            log.warn("[판독결과] 상세 요약·이동 조회 실패, 없이 표시 transId={}", transId, e);
        }
    }

    /** 조회 조건으로 목록을 읽는다. 내 담당만은 로그인 사용자 기준이고, 로그인 정보가 없으면 그 조건을 뺀다. */
    private List<OcrReviewTransactionVO> searchReviewTransactions(ReviewListQuery query, HttpSession session) {
        String assigneeId = query.mine() ? SsoLoginUserVO.userIdOf(session) : null;
        return ocrReviewService.reviewTransactions(query.wkId(), null, assigneeId, query.reviewStatus(),
                query.from().format(DateTimeFormatter.BASIC_ISO_DATE), query.to().format(DateTimeFormatter.BASIC_ISO_DATE));
    }

    /** 세션에서 허용된 판독결과 검토 메뉴와 상단 경로를 적용한다. */
    private void applyMenu(Model model, HttpSession session) {
        reviewMenuNavigation.apply(model, session, ReviewMenuNavigation.REVIEW_URL);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * 서식목록 항목 클릭 시 뷰어에 표시할 요청 원본 파일. Agent 가 제공하는 파일을 중계한다(마스킹 문서는 403).
     */
    @ApiResponse(responseCode = "200", description = "판독 문서 원본. 실제 형식에 따라 Content-Type이 달라집니다.",
            content = @Content(mediaType = "application/octet-stream", schema = @Schema(type = "string", format = "binary")))
    @Operation(summary = "판독 문서 원본 조회")
    @GetMapping("/ocr/result/lefrm/{wkId}/{lefrmId}/view")
    public ResponseEntity<byte[]> viewLefrmImage(@PathVariable long lefrmId, @PathVariable String wkId,
                                                 @RequestParam(value = "transId", required = false) String transId,
                                                 @RequestParam(value = "docId", required = false) String docId) {
        log.debug("[판독결과] 원본 보기 요청 wkId={} lefrmId={} transId={} docId={}", wkId, lefrmId, transId, docId);
        RestAgentProvider.Download original = ocrViewerService.original(lefrmId, wkId, transId, docId);
        log.debug("[판독결과] 원본 반환 wkId={} lefrmId={} bytes={} contentType={}", wkId, lefrmId, original.body().length, original.contentType());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .contentType(original.contentType())
                .body(original.body());
    }

    @Operation(summary = "판독 이미지 페이지 정보 조회")
    @GetMapping("/ocr/result/lefrm/{wkId}/{lefrmId}/viewer-meta")
    public ResponseEntity<OcrViewerMetadata> viewerMetadata(@PathVariable long lefrmId, @PathVariable String wkId,
            @RequestParam(value = "transId", required = false) String transId,
            @RequestParam(value = "docId", required = false) String docId, HttpServletRequest request) {
        log.debug("[판독결과] 뷰어 메타 요청 wkId={} lefrmId={} transId={} docId={}", wkId, lefrmId, transId, docId);
        String baseUrl = request.getRequestURI().replaceFirst("/viewer-meta$", "");
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ocrViewerService.metadata(lefrmId, wkId, transId, docId, baseUrl));
    }

    @ApiResponse(responseCode = "200", description = "판독 페이지 PNG 이미지",
            content = @Content(mediaType = "image/png", schema = @Schema(type = "string", format = "binary")))
    @Operation(summary = "판독 페이지 이미지 조회")
    @GetMapping("/ocr/result/lefrm/{wkId}/{lefrmId}/pages/{pageNumber}/image")
    public ResponseEntity<byte[]> viewerPage(@PathVariable long lefrmId, @PathVariable String wkId,
            @PathVariable int pageNumber, @RequestParam(value = "transId", required = false) String transId,
            @RequestParam(value = "docId", required = false) String docId,
            @RequestParam(value = "view", required = false) String view) {
        byte[] image = ocrViewerService.pageImage(lefrmId, wkId, transId, docId, pageNumber, view);
        log.debug("[판독결과] 페이지 이미지 반환 wkId={} lefrmId={} page={} view={} bytes={}", wkId, lefrmId, pageNumber, view, image.length);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.IMAGE_PNG)
                .body(image);
    }

    /**
     * 서식목록 항목 클릭 시 인식/확인 정보 패널에 표시할 필드 목록.
     */
    @Operation(summary = "판독 필드와 확인값 조회")
    @GetMapping("/ocr/result/lefrm/{wkId}/{lefrmId}/recog-fields")
    @ResponseBody
    public ResponseEntity<List<OcrFldVO>> resultRecogFields(@PathVariable long lefrmId, @PathVariable String wkId,
                                            @RequestParam(value = "transId", required = false) String transId,
                                            @RequestParam(value = "docId", required = false) String docId) {
        return recognition("fields", lefrmId, wkId, transId, () -> ocrReqService.getResultRecogFieldList(lefrmId, wkId, transId, docId));
    }

    /**
     * 서식목록 항목 클릭 시 인식/확인 정보 패널에 표시할 표 목록.
     */
    @Operation(summary = "판독 표와 셀 목록 조회")
    @GetMapping("/ocr/result/lefrm/{wkId}/{lefrmId}/recog-tables")
    @ResponseBody
    public ResponseEntity<List<OcrResultTblVO>> resultRecogTables(@PathVariable String wkId, @PathVariable long lefrmId,
                                                  @RequestParam(value = "transId", required = false) String transId,
                                                  @RequestParam(value = "docId", required = false) String docId) {
        return recognition("tables", lefrmId, wkId, transId, () -> ocrReqService.getResultTblList(lefrmId, wkId, transId, docId));
    }

    /** 인식값·표 조회(Agent API). 문서가 없으면 Agent 상태 그대로, 연계 실패는 502 로 응답하고 이곳에서 로그를 남긴다. */
    private <T> ResponseEntity<List<T>> recognition(String kind, long lefrmId, String wkId, String transId,
                                                    Supplier<List<T>> load) {
        log.debug("[판독결과] 인식 {} 조회 요청 lefrmId={} wkId={} transId={}", kind, lefrmId, wkId, transId);
        try {
            List<T> loaded = load.get();
            log.debug("[판독결과] 인식 {} 조회 완료 lefrmId={} wkId={} transId={} count={}", kind, lefrmId, wkId, transId, loaded.size());
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(loaded);
        } catch (ResponseStatusException e) {
            if (e.getStatusCode().is4xxClientError()) {
                log.warn("[판독결과] 인식 {} 조회 불가 lefrmId={} wkId={} transId={} status={}",
                        kind, lefrmId, wkId, transId, e.getStatusCode().value());
                return ResponseEntity.status(e.getStatusCode()).cacheControl(CacheControl.noStore()).build();
            }
            log.error("[판독결과] 인식 {} 조회 실패 lefrmId={} wkId={} transId={}", kind, lefrmId, wkId, transId, e);
        } catch (Exception e) {
            log.error("[판독결과] 인식 {} 조회 실패 lefrmId={} wkId={} transId={}", kind, lefrmId, wkId, transId, e);
        }
        return ResponseEntity.status(502).cacheControl(CacheControl.noStore()).build();
    }

    /**
     * "원본 JSON" 버튼 - 엔진 결과 조회 응답 원본(OCR 원문 포함)을 그대로 돌려준다. aiocr.raw-result.enabled=false 면 404.
     */


    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 화면번호(scrn_no)에 해당하는 eKEOS 화면 필드 매핑정보를 내려준다(옵션1).
     * 매핑 등록/관리 화면이 아직 없어, {mappingDir}/매핑정보_{screenNo}.json 을 먼저 읽고
     * 없으면 패키지에 포함한 classpath:mapping/{screenNo}.json 을 읽는다.
     * 추후 연계Agent 호출(옵션2)로 전환 시 설정으로 갈아탄다.
     */
    @Operation(summary = "단말 화면 필드 매핑 조회")
    @GetMapping("/ocr/result/mapping")
    @ResponseBody
    public ResponseEntity<Object> resultMapping(@RequestParam(name = "screenNo", required = false) String screenNo) {
        if (screenNo == null || screenNo.isBlank() || !SCREEN_NO.matcher(screenNo.trim()).matches()) {
            return ResponseEntity.badRequest().body(Map.of("message", "잘못된 화면번호입니다."));
        }
        screenNo = screenNo.trim();
        Path file = Paths.get(mappingDir, "매핑정보_" + screenNo + ".json");
        Resource packaged = new ClassPathResource("mapping/" + screenNo + ".json");
        try {
            String json;
            if (Files.isRegularFile(file)) {
                log.info("[화면매핑] 외부 파일 사용 screenNo={} path={}", screenNo, file);
                json = Files.readString(file, StandardCharsets.UTF_8);
            } else if (packaged.isReadable()) {
                log.info("[화면매핑] 패키지 파일 사용 screenNo={}", screenNo);
                json = packaged.getContentAsString(StandardCharsets.UTF_8);
            } else {
                log.warn("OCR field mapping file missing screenNo={} path={}", screenNo, file);
                return ResponseEntity.notFound().build();
            }
            // Windows 에서 만든 파일의 BOM 은 JSON 파서가 읽지 못한다.
            Map<String, Object> mapping = objectMapper.readValue(
                    json.replaceFirst("^﻿", ""), new TypeReference<Map<String, Object>>() {});
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(mapping);
        } catch (Exception e) {
            log.error("OCR field mapping load failed screenNo={}", screenNo, e);
            return ResponseEntity.status(500).body(Map.of("message", "화면 필드 매핑 정보를 불러오지 못했습니다."));
        }
    }
    @Operation(summary = "엔진 원본 판독결과 JSON 조회")
    @GetMapping("/ocr/result/trans/{transId}/raw")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> rawResult(@PathVariable String transId) {
        if (!rawResultEnabled) {
            return ResponseEntity.notFound().build();
        }
        if (!TRANS_ID.matcher(transId).matches()) {
            log.warn("[판독결과] 원본 JSON 거절, transId 형식 오류");
            return ResponseEntity.badRequest().body(Map.of("message", "잘못된 판독작업입니다."));
        }
        try {
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ocrReqService.getRawResult(transId));
        } catch (ResponseStatusException e) {
            if (e.getStatusCode().is4xxClientError()) {
                log.warn("[판독결과] 원본 JSON 조회 불가 transId={} agentStatus={}", transId, e.getStatusCode().value());
                return ResponseEntity.status(404).body(Map.of("message", "엔진에서 판독 결과를 찾을 수 없습니다. 보관기간이 지났을 수 있습니다."));
            }
            log.error("[판독결과] 원본 JSON 조회 실패 transId={} agentStatus={}", transId, e.getStatusCode().value(), e);
        } catch (RestClientResponseException e) {
            log.error("[판독결과] 원본 JSON 조회 실패 transId={} agentHttpStatus={}", transId, e.getStatusCode().value(), e);
        } catch (Exception e) {
            log.error("[판독결과] 원본 JSON 조회 실패 transId={}", transId, e);
        }
        return ResponseEntity.status(502).body(Map.of("message", "판독 결과 원본을 가져오지 못했습니다. 잠시 후 다시 시도해 주세요."));
    }

    /**
     * "결과 다시 가져오기" 버튼 - 폴링 제한 시간을 넘기거나 검토 대기로 결과가 없는 판독작업을 Agent 로 다시 조회한다.
     */
    @Operation(summary = "엔진 판독결과 다시 가져오기")
    @PostMapping("/ocr/result/trans/{transId}/refresh")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> refreshResult(@PathVariable String transId) {
        if (!TRANS_ID.matcher(transId).matches()) {
            log.warn("[판독결과] 결과 다시 가져오기 거절, transId 형식 오류");
            return ResponseEntity.badRequest().body(Map.of("message", "잘못된 판독작업입니다."));
        }
        try {
            Map<String, Object> data = ocrReqService.refreshResult(transId);
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of(
                    "status", String.valueOf(data.getOrDefault("status", "")),
                    "resultReady", Boolean.TRUE.equals(data.get("result_ready"))));
        } catch (ResponseStatusException e) {
            if (e.getStatusCode().value() == 404) {
                log.warn("[판독결과] 결과 다시 가져오기: Agent에 판독작업 없음 transId={}", transId);
                return ResponseEntity.status(404).body(Map.of("message", "판독 요청을 찾을 수 없습니다."));
            }
            log.error("[판독결과] 결과 다시 가져오기 실패 transId={} agentStatus={}", transId, e.getStatusCode().value(), e);
        } catch (RestClientResponseException e) {
            log.error("[판독결과] 결과 다시 가져오기 실패 transId={} agentHttpStatus={}", transId, e.getStatusCode().value(), e);
        } catch (Exception e) {
            log.error("[판독결과] 결과 다시 가져오기 실패 transId={}", transId, e);
        }
        return ResponseEntity.status(502).body(Map.of("message", "판독 결과를 가져오지 못했습니다. 잠시 후 다시 시도해 주세요."));
    }
}
