package com.agilesoda.common.route;

import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RoutingDataSource extends AbstractRoutingDataSource{
	
	@Override
	protected Object determineCurrentLookupKey() {
		log.debug("data source key : {}", DataSourceRoutingContext.getDataSourceKey());
		return DataSourceRoutingContext.getDataSourceKey();
	}
}
