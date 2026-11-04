package com.agilesoda.common.entity.req;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

@Getter
@Builder
@ToString
public class MultipartFileEntity {
	private String path;
	private String ext;
	private String originFileNm;
	private String fileNm;
	
}
