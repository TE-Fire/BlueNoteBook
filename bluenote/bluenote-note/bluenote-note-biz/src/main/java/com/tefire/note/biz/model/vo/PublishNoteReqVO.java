package com.tefire.note.biz.model.vo;

import java.util.List;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/*
 * @Author: TE-Fire 3037749727@qq.com
 * @Date: 2026-06-30 16:18:51
 * @Description: 发布笔记
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PublishNoteReqVO {
    // 笔记类型，0 代表图文笔记，1 代表视频笔记
    @NotNull(message = "笔记类型不能为空")
    private Integer type;
    // 图片链接数组，当为图文笔记时，此字段不能为空
    private List<String> imgUris;
    // 视频连接，当为视频笔记时，此字段不能为空
    private String videoUri;
    // 笔记标题
    private String title;
    // 笔记内容（可不填）
    private String content;
    // 话题 ID（可不填）
    private Long topicId;
}
