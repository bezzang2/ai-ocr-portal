package com.agilesoda.sample.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class BoardAttachEntity extends CommonEntity{
	private long boardSeq;
	private String path;
	private String ext;
	private String originFileNm;
	private String fileNm;
	
}
