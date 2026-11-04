package com.agilesoda.common.config;

import java.util.HashMap;
import java.util.Map;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import com.agilesoda.common.route.RoutingDataSource;
import com.zaxxer.hikari.HikariDataSource;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
@EnableTransactionManagement
public class DataSourceConfig {
	
	private final String BPR_DATA_SOURCE = "bprDataSource";
	
	private final String PHRMS_DATA_SOURCE = "phrmsDataSource";
	
	@Bean
	@ConfigurationProperties("spring.datasource.bpr.hikari")
	public DataSource bprDataSource() {
		return DataSourceBuilder.create().type(HikariDataSource.class).build();
	}
	
	@Bean
	@ConfigurationProperties("spring.datasource.phrms.hikari")
	public DataSource phrmsDataSource() {
		return DataSourceBuilder.create().type(HikariDataSource.class).build();
	}

	@Bean
	public DataSource routingDataSource(@Qualifier(BPR_DATA_SOURCE) DataSource bprDataSource,
			@Qualifier(PHRMS_DATA_SOURCE) DataSource phrmsDataSource){
		Map<Object, Object> targetDataSource = new HashMap<>() {
			{
				put("bpr", bprDataSource);
				put("phrms", phrmsDataSource);
			}
		};
		
		RoutingDataSource routingDataSource = new RoutingDataSource();
		routingDataSource.setDefaultTargetDataSource(bprDataSource);
		routingDataSource.setTargetDataSources(targetDataSource);
		
		return routingDataSource;
		
	}

	@Primary
	@Bean
	public DataSource dataSource(@Qualifier("routingDataSource") DataSource routingDataSource) {
		return new LazyConnectionDataSourceProxy(routingDataSource);
	}
}
