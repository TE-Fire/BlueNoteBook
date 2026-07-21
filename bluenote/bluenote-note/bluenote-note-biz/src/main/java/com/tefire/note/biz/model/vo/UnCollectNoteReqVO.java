package com.tefire.note.biz.model.vo;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/*
 * @Author: TE-Fire 3037749727@qq.com
 * @Date: 2026-07-21 10:14:47
 * @Description: 取消收藏笔记
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class UnCollectNoteReqVO {
    @NotNull(message = "笔记 ID 不能为空")
    private Long noteId;
}
