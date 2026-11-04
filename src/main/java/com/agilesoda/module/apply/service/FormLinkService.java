package com.agilesoda.module.apply.service;

import com.agilesoda.module.apply.mapper.FormLinkMapper;
import com.agilesoda.module.apply.vo.FormLinkClassVO;
import com.agilesoda.module.apply.vo.FormLinkWorkflowVO;
import com.agilesoda.module.apply.vo.FormMasterVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 관리자 > 업무·서식 연결 관리. 업무(엔진 워크플로)의 분류 클래스마다 연결된 서식을 보여 주고,
 * 서식 마스터(OCUOC_LEFRM_M)를 등록·수정한다. 저장하면 그 서식의 업무 연결(OCUOC_OCRWL_L)을 바로 다시 만든다.
 * 연결 규칙은 동기화와 같다(클래스 영문코드 = 서식 DOC_ID, 엔진 접두어 KJB_ 는 있어도 없어도 된다. 코드가 없는 클래스는 미연결).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FormLinkService {

    /** 이 화면의 메뉴 URL. 이 메뉴 권한이 있는 역할만 서식 저장·즉시 동기화를 할 수 있다 */
    public static final String MENU_URL = "module/admin/formLink";
    /** OCUOC_LEFRM_M.LEFRM_NM VARCHAR2(200), DOC_ID VARCHAR2(100) 바이트 */
    private static final int NAME_MAX_BYTES = 200;
    private static final int DOC_ID_MAX_BYTES = 100;

    private final FormLinkMapper formLinkMapper;

    public List<FormLinkWorkflowVO> getWorkflowList() {
        return formLinkMapper.selectWorkflowList();
    }

    /** wkId 가 없으면 전체 업무의 클래스. unlinkedOnly 면 맞는 서식이 없는 클래스만 */
    public List<FormLinkClassVO> getClassList(String wkId, boolean unlinkedOnly) {
        List<FormLinkClassVO> classes = formLinkMapper.selectClassList(wkId, unlinkedOnly);
        log.debug("[업무·서식 연결] 클래스 조회 wkId={} unlinkedOnly={} count={}", wkId, unlinkedOnly, classes.size());
        return classes;
    }

    public List<FormMasterVO> getFormList() {
        return formLinkMapper.selectFormList();
    }

    /** 서식 저장·즉시 동기화 권한. 없으면 403 */
    public void requireManager(String userId) {
        if (userId == null || formLinkMapper.countMenuPermission(userId, MENU_URL) == 0) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "업무·서식 연결 관리 권한이 없습니다.");
        }
    }

    /**
     * 서식 등록(lefrmId 없음)·수정. 문서 종류 코드는 KJB_ 유무를 빼고 서식끼리 겹칠 수 없다(겹치면 동기화가 ID가 작은 서식만 연결한다).
     * 저장 뒤 이 서식에 맞는 클래스로 업무 연결을 다시 만들어, 다음 정기 동기화를 기다리지 않는다.
     */
    @Transactional
    public FormMasterVO saveForm(FormMasterVO request, String userId) {
        FormMasterVO form = new FormMasterVO();
        form.setLefrmId(request.getLefrmId());
        form.setLefrmNm(required(request.getLefrmNm(), "서식명", NAME_MAX_BYTES));
        form.setDocId(required(request.getDocId(), "문서 종류 코드", DOC_ID_MAX_BYTES));

        if (form.getLefrmId() != null && formLinkMapper.selectForm(form.getLefrmId()) == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "수정할 서식이 없습니다. 목록을 다시 조회해 주세요.");
        }
        if (formLinkMapper.countDocId(form.getDocId(), form.getLefrmId()) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "문서 종류 코드 '" + form.getDocId() + "'는 다른 서식이 이미 쓰고 있습니다(KJB_ 유무는 구분하지 않습니다).");
        }

        if (form.getLefrmId() == null) {
            formLinkMapper.insertForm(form, userId);
        } else {
            formLinkMapper.updateForm(form, userId);
        }
        int removed = formLinkMapper.deleteLinksOfForm(form.getLefrmId());
        int linked = formLinkMapper.insertLinksOfForm(form.getLefrmId(), userId);
        form.setLinkCnt(linked);
        log.info("[업무·서식 연결] 서식 저장 lefrmId={} docId={} created={} linksRemoved={} linksCreated={} userId={}",
                form.getLefrmId(), form.getDocId(), request.getLefrmId() == null, removed, linked, userId);
        return form;
    }

    private static String required(String value, String label, int maxBytes) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, label + "을(를) 입력해 주세요.");
        }
        if (trimmed.getBytes(StandardCharsets.UTF_8).length > maxBytes) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    label + "이(가) 너무 깁니다. 한글 기준 " + (maxBytes / 3) + "자 이내로 입력해 주세요.");
        }
        return trimmed;
    }
}
