package com.tefire.note.biz.model.vo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/*
 * @Author: TE-Fire 3037749727@qq.com
 * @Date: 2026-07-01 21:19:02
 * @Description: 查询笔记详情
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class FindNoteDetailReqVO {
    @NotNull(message = "笔记 ID 不能为空")
    private Long id;
}
