package com.agilesoda.common.util;

import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Calendar;
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
		if(isNull(format)) {
			format = "yyyyMMddHHmmss";
		}
		
		SimpleDateFormat sdf = new SimpleDateFormat(format);
		return sdf.format(new Date());
	}
	
	public static String getCurrentDate() {
		return getCurrentDate("yyyyMMdd");
	}
	
	public static String getCurrentDate(String format) {
		if(isNull(format)) {
			format = "yyyyMMdd";
		}
		
		SimpleDateFormat sdf = new SimpleDateFormat(format);
		return sdf.format(new Date());
	}
	
	/**
	 * 날짜를 문자열로 (현재기준 날짜 이전/이후 지정일 단위)
	 * @param amount
	 * @param format
	 * @return
	 */
	public static String date2String(int amount, String format) {
		return date2String(Calendar.DATE, amount, format);
	}
	
	/**
	 * 특정일자의 특정기간 이전/이후의 날짜 변환
	 * @param src 특정일
	 * @param field (year, month, date, hour, minute, second)
	 * @param gap ( - ~ + )
	 * @return
	 */
	public static String getDateWithGap(String src, String field, int gap) {
		String result = "";
		
		if("".equals(StringUtil.nvl(field))) {
			return "";
		}
		
		SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
		Date currentDate = null;
		
		try {
			currentDate = sdf.parse(src);
		}
		catch(Exception e) {
			log.debug("[날짜] 날짜 해석 실패, 빈 문자열 반환 src={} field={} gap={}", src, field, gap, e);
			return "";
		}
		
		Calendar c = Calendar.getInstance();
		c.setTime(currentDate);
		
		if(field.toUpperCase().equals("YEAR")) {
			c.add(Calendar.YEAR, gap);
		}
		else if(field.toUpperCase().equals("MONTH")) {
			c.add(Calendar.MONTH, gap);
		}
		else if(field.toUpperCase().equals("DATE")) {
			c.add(Calendar.DATE, gap);
		}
		else if(field.toUpperCase().equals("HOUR")) {
			c.add(Calendar.HOUR, gap);
		}
		else if(field.toUpperCase().equals("MINUTE")) {
			c.add(Calendar.MINUTE, gap);
		}
		else if(field.toUpperCase().equals("SECOND")) {
			c.add(Calendar.SECOND, gap);
		}
		
		result = sdf.format(new Date(c.getTimeInMillis()));
		
		return result;
	}
	
	/**
	 * 날짜를 문자열로 (현재기준 날짜 이전/이후)
	 * @param field
	 * @param amount
	 * @param format
	 * @return
	 */
	public static String date2String(int field, int amount, String format) {
		if(isNull(format)) {
			format = "yyyyMMddHHmmss";
		}
		
		if( amount == 0 ) {
			return getCurrentDateTime(format);
		}
		
		Calendar c = Calendar.getInstance();
		c.add(field, amount);
		
		return date2String(c.getTimeInMillis(), format);
	}
	
	/**
	 * long 타입 형태를 문자열로...
	 * @param dt
	 * @param format
	 * @return
	 */
	public static String date2String(long dt, String format) {
		if( isNull(format)) {
			format = "yyyyMMddHHmmss";
		}
		
		SimpleDateFormat sdf = new SimpleDateFormat(format);
		return sdf.format(new Date(dt));
	}
	
	/**
	 * 문자열을 날짜 타입으로 변환
	 * @param dt
	 * @param format
	 * @return
	 */
	public static Date string2Date(String dt, String format) {
		if(isNull(format)) {
			format = "yyyyMMddHHmmss";
		}
		
		SimpleDateFormat sdf = new SimpleDateFormat(format);
		Date date = null;
		
		try {
			date = sdf.parse(dt);
		}
		catch(ParseException e) {
			log.debug("[날짜] 날짜 문자열 해석 실패, null 반환 value={} format={}", dt, format, e);
		}
		
		return date;
	}

	public static Timestamp date2Timestamp(Date date){
		return new Timestamp(date.getTime());
	}

	public static String timestamp2String(Timestamp timestamp, String format){
		SimpleDateFormat sdf = new SimpleDateFormat(format);
		return sdf.format(timestamp);
	}

	public static String timestamp2String(String dt, String format){
		Timestamp timestamp = Timestamp.from(Instant.parse(dt));

		return timestamp2String(timestamp, format);
	}
	
	public static boolean isNull(String str) {
		if( str == null || str.trim().equals("")) {
			return true;
		}
		
		return false;
	}
}
