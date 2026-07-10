package com.agilesoda.link;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class TestLinkService {
	public String callLink() {
		log.info("call Link");
		RestClient restClient = RestClient.builder()
				.baseUrl("http://localhost:8080")
				.defaultHeader("Authorization", "Bearer token")
				.build();
		
		log.info("step 1 ================================");
		String result = restClient.get()
				.uri("/api/v1/test/list", 1L)
				.retrieve()
				.body(String.class);
		
		log.info("step 2 ================================");
		log.info("result = {}", result);
		
		return result;
	}
}
