package com.agilesoda.common.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.Strings;
import org.apache.tika.Tika;
import org.springframework.web.multipart.MultipartFile;

import com.agilesoda.common.entity.req.MultipartFileEntity;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FileUtil {
	
	private static final String[][] mimes = {
			{"image", "application/pdf"},
			{"image"	//	image
				, "text/plain"	//	txt
				, "application/pdf"	//	pdf
				, "application/x-hwp"	//	hwp97 ~ 3.0
				, "application/x-tika-msoffice"	//	hwp
				, "application/msword"	//	doc
				, "application/vnd.openxml;formats-officedocument.wordprocessingml.document"	// docx
				, "application/vnd.ms-excel"	//	xls
				, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"	//	xlsx
				, "application/vnd.ms-powerpoint"	//	ppt
				, "application/vnd.openxmlformats-officedocument.presentationml.presentation"	//	pptx
			}
	};

	/**
	 * 멀티 파트로 업로드된 파일을 randomUUID로 파일명 변경 후 현재 날짜의 폴더에 저장한다.
	 * @param file
	 * @param filePath
	 * @return
	 * @throws IOException
	 */
	public static MultipartFileEntity saveMultipartFile(MultipartFile file, String filePath) throws IOException{
		log.info("[saveMultipartFile] 시작");
		
		MultipartFileEntity attach = null;
		
		String dir = DateUtil.getCurrentDate() + "/";
		String organizedFilePath = "";
		
		if(file.getSize() > 0 ) {
			UUID randomUUID = UUID.randomUUID();
			try (InputStream is = file.getInputStream()){
				log.debug("[파일] 업로드 디렉토리 filePath={}", filePath);
				
				File realUploadDir = new File(filePath);
				if(!realUploadDir.exists()) {
					realUploadDir.mkdirs();
				}
				
				filePath = filePath + dir;
				
				realUploadDir = new File(filePath);
				if(!realUploadDir.exists()) {
					realUploadDir.mkdirs();
				}

				String originFileNm = file.getOriginalFilename();
				
				String fileType = file.getOriginalFilename().substring(originFileNm.lastIndexOf(".") + 1);
				
				int len = originFileNm.length();
				
				organizedFilePath = filePath + randomUUID + "." + fileType;
				
				attach = MultipartFileEntity.builder()
						.fileNm(randomUUID.toString())
						.originFileNm(originFileNm.substring(0, len - (fileType.length() + 1)))
						.ext(fileType)
						.path(DateUtil.getCurrentDate())
						.build();
				
				log.debug("[파일] 업로드 파일 정보 fileNm={} ext={} size={}", attach.getFileNm(), attach.getExt(), file.getSize());
				
				try(OutputStream os = new FileOutputStream(organizedFilePath)){
					int readByte = 0;
					byte[] buffer = new byte[4096];
					while((readByte = is.read(buffer, 0, 4096)) != -1 ) {
						os.write(buffer, 0, readByte);
					}
				}
				catch(IOException e) {
					log.error("[파일] 업로드 파일 저장 실패 path={}", organizedFilePath, e);
				}
			}
			catch(IOException e) {
				log.error("[파일] 업로드 파일 읽기 실패 size={}", file.getSize(), e);
			}
		}
		
		return attach;
	}
	
	/**
	 * 파일을 Base64로 인코딩 한다.
	 * @param src
	 * @param filePath
	 * @return
	 * @throws Exception
	 */
	public static String encodeBase64(final MultipartFileEntity src, final String filePath) throws Exception{

		String base64 = "";
		
		File file = new File(filePath + File.separator + src.getPath() + File.separator + src.getFileNm() + "." + src.getExt());

		if(!file.exists()){
			log.warn("[파일] 파일 없음 path={}", file.getCanonicalPath());
			throw new FileNotFoundException(src.getFileNm());
		}

		try(FileInputStream fis = new FileInputStream(file)){
			byte[] buf = new byte[(int)file.length()];
			fis.read(buf);
			base64 = new String(Base64.encodeBase64(buf));
		}
		// IOException 은 호출부가 최종 처리하므로 그대로 올린다.
		
		return base64;
	}
	
	/**
	 * 파일을 삭제한다.
	 * @param path(파일명)
	 * @return
	 */
	public static int deleteFile(String path) {
		int nRet = -1;
		File file = new File(path);
		
		if(file.exists()) {
			if(file.delete()) {
				nRet = 0;
			}
		}
		
		return nRet;
	}
	
	/**
	 * 디렉토리를 삭제한다.
	 * @param path
	 */
	public static void deleteDirectory(String path) {
		File dir = new File(path);
		
		File[] fileList = dir.listFiles();
		try {
			for(File file : fileList) {
				if(file.isDirectory()) {
					deleteDirectory(file.getCanonicalPath().toString());
				}
				else {
					file.delete();
				}
			}
			
			dir.delete();
		}
		catch(IOException e) {
			log.error("[파일] 디렉토리 삭제 실패 path={}", path, e);
		}
	}
	
	/**
	 * mimeType을 추출한다.
	 * @param file
	 * @return
	 */
	public static String getMimeType(File file) {
		log.info("[getMimeType] 시작");

		Tika tika = new Tika();
		String mimeType = "";
		try {
			mimeType = tika.detect(file);
		}
		catch(Exception e) {
			log.error("[파일] mimeType 판별 실패 file={}", file.getName(), e);
		}
		
		return mimeType;
	}

	public static String getFormat(File file){
		String format = "";
		String mimeType = getMimeType(file);

		if(mimeType.indexOf("image") > -1 ){
			format = "image";
		}
		else if(mimeType.indexOf("pdf") > -1){
			format = "pdf";
		}

		return format;
	}

	public static boolean checkFileType(String fileNm, String ext, int idx) {
		log.info("[checkFileType] 시작");
		log.debug("[파일] 형식 검사 file={} ext={} idx={}", fileNm, ext, idx);

		boolean bRet = false;

		Tika tika = new Tika();

		try {
			String mimeType = tika.detect(new File(fileNm));
			log.debug("[파일] 판별된 mimeType={}", mimeType);

			bRet = isPermittedMimeType(idx, mimeType);
		}
		catch(Exception e) {
			log.error("[파일] 파일 형식 검사 실패 file={} idx={}", fileNm, idx, e);
		}

		return bRet;
	}

	/**
	 * 업로드된 파일의 확장자와 mime type일 일치하는 지 확인
	 * @param mpFile
	 * @param idx
	 * @return
	 */
	public static boolean checkFileType(MultipartFile mpFile, int idx) {
		log.info("[checkFileType] 시작");
		log.debug("[파일] 업로드 파일 형식 검사 size={} idx={}", mpFile.getSize(), idx);
		
		boolean bRet = false;
		
		Tika tika = new Tika();
		try {
			File convFile = new File(new String(mpFile.getOriginalFilename().getBytes("8859_1"), "UTF-8"));
			mpFile.transferTo(convFile);
			
			String mimeType = tika.detect(convFile);
			
			bRet = isPermittedMimeType(idx, mimeType);
		}
		catch(Exception e) {
			log.error("[파일] 업로드 파일 형식 검사 실패 file={} idx={}", mpFile.getOriginalFilename(), idx, e);
		}
		
		return bRet;
	}
	
	/**
	 * 허용된 파일 확장인지 체크 
	 * @param idx
	 * @param mime
	 * @return
	 */
	private static boolean isPermittedMimeType(int idx, String mime) {
		boolean bRet = false;
		
		for(String mimeType : mimes[idx]) {
			if(Strings.CI.startsWith(mime, mimeType)) {
				bRet = true;
				break;
			}
		}
		
		return bRet;
	}

	public static String readFile(File file){
		String result = "";
		try(FileInputStream fis = new FileInputStream(file)){
			byte[] buf = fis.readAllBytes();
			result = new String(buf);
		}
		catch(Exception e){
			log.error("[파일] 파일 읽기 실패 file={}", file.getName(), e);
		}

		return result;
	}
}
