package com.tefire.note.biz.model.vo;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/*
 * @Author: TE-Fire 3037749727@qq.com
 * @Date: 2026-07-04 21:11:21
 * @Description: 笔记删除
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class DeleteNoteReqVO {
    @NotNull(message = "笔记 ID 不能为空")
    private Long id;
}
