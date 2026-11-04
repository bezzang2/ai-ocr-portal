package com.agilesoda.module.ocr.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.agilesoda.module.admin.vo.SsoLoginUserVO;
import com.agilesoda.common.menu.ReviewMenuNavigation;
import com.agilesoda.module.ocr.service.CrossVerifyService;
import com.agilesoda.module.ocr.vo.OcrAgentData;
import com.agilesoda.module.ocr.vo.OcrResultWkGroupVO;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * 통합검색 > 서식간 검증 결과. 목록은 서버에서 그리고, 상세는 화면이 /detail·/evaluate 를 fetch 한다.
 * 항목 수정은 판독결과 검토의 확인값 저장(/module/ocr/result/lefrm/{wkId}/{lefrmId}/save)을 쓴다.
 */
@Slf4j
@Controller
@RequestMapping("/module/ocr/crossVerify")
@RequiredArgsConstructor
@Tag(name = "서식간 검증 결과", description = "서식간 규칙 판정 결과 조회와 재판정")
public class CrossVerifyController {
    private static final DateTimeFormatter DAY = DateTimeFormatter.BASIC_ISO_DATE;
    /** 기본 조회 기간(오늘 포함 일수) */
    static final int DEFAULT_DAYS = 7;

    private final CrossVerifyService crossVerifyService;
    private final ReviewMenuNavigation reviewMenuNavigation;

    @GetMapping
    public String list(@RequestParam(value = "wkId", required = false) String wkId,
                       @RequestParam(value = "result", required = false) String result,
                       @RequestParam(value = "from", required = false) String from,
                       @RequestParam(value = "to", required = false) String to,
                       @RequestParam(value = "menuId", defaultValue = "") String menuId,
                       Model model, HttpSession session) {
        LocalDate[] period = period(from, to);
        LocalDate fromDay = period[0], toDay = period[1];
        log.debug("[서식간검증] 목록 요청 wkId={} result={} from={} to={}", wkId, result, fromDay, toDay);
        List<OcrResultWkGroupVO> workflows = List.of();
        List<OcrAgentData.CrossSummary> summaries = List.of();
        long started = System.currentTimeMillis();
        try {
            workflows = crossVerifyService.workflows();
            summaries = crossVerifyService.search(wkId, result, fromDay.format(DAY), toDay.format(DAY));
            log.info("[서식간검증] 목록 조회 완료 wkId={} result={} from={} to={} rows={} elapsedMs={}", wkId, result, fromDay, toDay,
                    summaries.size(), System.currentTimeMillis() - started);
        } catch (ResponseStatusException e) {
            if (!e.getStatusCode().is4xxClientError()) throw e;
            log.warn("[서식간검증] 목록 조회 거절 status={} wkId={} result={} reason={}", e.getStatusCode().value(), wkId,
                    result, e.getReason());
            model.addAttribute("loadError", e.getReason());
        } catch (Exception e) {
            log.error("[서식간검증] 목록 조회 실패 wkId={} result={} from={} to={}", wkId, result, fromDay, toDay, e);
            model.addAttribute("loadError", "서식간 검증 결과를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.");
        }
        model.addAttribute("workflowList", workflows);
        model.addAttribute("summaries", summaries);
        model.addAttribute("wkId", wkId);
        model.addAttribute("result", result);
        model.addAttribute("from", fromDay.toString());
        model.addAttribute("to", toDay.toString());
        reviewMenuNavigation.apply(model, session, ReviewMenuNavigation.CROSS_VERIFY_URL);
        return "module/ocr/cross-verify";
    }

    /** 목록 조회 조건(업무·스캔기간)의 판독작업을 지금 규칙으로 다시 판정. 결과 조건은 쓰지 않는다. */
    @Operation(summary = "조회 조건의 서식간 검증 다시 판정")
    @PostMapping("/evaluate")
    @ResponseBody
    public ResponseEntity<?> evaluateAll(@RequestParam(value = "wkId", required = false) String wkId,
                                         @RequestParam(value = "from", required = false) String from,
                                         @RequestParam(value = "to", required = false) String to) {
        LocalDate[] period = period(from, to);
        long started = System.currentTimeMillis();
        log.info("[서식간검증] 일괄 재판정 시작 wkId={} from={} to={}", wkId, period[0], period[1]);
        try {
            OcrAgentData.CrossBulkResult result = crossVerifyService.evaluateAll(wkId, period[0].format(DAY), period[1].format(DAY));
            log.info("[서식간검증] 일괄 재판정 완료 wkId={} from={} to={} targets={} evaluated={} failed={} truncated={} elapsedMs={}",
                    wkId, period[0], period[1], result.getTargets(), result.getEvaluated(), result.getFailed(),
                    result.isTruncated(), System.currentTimeMillis() - started);
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(result);
        } catch (ResponseStatusException e) {
            if (e.getStatusCode().is4xxClientError()) {
                log.warn("[서식간검증] 일괄 재판정 거절 status={} wkId={} reason={}", e.getStatusCode().value(), wkId,
                        e.getReason());
                return ResponseEntity.status(e.getStatusCode()).body(Map.of("message",
                        Objects.requireNonNullElse(e.getReason(), "요청을 확인해 주세요.")));
            }
            log.error("[서식간검증] 일괄 재판정 실패 wkId={} from={} to={}", wkId, period[0], period[1], e);
        } catch (Exception e) {
            log.error("[서식간검증] 일괄 재판정 실패 wkId={} from={} to={}", wkId, period[0], period[1], e);
        }
        return ResponseEntity.internalServerError().body(Map.of("message", "처리하지 못했습니다. 잠시 후 다시 시도해 주세요."));
    }

    @GetMapping("/{transId}")
    public String detailPage(@PathVariable String transId,
                             @RequestParam(value = "menuId", defaultValue = "") String menuId, Model model, HttpSession session) {
        model.addAttribute("transId", transId);
        // 값을 고칠 때 "내가 맡기"에 쓴다.
        SsoLoginUserVO user = (SsoLoginUserVO) session.getAttribute("ssoLoginUser");
        model.addAttribute("userId", user == null ? "" : user.getUserId());
        reviewMenuNavigation.apply(model, session, ReviewMenuNavigation.CROSS_VERIFY_URL);
        return "module/ocr/cross-verify-detail";
    }

    @Operation(summary = "판독작업의 서식간 검증 상세 조회")
    @GetMapping("/{transId}/detail")
    @ResponseBody
    public ResponseEntity<?> detail(@PathVariable String transId) {
        return handle("detail", transId, () -> crossVerifyService.detail(transId));
    }

    /** 지금 규칙·값으로 다시 판정 */
    @Operation(summary = "판독작업의 서식간 검증 다시 판정")
    @PostMapping("/{transId}/evaluate")
    @ResponseBody
    public ResponseEntity<?> evaluate(@PathVariable String transId) {
        return handle("evaluate", transId, () -> crossVerifyService.evaluate(transId));
    }

    private ResponseEntity<?> handle(String action, String transId, Supplier<OcrAgentData.CrossDetail> work) {
        long started = System.currentTimeMillis();
        log.debug("[서식간검증] {} 요청 transId={}", action, transId);
        try {
            OcrAgentData.CrossDetail detail = work.get();
            log.info("[서식간검증] {} 완료 transId={} result={} elapsedMs={}", action, transId, detail.getResult(),
                    System.currentTimeMillis() - started);
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(detail);
        } catch (ResponseStatusException e) {
            if (e.getStatusCode().is4xxClientError()) {
                log.warn("[서식간검증] {} 거절 status={} transId={} reason={}", action, e.getStatusCode().value(),
                        transId, e.getReason());
                return ResponseEntity.status(e.getStatusCode()).body(Map.of("message",
                        Objects.requireNonNullElse(e.getReason(), "요청을 확인해 주세요.")));
            }
            log.error("[서식간검증] {} 실패 transId={}", action, transId, e);
        } catch (Exception e) {
            log.error("[서식간검증] {} 실패 transId={}", action, transId, e);
        }
        return ResponseEntity.internalServerError().body(Map.of("message", "처리하지 못했습니다. 잠시 후 다시 시도해 주세요."));
    }

    /** 조회 기간 [시작, 끝]. 비면 최근 DEFAULT_DAYS 일, 거꾸로면 바꾼다 */
    static LocalDate[] period(String from, String to) {
        LocalDate toDay = parse(to, LocalDate.now());
        LocalDate fromDay = parse(from, toDay.minusDays(DEFAULT_DAYS - 1L));
        return fromDay.isAfter(toDay) ? new LocalDate[]{toDay, fromDay} : new LocalDate[]{fromDay, toDay};
    }

    /** yyyy-MM-dd. 비었거나 형식이 틀리면 기본값 */
    private static LocalDate parse(String value, LocalDate fallback) {
        if (value == null || value.isBlank()) return fallback;
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            log.debug("[서식간검증] 날짜 형식 오류, 기본값 사용 value={} fallback={}", value, fallback, e);
            return fallback;
        }
    }
}
