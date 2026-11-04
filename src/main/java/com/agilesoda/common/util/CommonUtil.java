package com.agilesoda.common.util;

import org.jspecify.annotations.Nullable;

import com.agilesoda.sample.dto.UserResDto;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CommonUtil {
	public static <T> @Nullable T convert(Object obj, Class<T> toType) {
		T t;
		try {
			ObjectMapper om = new ObjectMapper();
			t = om.convertValue(obj, toType);
		}
		catch(IllegalArgumentException e) {
			log.debug("[변환] 타입 변환 실패, null 반환 toType={}", toType.getSimpleName(), e);
			t = null;
		}
		return t;
	}
	
	public static UserResDto getUserEntity(HttpServletRequest request) {
		HttpSession session = request.getSession();

		return (UserResDto)session.getAttribute("user");
	}
}