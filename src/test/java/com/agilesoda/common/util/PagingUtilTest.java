package com.agilesoda.common.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * PagingUtil의 게시물 시작·끝번호 계산을 검증한다.
 * OCR업무조회 하단의 "조회 결과 : 시작-끝/총개수" 표시가 이 값을 그대로 쓴다.
 */
class PagingUtilTest {

    @Test
    void 총개수가_한페이지보다_적으면_끝번호는_총개수다() {
        PagingUtil page = new PagingUtil(1, 3, 15, 10);
        assertEquals(1, page.getStartRowIndex());
        assertEquals(3, page.getEndRowIndex());
    }

    @Test
    void 가득찬_페이지의_끝번호는_페이지크기의_배수다() {
        PagingUtil page = new PagingUtil(2, 40, 15, 10);
        assertEquals(16, page.getStartRowIndex());
        assertEquals(30, page.getEndRowIndex());
    }

    @Test
    void 마지막_페이지의_끝번호는_총개수다() {
        PagingUtil page = new PagingUtil(3, 40, 15, 10);
        assertEquals(31, page.getStartRowIndex());
        assertEquals(40, page.getEndRowIndex());
    }

    @Test
    void 요청_페이지가_범위를_넘으면_마지막_페이지_기준으로_계산한다() {
        PagingUtil page = new PagingUtil(9, 40, 15, 10);
        assertEquals(31, page.getStartRowIndex());
        assertEquals(40, page.getEndRowIndex());
    }

    @Test
    void 게시물이_없으면_끝번호는_0이다() {
        assertEquals(0, new PagingUtil(1, 0, 15, 10).getEndRowIndex());
    }
}
