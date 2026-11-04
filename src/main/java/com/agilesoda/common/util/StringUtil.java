package com.agilesoda.common.util;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;
import net.sf.json.JSONObject;

@Slf4j
public class StringUtil{

	public static String nvl(Object src) {
		return nvl(src, "");
	}
	
	public static String nvl(Object src, String rtn) {
		if( src == null ) {
			return rtn;
		}
		
		String temp = String.valueOf(src);
		if("".equals(temp) || temp.isEmpty()) {
			return rtn;
		}
		
		return temp;
	}
	
	public static int nvlInt(Object src, int rtn) {
		return nvlInt(nvl(src), rtn);
	}
	
	public static int nvlInt(String src, int rtn) {
		if(isEmpty(src) || "null".equals(src)) {
			return rtn;
		}

		try {
			return Integer.parseInt(src);
		}
		catch(NumberFormatException e) {
			return rtn;
		}
	}

	public static long nvlLong(Object src, long rtn) {
		return nvlLong(nvl(src), rtn);
	}

	public static long nvlLong(String src, long rtn) {
		if(isEmpty(src) || "null".equals(src)) {
			return rtn;
		}

		try {
			return Long.parseLong(src);
		}
		catch(NumberFormatException e) {
			return rtn;
		}
	}

	public static boolean nvlBool(Object src, boolean rtn) {
		return nvlBool(nvl(src), rtn);
	}
	public static boolean nvlBool(String src, boolean rtn) {
		if(isEmpty(src) || "null".equals(src)) {
			return rtn;
		}

		boolean temp = rtn;
		
		if("N".equals(src)) {
			temp = false;
		}
		else if("0".equals(src)) {
			temp = false;
		}
		else if("1".equals(src)) {
			temp = true;
		}
		else if("Y".equals(src)) {
			temp = true;
		}
		else {
			temp = Boolean.parseBoolean(src);
		}
		
		return temp;
	}
	
	public static boolean isEmpty(String src) {
		return src.isEmpty();
	}

	public static String urlDecodeUTF8(String str) {
		try {
			return URLDecoder.decode(str, "UTF-8");
		}
		catch(UnsupportedEncodingException e) {
			throw new UnsupportedOperationException(e);
		}
	}
	
	@SuppressWarnings("unchecked")
	public static String urlDecodeUTF8(JSONObject jobj) {
		
		StringBuilder sb = new StringBuilder();
		Set<String> keys = jobj.keySet();
		for(String key : keys) {
			if(sb.length() > 0 ) {
				sb.append("&");
			}
			
			sb.append(String.format("%s=%s",
					urlDecodeUTF8(key),
					urlDecodeUTF8(jobj.getString(key))
			));
		}
		
		return sb.toString();
	}

}
