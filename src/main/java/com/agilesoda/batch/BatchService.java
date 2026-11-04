package com.agilesoda.batch;

import com.agilesoda.common.route.DataSourceRoutingContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class BatchService {

    private final BatchMapper batchMapper;

    public void selectUserList() throws Exception{
        DataSourceRoutingContext.setDataSourceKey("phrms");
        List<Map<String, Object>> list = batchMapper.selectUserList();
        log.debug("list => {}", list);
    }
}
