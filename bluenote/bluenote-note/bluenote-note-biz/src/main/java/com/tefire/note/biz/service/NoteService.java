package com.tefire.note.biz.service;

import com.tefire.framework.common.response.Response;
import com.tefire.note.biz.model.vo.PublishNoteReqVO;

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
}
