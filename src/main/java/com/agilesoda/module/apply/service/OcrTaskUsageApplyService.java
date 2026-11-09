package com.agilesoda.module.apply.service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.agilesoda.module.admin.vo.SsoLoginUserVO;
import com.agilesoda.module.apply.mapper.OcrTaskUsageApplyMapper;

import lombok.RequiredArgsConstructor;

/****************************************************
 *   OCR업무사용신청 서비스
 *   - 저장 : 여러 행 중 하나라도 실패하면 전체 롤백
 *   - 사용신청 : 신청 1건(TEST_OCR_APPLY) + 결재선 N건(TEST_OCR_APPLY_LINE) 한 번에 저장
 *****************************************************/
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OcrTaskUsageApplyService {

    private static final int MAX_REASON_BYTES = 100;   // 화면 MAX_REASON_BYTES 와 같게
    private static final int MAX_LINES = 5;            // 결재자 최대 인원

    private final OcrTaskUsageApplyMapper ocrTaskUsageApplyMapper;

    public int selectUsageCnt(Map<String, Object> param) throws Exception {
        return ocrTaskUsageApplyMapper.selectUsageCnt(param);
    }

    public List<Map<String, Object>> selectUsageList(Map<String, Object> param) throws Exception {
        return ocrTaskUsageApplyMapper.selectUsageList(param);
    }

    @Transactional
    public int save(List<Map<String, Object>> rows, String userId) throws Exception {
        if (rows == null || rows.isEmpty()) {
            throw new IllegalArgumentException("수정된 데이터가 없습니다.");
        }
        int count = 0;
        for (Map<String, Object> row : rows) {
            this.validateRow(row);
            row.put("userId", userId);                      // 등록자/수정자
            int updated = ocrTaskUsageApplyMapper.updateUsage(row);
            if (updated == 0) {
                throw new IllegalArgumentException("저장할 대상을 찾지 못했습니다. : "
                    + row.get("wkId") + " / " + row.get("lefrmId") + " / " + row.get("readItem"));
            }
            count += updated;
        }
        return count;
    }

    /** 한 행 검사 : 키 값 확인 + 표시순서는 숫자만 (화면에서 막아도 서버에서 한 번 더) */
    private void validateRow(Map<String, Object> row) {
        for (String key : List.of("wkId", "lefrmId", "readItem")) {
            if (str(row.get(key)).isEmpty()) {
                throw new IllegalArgumentException("행을 구분하는 값(" + key + ")이 없습니다.");
            }
        }
        String dispOrd = str(row.get("dispOrd"));
        if (dispOrd.isEmpty()) {
            row.put("dispOrd", null);                       // 비우면 NULL 로 저장
        } else if (!dispOrd.matches("\\d+")) {
            throw new IllegalArgumentException("표시순서는 숫자만 입력할 수 있습니다. : " + row.get("readItem"));
        } else {
            row.put("dispOrd", Integer.parseInt(dispOrd));
        }
        row.put("lefrmId", Long.parseLong(str(row.get("lefrmId"))));   // 서식ID 는 숫자
    }

    @Transactional
    @SuppressWarnings("unchecked")
    public Map<String, Object> apply(Map<String, Object> param, SsoLoginUserVO loginUser) throws Exception {
        String ocrDoc      = str(param.get("ocrDoc"));
        String ocrCode     = str(param.get("ocrCode"));
        String applyReason = str(param.get("applyReason"));
        List<Map<String, Object>> lines = (List<Map<String, Object>>) param.get("lines");

        // 1) 입력값 확인
        if (ocrDoc.isEmpty() || ocrCode.isEmpty()) {
            throw new IllegalArgumentException("업무명(서식) 정보가 없습니다.");
        }
        if (applyReason.isEmpty()) {
            throw new IllegalArgumentException("신청사유를 입력하세요.");
        }
        if (applyReason.getBytes(StandardCharsets.UTF_8).length > MAX_REASON_BYTES) {
            throw new IllegalArgumentException("신청사유는 최대 " + MAX_REASON_BYTES + "byte 까지 입력할 수 있습니다.");
        }
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("결재선을 지정하세요.");
        }
        if (lines.size() > MAX_LINES) {
            throw new IllegalArgumentException("결재자는 최대 " + MAX_LINES + "명까지 지정할 수 있습니다.");
        }

        // 2) 결재자 확인 : 본인 · 중복
        Set<String> seen = new HashSet<>();
        for (Map<String, Object> line : lines) {
            String empno = str(line.get("empno"));
            if (empno.isEmpty()) {
                throw new IllegalArgumentException("결재자 정보가 없습니다.");
            }
            if (!seen.add(empno)) {
                throw new IllegalArgumentException("같은 결재자가 두 번 지정되었습니다.");
            }
            if (empno.equals(loginUser.getUserId())) {
                throw new IllegalArgumentException("본인은 결재자로 지정할 수 없습니다.");
            }
        }

        // 3) 신청 저장 : 신청자는 화면 값이 아니라 로그인 세션 값으로
        Map<String, Object> apply = new LinkedHashMap<>();
        apply.put("ocrDoc", ocrDoc);
        apply.put("ocrCode", ocrCode);
        apply.put("applyReason", applyReason);
        apply.put("applyUserId", loginUser.getUserId());
        apply.put("applyUserNm", loginUser.getUserName());
        ocrTaskUsageApplyMapper.insertApply(apply);             // → apply 에 applyId 가 채워짐

        // 4) 결재선 저장 (1차, 2차 ...)
        List<Map<String, Object>> savedLines = new ArrayList<>();
        int seq = 1;
        for (Map<String, Object> line : lines) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("applyId", apply.get("applyId"));
            row.put("seq", seq++);
            row.put("apprUserId", str(line.get("empno")));
            row.put("apprUserNm", str(line.get("empnm")));
            ocrTaskUsageApplyMapper.insertApplyLine(row);
            savedLines.add(row);
        }

        // 5) 저장된 값 돌려주기
        Map<String, Object> result = new LinkedHashMap<>(apply);
        result.put("lines", savedLines);
        result.put("lineText", savedLines.stream()
                .map(r -> r.get("seq") + ". " + r.get("apprUserNm"))
                .collect(Collectors.joining(" → ")));
        return result;
    }

    private static String str(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    public List<Map<String, Object>> selectTaskList(String keyword) throws Exception {
        return ocrTaskUsageApplyMapper.selectTaskList(Map.of("keyword", keyword == null ? "" : keyword.trim()));
    }
}