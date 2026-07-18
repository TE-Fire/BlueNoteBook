package com.tefire.note.biz.domain.dataobject;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class NoteCollectionDO {
    private Long id;

    private Long userId;

    private Long noteId;

    private LocalDateTime createTime;

    private Integer status;
}