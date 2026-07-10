package com.agilesoda.common.util;

import java.text.SimpleDateFormat;
import java.util.Date;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DateUtil {
	public static final int YEAR = 1;
	public static final int MONTH = 2;
	public static final int DAY = 3;
	public static final int MONTHSTART = 4;
	public static final int MONTHEND = 5;
	
	/**
	 * 현재 yyyyMMddHHmmss 변환
	 * 
	 */
	public static String getCurrentDateTime() {
		return getCurrentDateTime("yyyyMMddHHmmss");
	}
	
	public static String getCurrentDateTime(String format) {
		SimpleDateFormat sdf = new SimpleDateFormat(format);
		return sdf.format(new Date());
	}
	
}
