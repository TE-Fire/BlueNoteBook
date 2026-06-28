package com.tefire.biz.kv.service;

/*
 * @Author: TE-Fire 3037749727@qq.com
 * @Date: 2026-06-28 13:06:21
 * @Description: 笔记内容存储业务
 */
import com.tefire.framework.common.response.Response;
import com.tefire.kv.dto.req.AddNoteContentReqDTO;
import com.tefire.kv.dto.req.DeleteNoteContentReqDTO;
import com.tefire.kv.dto.req.FindNoteContentReqDTO;
import com.tefire.kv.dto.rsp.FindNoteContentRspDTO;

public interface NoteContentService {
    
    /**
     * 添加笔记内容
     * 
     * @param addNoteContentReqDTO
     * @return
     */
    Response<?> addNoteContent(AddNoteContentReqDTO addNoteContentReqDTO);

     /**
     * 查询笔记内容
     * 
     * @param findNoteContentReqDTO
     * @return
     */
    Response<FindNoteContentRspDTO> findNoteContent(FindNoteContentReqDTO findNoteContentReqDTO);

      /**
     * 删除笔记内容
     * 
     * @param deleteNoteContentReqDTO
     * @return
     */
    Response<?> deleteNoteContent(DeleteNoteContentReqDTO deleteNoteContentReqDTO);
}
