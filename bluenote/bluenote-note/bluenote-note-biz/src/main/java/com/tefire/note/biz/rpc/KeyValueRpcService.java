package com.tefire.note.biz.rpc;

import java.util.Objects;

import org.springframework.stereotype.Component;

import com.tefire.framework.common.response.Response;
import com.tefire.kv.api.KeyValueFeignApi;
import com.tefire.kv.dto.req.AddNoteContentReqDTO;
import com.tefire.kv.dto.req.DeleteNoteContentReqDTO;

import jakarta.annotation.Resource;

/*
 * @Author: TE-Fire 3037749727@qq.com
 * @Date: 2026-06-30 16:40:25
 * @Description: kv 服务调用
 */
@Component
public class KeyValueRpcService {
    
    @Resource
    private KeyValueFeignApi keyValueFeignApi;

    /**
     * 保存笔记内容
     *
     * @param uuid
     * @param content
     * @return
     */
    public boolean saveNoteContent(String uuid, String content) {
        AddNoteContentReqDTO addNoteContentReqDTO = new AddNoteContentReqDTO();
        addNoteContentReqDTO.setUuid(uuid);
        addNoteContentReqDTO.setContent(content);

       Response<?> response = keyValueFeignApi.addNoteContent(addNoteContentReqDTO);

       if (Objects.isNull(response) || !response.isSuccess()) {
            return false;
       }

       return true;
    }

    /**
     * 删除笔记内容
     *
     * @param uuid
     * @return
     */
    public boolean deleteNoteContent(String uuid) {
        DeleteNoteContentReqDTO deleteNoteContentReqDTO = new DeleteNoteContentReqDTO();
        deleteNoteContentReqDTO.setUuid(uuid);

        Response<?> response = keyValueFeignApi.deleteNoteContent(deleteNoteContentReqDTO);

        if (Objects.isNull(response) || !response.isSuccess()) {
            return false;
        }

        return true;
    }
}
