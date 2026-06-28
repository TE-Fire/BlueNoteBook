package com.tefire.kv.api;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.tefire.framework.common.response.Response;
import com.tefire.kv.constants.ApiConstants;
import com.tefire.kv.dto.req.AddNoteContentReqDTO;
import com.tefire.kv.dto.req.FindNoteContentReqDTO;
import com.tefire.kv.dto.rsp.FindNoteContentRspDTO;

/*
 * @Author: TE-Fire 3037749727@qq.com
 * @Date: 2026-06-28 15:01:05
 * @Description: K-V 键值存储 Feign 接口
 */
@FeignClient(name = ApiConstants.SERVICE_NAME)
public interface KeyValueFeignApi {
    
    String PREFIX = "/kv";

    @PostMapping(value = PREFIX + "/note/content/add")
    Response<?> addNoteContent(@RequestBody AddNoteContentReqDTO addNoteContentReqDTO);

    @PostMapping(value = PREFIX + "/note/content/find")
    Response<FindNoteContentRspDTO> findNoteContent(@RequestBody FindNoteContentReqDTO findNoteContentReqDTO); 
}
