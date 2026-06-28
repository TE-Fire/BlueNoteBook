package com.tefire.kv.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/*
 * @Author: TE-Fire 3037749727@qq.com
 * @Date: 2026-06-28 13:02:09
 * @Description: 新增笔记内容
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AddNoteContentReqDTO {
    @NotNull(message = "笔记 ID 不能为空")
    private Long noteId;

    @NotBlank(message = "笔记内容不能为空")
    private String content;
}
