package com.tefire.kv.dto.rsp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/*
 * @Author: TE-Fire 3037749727@qq.com
 * @Date: 2026-06-28 15:05:33
 * @Description: 笔记内容
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class FindNoteContentRspDTO {

    /**
     * 笔记 ID
     */
    private UUID noteId;

    /**
     * 笔记内容
     */
    private String content;

}
