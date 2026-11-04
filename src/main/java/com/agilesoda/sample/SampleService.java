package com.agilesoda.sample;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.agilesoda.sample.dto.BoardInqDto;
import com.agilesoda.sample.dto.BoardResDto;
import com.agilesoda.sample.dto.UserResDto;
import com.agilesoda.sample.entity.BoardAttachEntity;
import com.agilesoda.sample.entity.BoardEntity;
import com.agilesoda.sample.entity.req.UserRegEntity;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;


@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SampleService {
	
	private final SampleMapper sampleMapper;
	
	public Optional<UserResDto> getUserInfo(String userId) throws Exception{
		return sampleMapper.selectUserInfo(userId);
	}
	
	public int selectBoardCnt(BoardInqDto boardInqRequest) throws Exception{
		return sampleMapper.selectBoardCnt(boardInqRequest);
	}
	
	public List<BoardResDto> selectBoardList(BoardInqDto boardInqRequest) throws Exception{
		return sampleMapper.selectBoardList(boardInqRequest);
	}
	
	@Transactional
	public int registUserInfo(UserRegEntity ure) throws Exception{
		return sampleMapper.registUserInfo(ure);
	}
	/*
	@Transactional
	public int updateUserInfo(UserRegEntity ure) throws Exception{
		Optional<UserEntity> opUserEntity = sampleMapper.selectUserInfo(ure.getUserId());
		
		if(opUserEntity.isPresent()) {
			UserEntity data = opUserEntity.get();
			if("".equals(ure.getEmail())) {
				ure.setEmail(data.getEmail());
			}
			if("".equals(ure.getHpNo())) {
				ure.setHpNo(data.getHpNo());
			}
			return sampleMapper.updateUserInfo(ure);
		}
		
		return 0;
	}
	*/
	@Transactional
	public long registBoard(BoardEntity boardEntity) throws Exception{
		long result = 0;
		int cnt = sampleMapper.insBoard(boardEntity);
		if(cnt > 0) {
			result = boardEntity.getSeq(); 
		}
		return result;
	}
	
	@Transactional
	public int insertAttachFiles(List<BoardAttachEntity> attachFiles) throws Exception{
		int cnt = 0;
		for(BoardAttachEntity file : attachFiles) {
			cnt += sampleMapper.insAttachFiles(file);
		}
		return cnt;
	}

	public int selectTrxStatus(String trxId) throws Exception{
		return sampleMapper.selectTrxStatus(trxId);
	}
}
