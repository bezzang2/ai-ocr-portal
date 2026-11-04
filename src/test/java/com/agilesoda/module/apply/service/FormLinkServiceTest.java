package com.agilesoda.module.apply.service;

import com.agilesoda.module.apply.mapper.FormLinkMapper;
import com.agilesoda.module.apply.vo.FormLinkClassVO;
import com.agilesoda.module.apply.vo.FormMasterVO;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 업무·서식 연결 관리: 서식 저장 검증, 저장 직후 연결 재생성, 관리 권한 */
class FormLinkServiceTest {

    private static FormMasterVO form(Long id, String name, String docId) {
        FormMasterVO vo = new FormMasterVO();
        vo.setLefrmId(id);
        vo.setLefrmNm(name);
        vo.setDocId(docId);
        return vo;
    }

    private static HttpStatus statusOf(Runnable call) {
        return HttpStatus.valueOf(assertThrows(ResponseStatusException.class, call::run).getStatusCode().value());
    }

    @Test void registersFormAndRelinksRightAway() {
        FormLinkMapper mapper = mock(FormLinkMapper.class);
        doAnswer(inv -> { ((FormMasterVO) inv.getArgument(0)).setLefrmId(15L); return null; })
                .when(mapper).insertForm(any(), eq("U1"));
        when(mapper.insertLinksOfForm(15L, "U1")).thenReturn(2);

        FormMasterVO saved = new FormLinkService(mapper).saveForm(form(null, "  적합성 확인서 ", " KJB_TEST01 "), "U1");

        assertEquals(15L, saved.getLefrmId());
        assertEquals("적합성 확인서", saved.getLefrmNm());
        assertEquals("KJB_TEST01", saved.getDocId());
        assertEquals(2, saved.getLinkCnt());
        InOrder order = inOrder(mapper);
        order.verify(mapper).countDocId("KJB_TEST01", null);
        order.verify(mapper).insertForm(any(), eq("U1"));
        order.verify(mapper).deleteLinksOfForm(15L);
        order.verify(mapper).insertLinksOfForm(15L, "U1");
        verify(mapper, never()).updateForm(any(), any());
    }

    @Test void updatesExistingForm() {
        FormLinkMapper mapper = mock(FormLinkMapper.class);
        when(mapper.selectForm(7L)).thenReturn(form(7L, "옛 이름", "OLD"));

        new FormLinkService(mapper).saveForm(form(7L, "새 이름", "NEW"), "U1");

        verify(mapper).countDocId("NEW", 7L);
        verify(mapper).updateForm(argThat(f -> f.getLefrmId() == 7L && "새 이름".equals(f.getLefrmNm())), eq("U1"));
        verify(mapper).deleteLinksOfForm(7L);
        verify(mapper).insertLinksOfForm(7L, "U1");
    }

    @Test void rejectsDuplicateDocIdBlankAndTooLong() {
        FormLinkMapper mapper = mock(FormLinkMapper.class);
        when(mapper.countDocId("DUP", null)).thenReturn(1);
        FormLinkService service = new FormLinkService(mapper);

        assertEquals(HttpStatus.CONFLICT, statusOf(() -> service.saveForm(form(null, "서식", "DUP"), "U1")));
        assertEquals(HttpStatus.BAD_REQUEST, statusOf(() -> service.saveForm(form(null, " ", "X"), "U1")));
        assertEquals(HttpStatus.BAD_REQUEST, statusOf(() -> service.saveForm(form(null, "서식", null), "U1")));
        // 한글 67자 = 201바이트 > VARCHAR2(200)
        assertEquals(HttpStatus.BAD_REQUEST, statusOf(() -> service.saveForm(form(null, "가".repeat(67), "X"), "U1")));
        assertEquals(HttpStatus.NOT_FOUND, statusOf(() -> service.saveForm(form(99L, "서식", "X"), "U1")));
        verify(mapper, never()).insertForm(any(), any());
        verify(mapper, never()).updateForm(any(), any());
        verify(mapper, never()).deleteLinksOfForm(anyLong());
    }

    @Test void onlyMenuHoldersMayManage() {
        FormLinkMapper mapper = mock(FormLinkMapper.class);
        when(mapper.countMenuPermission("ADMIN", FormLinkService.MENU_URL)).thenReturn(1);
        FormLinkService service = new FormLinkService(mapper);

        assertDoesNotThrow(() -> service.requireManager("ADMIN"));
        assertEquals(HttpStatus.FORBIDDEN, statusOf(() -> service.requireManager("TEST")));
        assertEquals(HttpStatus.FORBIDDEN, statusOf(() -> service.requireManager(null)));
    }

    @Test void classStatusFollowsMatchAndLink() {
        FormLinkClassVO c = new FormLinkClassVO();
        assertEquals(FormLinkClassVO.UNLINKED, c.getStatus());
        c.setLefrmId(3L);
        c.setLinkYn("N");
        assertEquals(FormLinkClassVO.PENDING, c.getStatus());
        c.setLinkYn("Y");
        assertEquals(FormLinkClassVO.LINKED, c.getStatus());
    }
}
