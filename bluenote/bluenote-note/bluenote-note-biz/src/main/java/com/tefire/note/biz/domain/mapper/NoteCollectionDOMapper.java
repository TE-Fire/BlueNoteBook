package com.tefire.note.biz.domain.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.tefire.note.biz.domain.dataobject.NoteCollectionDO;

public interface NoteCollectionDOMapper {
    int deleteByPrimaryKey(Long id);

    int insert(NoteCollectionDO record);

    int insertSelective(NoteCollectionDO record);

    NoteCollectionDO selectByPrimaryKey(Long id);

    int updateByPrimaryKeySelective(NoteCollectionDO record);

    int updateByPrimaryKey(NoteCollectionDO record);

    /**
     * 查询笔记是否被收藏
     * @param userId
     * @param noteId
     * @return
     */
    int selectCountByUserIdAndNoteId(@Param("userId") Long userId, @Param("noteId") Long noteId);

     /**
     * 查询用户已收藏的笔记
     * @param userId
     * @return
     */
    List<NoteCollectionDO> selectByUserId(Long userId);

    /**
     * 查询笔记是否已经被收藏
     * @param userId
     * @param noteId
     * @return
     */
    int selectNoteIsCollected(@Param("userId") Long userId, @Param("noteId") Long noteId);

     /**
     * 查询用户最近收藏的笔记
     * @param userId
     * @param limit
     * @return
     */
    List<NoteCollectionDO> selectCollectedByUserIdAndLimit(@Param("userId") Long userId, @Param("limit")  int limit);

     /**
     * 新增笔记收藏记录，若已存在，则更新笔记收藏记录
     * @param noteCollectionDO
     * @return
     */
    int insertOrUpdate(NoteCollectionDO noteCollectionDO);

     /**
     * 取消收藏
     * @param noteCollectionDO
     * @return
     */
    int update2UnCollectByUserIdAndNoteId(NoteCollectionDO noteCollectionDO);
}