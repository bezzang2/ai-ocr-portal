package com.agilesoda.common.route;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DataSourceRoutingContext {

	private static final ThreadLocal<String> contextHolder = new ThreadLocal<>();

	public static String getDataSourceKey(){
		return contextHolder.get();
	}

	public static void setDataSourceKey(String key){
		contextHolder.set(key);
	}

	public static void clearDataSourceKey(){
		contextHolder.remove();
	}
}
