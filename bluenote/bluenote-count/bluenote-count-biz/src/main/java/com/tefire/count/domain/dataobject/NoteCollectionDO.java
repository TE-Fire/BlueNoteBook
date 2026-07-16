package com.tefire.count.domain.dataobject;

import java.util.Date;

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

    private Date createTime;

    private Integer status;
}