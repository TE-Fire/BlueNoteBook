/*
 * @Author: TE-Fire 3037749727@qq.com
 * @Date: 2026-06-28 12:48:50
 * @Description: 
 */
package com.tefire.biz.kv.domain.dataobject;

import java.util.UUID;

import org.springframework.data.cassandra.core.mapping.PrimaryKey;
import org.springframework.data.cassandra.core.mapping.Table;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


@Table("note_content")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NoteContentDO {
    @PrimaryKey("id")
    private UUID id;

    private String content;
}
