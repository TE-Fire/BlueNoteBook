package com.tefire.note.biz.model.vo;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/*
 * @Author: TE-Fire 3037749727@qq.com
 * @Date: 2026-07-04 21:23:51
 * @Description: 仅我可见
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class UpdateNoteVisibleOnlyMeReqVO {
    @NotNull(message = "笔记 ID 不能为空")
    private Long id;
}
