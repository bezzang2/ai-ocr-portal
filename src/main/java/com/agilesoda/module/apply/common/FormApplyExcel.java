package com.agilesoda.module.apply.common;

import com.agilesoda.module.apply.vo.FormApplVO;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class FormApplyExcel {
    private static final String MENU_NM = "서식신청목록";
    private static final DateTimeFormatter FILE_NM_DT_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmm");
    private static final String[] HEADERS = {"No", "진행상태", "서식명", "신청자", "신청부서", "신청일자", "최종변경일자"};

    private FormApplyExcel() {}

    // 파일명 형식: 메뉴명+yyyymmddhhmm (예: 서식신청목록202608251330.xlsx)
    public static String buildFileName() {
        return MENU_NM + LocalDateTime.now().format(FILE_NM_DT_FORMATTER) + ".xlsx";
    }

    public static byte[] write(List<FormApplVO> formList) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet(MENU_NM);

            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            Row headerRow = sheet.createRow(0);
            for (int col = 0; col < HEADERS.length; col++) {
                Cell cell = headerRow.createCell(col);
                cell.setCellValue(HEADERS[col]);
                cell.setCellStyle(headerStyle);
            }

            int rowIdx = 1;
            for (FormApplVO row : formList) {
                Row dataRow = sheet.createRow(rowIdx);
                dataRow.createCell(0).setCellValue(row.getNo());
                dataRow.createCell(1).setCellValue(nullToEmpty(row.getProcStatNm()));
                dataRow.createCell(2).setCellValue(nullToEmpty(row.getFormNm()));
                dataRow.createCell(3).setCellValue(nullToEmpty(row.getApplicantNm()));
                dataRow.createCell(4).setCellValue(nullToEmpty(row.getApplDeptNm()));
                dataRow.createCell(5).setCellValue(nullToEmpty(row.getApplDate()));
                dataRow.createCell(6).setCellValue(nullToEmpty(row.getUpdateDate()));
            }

            for (int col = 0; col < HEADERS.length; col++) {
                sheet.autoSizeColumn(col);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
