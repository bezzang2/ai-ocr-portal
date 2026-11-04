package com.agilesoda.sample;

import java.util.List;
import java.util.Optional;

import org.apache.ibatis.annotations.Mapper;

import com.agilesoda.sample.dto.BoardInqDto;
import com.agilesoda.sample.dto.BoardResDto;
import com.agilesoda.sample.dto.UserResDto;
import com.agilesoda.sample.entity.BoardAttachEntity;
import com.agilesoda.sample.entity.BoardEntity;
import com.agilesoda.sample.entity.req.UserRegEntity;

@Mapper
public interface SampleMapper {
	Optional<UserResDto> selectUserInfo(String userId);
	
	int registUserInfo(UserRegEntity ure); 
	
	int updateUserInfo(UserRegEntity ure);
	
	int selectBoardCnt(BoardInqDto boardInqRequest);
	
	List<BoardResDto> selectBoardList(BoardInqDto boardInqRequest);

	Optional<BoardResDto> selectBoard(long seq);
	
	int insBoard(BoardEntity boardEntity);
	
	int insAttachFiles(BoardAttachEntity boardAttachFile);

	int selectTrxStatus(String trxId);
}
