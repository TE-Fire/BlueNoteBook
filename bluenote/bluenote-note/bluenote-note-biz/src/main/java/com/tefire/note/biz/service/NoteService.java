package com.tefire.note.biz.service;

import com.tefire.framework.common.response.Response;
import com.tefire.note.biz.model.vo.FindNoteDetailReqVO;
import com.tefire.note.biz.model.vo.FindNoteDetailRspVO;
import com.tefire.note.biz.model.vo.PublishNoteReqVO;
import com.tefire.note.biz.model.vo.UpdateNoteReqVO;

/*
 * @Author: TE-Fire 3037749727@qq.com
 * @Date: 2026-07-01 12:03:57
 * @Description: 笔记业务
 */
public interface NoteService {
    
    /**
     * 笔记发布
     * @param publishNoteReqVO
     * @return
     */
    Response<?> publishNote(PublishNoteReqVO publishNoteReqVO);

    /**
     * 笔记详情
     * @param findNoteDetailReqVO
     * @return
     */
    Response<FindNoteDetailRspVO> findNoteDetail(FindNoteDetailReqVO findNoteDetailReqVO);

     /**
     * 笔记更新
     * @param updateNoteReqVO
     * @return
     */
    Response<?> updateNote(UpdateNoteReqVO updateNoteReqVO);

    
    /**
     * 删除本地笔记缓存
     * @param noteId
     */
    void deleteNoteLocalCache(Long noteId);

}
