package com.tefire.note.biz.service;

import com.tefire.framework.common.response.Response;
import com.tefire.note.biz.model.vo.DeleteNoteReqVO;
import com.tefire.note.biz.model.vo.FindNoteDetailReqVO;
import com.tefire.note.biz.model.vo.FindNoteDetailRspVO;
import com.tefire.note.biz.model.vo.LikeNoteReqVO;
import com.tefire.note.biz.model.vo.PublishNoteReqVO;
import com.tefire.note.biz.model.vo.TopNoteReqVO;
import com.tefire.note.biz.model.vo.UnlikeNoteReqVO;
import com.tefire.note.biz.model.vo.UpdateNoteReqVO;
import com.tefire.note.biz.model.vo.UpdateNoteVisibleOnlyMeReqVO;

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

     /**
     * 删除笔记
     * @param deleteNoteReqVO
     * @return
     */
    Response<?> deleteNote(DeleteNoteReqVO deleteNoteReqVO);

     /**
     * 笔记仅对自己可见
     * @param updateNoteVisibleOnlyMeReqVO
     * @return
     */
    Response<?> visibleOnlyMe(UpdateNoteVisibleOnlyMeReqVO updateNoteVisibleOnlyMeReqVO);

    /**
     * 笔记置顶 / 取消置顶
     * @param topNoteReqVO
     * @return
     */
    Response<?> topNote(TopNoteReqVO topNoteReqVO);

    /**
     * 点赞笔记
     * @param likeNoteReqVO
     * @return
     */
    Response<?> likeNote(LikeNoteReqVO likeNoteReqVO);

    /**
     * 取消点赞笔记
     * @param unlikeNoteReqVO
     * @return
     */
    Response<?> unlikeNote(UnlikeNoteReqVO unlikeNoteReqVO);

}
